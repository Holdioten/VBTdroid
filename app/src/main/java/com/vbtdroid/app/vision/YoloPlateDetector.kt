package com.vbtdroid.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Self-trained YOLO11n plate detector (`assets/models/plate_yolo.tflite`).
 *
 * Export contract (LiteRT, float32):
 * - input  `[1, 3, H, W]` float32 RGB, 0..1, letterboxed with 114 padding
 * - output `[1, 4 + classes, anchors]` float32: normalized `cx, cy, w, h` relative to the
 *   letterboxed input, followed by one sigmoid score per class (`plate`, `bar_end`).
 *
 * The box is only a proposal. Geometry (circle centre/radius) is refined by the tracker;
 * a partially occluded plate must not be treated as if its bounding-box centre were its centre.
 */
class YoloPlateDetector(context: Context) : PlateDetector, AutoCloseable {
    private val interpreter: Interpreter
    private val gpuDelegate: GpuDelegate?
    private val inputWidth: Int
    private val inputHeight: Int
    private val anchorCount: Int
    private val classCount: Int
    private val inputBuffer: ByteBuffer
    private val outputBuffer: Array<Array<FloatArray>>

    init {
        val modelFile = "models/plate_yolo.tflite"
        val descriptor = try {
            context.assets.openFd(modelFile)
        } catch (error: Exception) {
            throw IllegalStateException(
                "YOLO plate model missing: add app/src/main/assets/$modelFile.",
                error,
            )
        }
        val model = FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
            channel.map(
                FileChannel.MapMode.READ_ONLY,
                descriptor.startOffset,
                descriptor.declaredLength,
            )
        }.also { descriptor.close() }

        val options = Interpreter.Options().setNumThreads(4)
        var delegate: GpuDelegate? = null
        val compatibility = CompatibilityList()
        try {
            if (compatibility.isDelegateSupportedOnThisDevice) {
                delegate = GpuDelegate()
                options.addDelegate(delegate)
            }
        } catch (_: Exception) {
            delegate?.close()
            delegate = null
        } finally {
            compatibility.close()
        }
        val created = try {
            Interpreter(model, options)
        } catch (gpuError: Exception) {
            val failed = delegate ?: throw gpuError
            failed.close()
            delegate = null
            Interpreter(model, Interpreter.Options().setNumThreads(4))
        }

