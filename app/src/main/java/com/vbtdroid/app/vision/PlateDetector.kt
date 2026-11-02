package com.vbtdroid.app.vision

import android.content.Context
import android.graphics.Bitmap
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

data class PlateDetection(
    val cxPx: Float,
    val cyPx: Float,
    val widthPx: Float,
    val heightPx: Float,
    val confidence: Float,
)

interface PlateDetector {
    fun detect(bitmap: Bitmap): List<PlateDetection>
}

/** 适配输出形状为 [1, 5, N] 的 YOLOv8 TFLite 模型。 */
class TflitePlateDetector(context: Context) : PlateDetector, AutoCloseable {
    private val interpreter: Interpreter
    private var gpuDelegate: GpuDelegate?
    private val inputWidth: Int
    private val inputHeight: Int
    private val outputCount: Int
    private val inputType: DataType

    init {
        val modelFile = "models/plate.tflite"
        val modelDescriptor = try {
            context.assets.openFd(modelFile)
        } catch (error: Exception) {
            throw IllegalStateException(
                "Plate model missing: add app/src/main/assets/$modelFile. See assets/models/README.md.",
                error,
            )
        }
        val model = FileInputStream(modelDescriptor.fileDescriptor).channel.use { channel ->
            channel.map(
                FileChannel.MapMode.READ_ONLY,
                modelDescriptor.startOffset,
                modelDescriptor.declaredLength,
            )
        }.also { modelDescriptor.close() }

        val options = Interpreter.Options().setNumThreads(4)
        var delegate: GpuDelegate? = null
        try {
            if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                delegate = GpuDelegate()
                options.addDelegate(delegate)
            }
        } catch (_: Exception) {
            delegate?.close()
            delegate = null
        }
        gpuDelegate = delegate
        interpreter = try {
            Interpreter(model, options)
        } catch (gpuError: Exception) {
            val failedDelegate = gpuDelegate ?: throw gpuError
            failedDelegate.close()
            gpuDelegate = null
            Interpreter(model, Interpreter.Options().setNumThreads(4))
        }

        val inputShape = interpreter.getInputTensor(0).shape()
        require(inputShape.size == 4 && inputShape[0] == 1 && inputShape[3] == 3) {
            "Unsupported plate model input tensor: ${inputShape.contentToString()}"
        }
        inputHeight = inputShape[1]
        inputWidth = inputShape[2]
        require((inputWidth == 384 && inputHeight == 224) || (inputWidth == 640 && inputHeight == 640)) {
            "Plate model input must be 384x224 or 640x640, got ${inputWidth}x$inputHeight"
        }
        inputType = interpreter.getInputTensor(0).dataType()
        require(inputType == DataType.FLOAT32 || inputType == DataType.UINT8) {
            "Unsupported plate model input type: $inputType"
        }

        val outputShape = interpreter.getOutputTensor(0).shape()
        require(outputShape.size == 3 && outputShape[0] == 1 && outputShape[1] == 5) {
            "Unsupported plate model output tensor: ${outputShape.contentToString()}; expected [1,5,N]"
        }
        outputCount = outputShape[2]
    }

    @Synchronized
    override fun detect(bitmap: Bitmap): List<PlateDetection> {
        val resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val bytesPerChannel = if (inputType == DataType.FLOAT32) 4 else 1
        val input = ByteBuffer.allocateDirect(inputWidth * inputHeight * 3 * bytesPerChannel)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputWidth * inputHeight)
        resized.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        pixels.forEach { pixel ->
            val channels = intArrayOf(pixel shr 16 and 0xff, pixel shr 8 and 0xff, pixel and 0xff)
            channels.forEach { channel ->
                if (inputType == DataType.FLOAT32) input.putFloat(channel / 255f)
                else input.put(channel.toByte())
            }
        }
        input.rewind()
        val output = ByteBuffer.allocateDirect(5 * outputCount * 4).order(ByteOrder.nativeOrder())
        interpreter.run(input, output)
        output.rewind()
        val planar = FloatArray(5 * outputCount)
        output.asFloatBuffer().get(planar)

        val scaleX = bitmap.width.toFloat() / inputWidth
        val scaleY = bitmap.height.toFloat() / inputHeight
        val candidates = (0 until outputCount).mapNotNull { index ->
            val score = planar[4 * outputCount + index]
            if (!score.isFinite() || score < CONFIDENCE_THRESHOLD) return@mapNotNull null
            PlateDetection(
                cxPx = planar[index] * scaleX,
                cyPx = planar[outputCount + index] * scaleY,
                widthPx = planar[2 * outputCount + index] * scaleX,
                heightPx = planar[3 * outputCount + index] * scaleY,
                confidence = score,
            ).takeIf { it.widthPx > 0 && it.heightPx > 0 }
        }
        return nonMaximumSuppression(candidates)
    }

    override fun close() {
        interpreter.close()
        gpuDelegate?.close()
    }

    private fun nonMaximumSuppression(detections: List<PlateDetection>): List<PlateDetection> {
        val remaining = detections.sortedByDescending { it.confidence }.toMutableList()
        val kept = mutableListOf<PlateDetection>()
        while (remaining.isNotEmpty()) {
            val best = remaining.removeAt(0)
            kept += best
            remaining.removeAll { intersectionOverUnion(best, it) > NMS_IOU_THRESHOLD }
        }
        return kept
    }

    private fun intersectionOverUnion(a: PlateDetection, b: PlateDetection): Float {
        val left = max(a.cxPx - a.widthPx / 2f, b.cxPx - b.widthPx / 2f)
        val top = max(a.cyPx - a.heightPx / 2f, b.cyPx - b.heightPx / 2f)
        val right = min(a.cxPx + a.widthPx / 2f, b.cxPx + b.widthPx / 2f)
        val bottom = min(a.cyPx + a.heightPx / 2f, b.cyPx + b.heightPx / 2f)
        val intersection = max(0f, right - left) * max(0f, bottom - top)
        val union = a.widthPx * a.heightPx + b.widthPx * b.heightPx - intersection
        return if (union > 0f) intersection / union else 0f
    }

    private companion object {
        const val CONFIDENCE_THRESHOLD = 0.35f
        const val NMS_IOU_THRESHOLD = 0.45f
    }
}