        try {
            val input = created.getInputTensor(0)
            val shape = input.shape()
            require(input.dataType() == DataType.FLOAT32) {
                "Unsupported YOLO input type ${input.dataType()}; expected FLOAT32"
            }
            require(shape.size == 4 && shape[0] == 1 && shape[1] == 3) {
                "Unsupported YOLO input shape ${shape.contentToString()}; expected [1,3,H,W]"
            }
            inputHeight = shape[2]
            inputWidth = shape[3]
            require(inputWidth > 0 && inputHeight > 0)

            val output = created.getOutputTensor(0)
            require(output.dataType() == DataType.FLOAT32)
            val outShape = output.shape()
            require(outShape.size == 3 && outShape[0] == 1 && outShape[1] >= 5) {
                "Unsupported YOLO output shape ${outShape.contentToString()}"
            }
            classCount = outShape[1] - 4
            anchorCount = outShape[2]
            inputBuffer = ByteBuffer.allocateDirect(1 * 3 * inputWidth * inputHeight * 4)
                .order(ByteOrder.nativeOrder())
            outputBuffer = Array(1) { Array(outShape[1]) { FloatArray(outShape[2]) } }
            interpreter = created
            gpuDelegate = delegate
            android.util.Log.d(
                "VBTDebug",
                "YOLO ready input=${inputWidth}x${inputHeight} anchors=$anchorCount classes=$classCount gpu=${delegate != null}",
            )
        } catch (error: Throwable) {
            created.close()
            delegate?.close()
            throw error
        }
    }

    override fun detect(bitmap: Bitmap): List<PlateDetection> {
        if (bitmap.width <= 0 || bitmap.height <= 0) return emptyList()
        val scale = min(inputWidth.toFloat() / bitmap.width, inputHeight.toFloat() / bitmap.height)
        val drawWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val drawHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val padX = (inputWidth - drawWidth) / 2f
        val padY = (inputHeight - drawHeight) / 2f

        val canvasBitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(canvasBitmap)
            canvas.drawColor(Color.rgb(114, 114, 114))
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(padX, padY, padX + drawWidth, padY + drawHeight),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            val pixels = IntArray(inputWidth * inputHeight)
            canvasBitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
            inputBuffer.rewind()
            val plane = inputWidth * inputHeight
            // CHW float layout, RGB, 0..1.
            for (index in 0 until plane) {
                val color = pixels[index]
                inputBuffer.putFloat(((color shr 16) and 0xff) / 255f)
            }
            for (index in 0 until plane) {
                val color = pixels[index]
                inputBuffer.putFloat(((color shr 8) and 0xff) / 255f)
            }
            for (index in 0 until plane) {
                val color = pixels[index]
                inputBuffer.putFloat((color and 0xff) / 255f)
            }
            inputBuffer.rewind()
        } finally {
            if (!canvasBitmap.isRecycled) canvasBitmap.recycle()
        }

        try {
            interpreter.run(inputBuffer, outputBuffer)
        } catch (error: Exception) {
            android.util.Log.e("VBTDebug", "YOLO inference failed", error)
            throw error
        }

        val detections = ArrayList<PlateDetection>()
        for (anchor in 0 until anchorCount) {
            val score = outputBuffer[0][4][anchor]
            if (!score.isFinite() || score < MIN_CONFIDENCE) continue
            var bestClass = 0
            var bestScore = score
            for (cls in 1 until classCount) {
                val candidate = outputBuffer[0][4 + cls][anchor]
                if (candidate > bestScore) {
                    bestScore = candidate
                    bestClass = cls
                }
            }
            if (bestClass != PLATE_CLASS) continue
            val cx = outputBuffer[0][0][anchor] * inputWidth
            val cy = outputBuffer[0][1][anchor] * inputHeight
            val width = outputBuffer[0][2][anchor] * inputWidth
            val height = outputBuffer[0][3][anchor] * inputHeight
            if (width <= 1f || height <= 1f) continue
            val left = ((cx - width / 2f) - padX) / scale
            val top = ((cy - height / 2f) - padY) / scale
            val right = ((cx + width / 2f) - padX) / scale
            val bottom = ((cy + height / 2f) - padY) / scale
            val clampLeft = left.coerceIn(0f, bitmap.width.toFloat())
            val clampTop = top.coerceIn(0f, bitmap.height.toFloat())
            val clampRight = right.coerceIn(0f, bitmap.width.toFloat())
            val clampBottom = bottom.coerceIn(0f, bitmap.height.toFloat())
            if (clampRight - clampLeft < 2f || clampBottom - clampTop < 2f) continue
            detections += PlateDetection(
                cxPx = (clampLeft + clampRight) / 2f,
                cyPx = (clampTop + clampBottom) / 2f,
                widthPx = clampRight - clampLeft,
                heightPx = clampBottom - clampTop,
                confidence = bestScore,
            )
        }
        val kept = nonMaximumSuppression(detections, NMS_IOU)
        if (kept.isEmpty()) {
            android.util.Log.w("VBTDebug", "YOLO no plate: raw=$detections")
        } else {
            android.util.Log.d(
                "VBTDebug",
                "YOLO plates=${kept.size} best=${kept.first().confidence} box=${kept.first().cxPx},${kept.first().cyPx},${kept.first().widthPx}x${kept.first().heightPx}",
            )
        }
        return kept
    }

    private fun nonMaximumSuppression(
        detections: List<PlateDetection>,
        iouThreshold: Float,
    ): List<PlateDetection> {
        val remaining = detections.sortedByDescending { it.confidence }.toMutableList()
        val kept = ArrayList<PlateDetection>()
        while (remaining.isNotEmpty()) {
            val best = remaining.removeAt(0)
            kept += best
            remaining.removeAll { candidate ->
                val left = max(best.cxPx - best.widthPx / 2f, candidate.cxPx - candidate.widthPx / 2f)
                val top = max(best.cyPx - best.heightPx / 2f, candidate.cyPx - candidate.heightPx / 2f)
                val right = min(best.cxPx + best.widthPx / 2f, candidate.cxPx + candidate.widthPx / 2f)
                val bottom = min(best.cyPx + best.heightPx / 2f, candidate.cyPx + candidate.heightPx / 2f)
                val intersection = max(0f, right - left) * max(0f, bottom - top)
                val union = best.widthPx * best.heightPx + candidate.widthPx * candidate.heightPx - intersection
                union > 0f && intersection / union > iouThreshold
            }
        }
        return kept
    }

    override fun close() {
        interpreter.close()
        gpuDelegate?.close()
    }

    private companion object {
        const val PLATE_CLASS = 0
        const val MIN_CONFIDENCE = 0.35f
        const val NMS_IOU = 0.45f
    }
}
