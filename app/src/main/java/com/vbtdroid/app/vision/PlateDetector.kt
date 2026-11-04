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
import kotlin.math.roundToInt

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

/**
 * EfficientDet-Lite0 plate detector (`app/src/main/assets/models/plate.tflite`).
 *
 * The bundled model comes from simonkosina/vbt (MIT) and exposes four outputs:
 * scores `[1, N]`, boxes `[1, N, 4]` normalized as `ymin, xmin, ymax, xmax`,
 * a detection count `[1]` and class ids `[1, N]`. Tensor indices are resolved by
 * shape/name instead of relying on a fixed output order.
 */
class TflitePlateDetector(context: Context) : PlateDetector, AutoCloseable {
    private val interpreter: Interpreter
    private val gpuDelegate: GpuDelegate?
    private val inputWidth: Int
    private val inputHeight: Int
    private val inputType: DataType
    private val scoreOutput: OutputTensor
    private val boxesOutput: OutputTensor
    private val countOutput: OutputTensor
    private val classOutput: OutputTensor?

    private data class OutputTensor(val index: Int, val elementCount: Int, val name: String)

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
        val createdInterpreter = try {
            Interpreter(model, options)
        } catch (gpuError: Exception) {
            val failedDelegate = delegate ?: throw gpuError
            failedDelegate.close()
            delegate = null
            Interpreter(model, Interpreter.Options().setNumThreads(4))
        }

        try {
            val inputTensor = createdInterpreter.getInputTensor(0)
            val inputShape = inputTensor.shape()
            require(inputShape.size == 4 && inputShape[0] == 1 && inputShape[3] == 3) {
                "Unsupported plate model input tensor: ${inputShape.contentToString()}"
            }
            inputHeight = inputShape[1]
            inputWidth = inputShape[2]
            require(inputWidth > 0 && inputHeight > 0) {
                "Plate model input must have positive dimensions, got ${inputWidth}x$inputHeight"
            }
            inputType = inputTensor.dataType()
            require(inputType == DataType.FLOAT32 || inputType == DataType.UINT8) {
                "Unsupported plate model input type: $inputType"
            }

            val outputs = (0 until createdInterpreter.outputTensorCount).map { index ->
                val tensor = createdInterpreter.getOutputTensor(index)
                require(tensor.dataType() == DataType.FLOAT32) {
                    "Unsupported plate model output type at index $index: ${tensor.dataType()}; expected FLOAT32"
                }
                OutputTensor(
                    index = index,
                    elementCount = tensor.shape().fold(1) { total, dimension -> total * dimension },
                    name = tensor.name().orEmpty(),
                )
            }

            boxesOutput = outputs.singleOrNull { tensor ->
                createdInterpreter.getOutputTensor(tensor.index).shape().let { shape ->
                    shape.isNotEmpty() && shape.last() == EfficientDetPostprocess.BOX_VALUES
                }
            } ?: unsupportedOutputs(outputs, createdInterpreter)
            countOutput = outputs.singleOrNull { it.elementCount == 1 }
                ?: unsupportedOutputs(outputs, createdInterpreter)
            val scoreAndClass = outputs.filter { it.index != boxesOutput.index && it.index != countOutput.index }
            require(scoreAndClass.size == 2) { unsupportedOutputsMessage(outputs, createdInterpreter) }
            val score = scoreAndClass.firstOrNull { it.name.contains("score", ignoreCase = true) }
                ?: scoreAndClass.firstOrNull { it.name.contains("output_1", ignoreCase = true) }
                ?: scoreAndClass[0]
            scoreOutput = score
            classOutput = scoreAndClass.firstOrNull { it !== score }
            interpreter = createdInterpreter
            gpuDelegate = delegate
        } catch (error: Throwable) {
            createdInterpreter.close()
            delegate?.close()
            throw error
        }
    }

    /**
     * Runs the detector on the full frame plus top/bottom crops.
     *
     * Plates near the top of a tall portrait frame occupy few pixels after the square model
     * resize, which pushed their scores below the threshold (squat filming angle). Crops restore
     * the apparent plate size. Every detection is mapped back to original coordinates, merged and
     * de-duplicated with NMS. Only used for ROI init/recovery, so three inferences per call is
     * acceptable.
     */
    @Synchronized
    override fun detect(bitmap: Bitmap): List<PlateDetection> {
        if (bitmap.width <= 0 || bitmap.height <= 0) return emptyList()
        val cropHeight = (bitmap.height * CROP_HEIGHT_FRACTION).roundToInt()
            .coerceIn(1, bitmap.height)
        val bottomTop = bitmap.height - cropHeight
        val regions = buildList {
            add(EfficientDetPostprocess.CropRegion(0, 0, bitmap.width, bitmap.height))
            add(EfficientDetPostprocess.CropRegion(0, 0, bitmap.width, cropHeight))
            if (bottomTop > 0) {
                add(EfficientDetPostprocess.CropRegion(0, bottomTop, bitmap.width, cropHeight))
            }
        }
        val detections = ArrayList<PlateDetection>()
        for (region in regions) {
            val crop = if (region.left == 0 && region.top == 0 &&
                region.width == bitmap.width && region.height == bitmap.height
            ) {
                bitmap
            } else {
                Bitmap.createBitmap(bitmap, region.left, region.top, region.width, region.height)
            }
            try {
                detections += EfficientDetPostprocess.mapCropDetections(
                    detections = detectSingle(crop),
                    cropWidth = crop.width,
                    cropHeight = crop.height,
                    region = region,
                )
            } finally {
                if (crop !== bitmap) crop.recycle()
            }
        }
        return EfficientDetPostprocess.mergeDetections(detections)
    }

    private fun detectSingle(bitmap: Bitmap): List<PlateDetection> {
        val resized = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val bytesPerChannel = if (inputType == DataType.FLOAT32) 4 else 1
        val input = ByteBuffer.allocateDirect(inputWidth * inputHeight * 3 * bytesPerChannel)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputWidth * inputHeight)
        resized.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        if (resized !== bitmap) resized.recycle()
        pixels.forEach { pixel ->
            val red = pixel shr 16 and 0xff
            val green = pixel shr 8 and 0xff
            val blue = pixel and 0xff
            if (inputType == DataType.FLOAT32) {
                input.putFloat(red / 255f)
                input.putFloat(green / 255f)
                input.putFloat(blue / 255f)
            } else {
                // EfficientDet uint8 models consume raw 0..255 pixels through the signature input.
                input.put(red.toByte())
                input.put(green.toByte())
                input.put(blue.toByte())
            }
        }
        input.rewind()

        val scores = FloatArray(scoreOutput.elementCount)
        val boxes = FloatArray(boxesOutput.elementCount)
        val counts = FloatArray(countOutput.elementCount)
        val classes = classOutput?.let { FloatArray(it.elementCount) }
        val outputBuffers = buildMap {
            put(scoreOutput.index, scores.toDirectBuffer())
            put(boxesOutput.index, boxes.toDirectBuffer())
            put(countOutput.index, counts.toDirectBuffer())
            if (classOutput != null && classes != null) put(classOutput.index, classes.toDirectBuffer())
        }
        interpreter.runForMultipleInputsOutputs(arrayOf(input), outputBuffers)
        readFloats(outputBuffers.getValue(scoreOutput.index), scores)
        readFloats(outputBuffers.getValue(boxesOutput.index), boxes)
        readFloats(outputBuffers.getValue(countOutput.index), counts)
        if (classOutput != null && classes != null) {
            readFloats(outputBuffers.getValue(classOutput.index), classes)
        }

        val detectedCount = (counts.firstOrNull() ?: 0f).takeIf { it.isFinite() }?.toInt() ?: 0
        val classValues = classes
        val scoreValues: FloatArray
        val classIds: FloatArray?
        if (classValues != null &&
            EfficientDetPostprocess.looksLikeClassIds(scores, detectedCount) &&
            !EfficientDetPostprocess.looksLikeClassIds(classValues, detectedCount)
        ) {
            // Output names did not reveal which tensor carries class ids; swap them.
            scoreValues = classValues
            classIds = scores
        } else {
            scoreValues = scores
            classIds = classValues
        }
        val candidates = EfficientDetPostprocess.parse(
            scores = scoreValues,
            boxes = boxes,
            classIds = classIds,
            count = counts.firstOrNull() ?: 0f,
        )
        return EfficientDetPostprocess.toDetections(candidates, bitmap.width, bitmap.height)
    }

    override fun close() {
        interpreter.close()
        gpuDelegate?.close()
    }

    private companion object {
        /** Top/bottom crop height as a fraction of the full frame. */
        const val CROP_HEIGHT_FRACTION = 0.6f

        fun FloatArray.toDirectBuffer(): ByteBuffer =
            ByteBuffer.allocateDirect(size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())

        fun readFloats(buffer: ByteBuffer, destination: FloatArray) {
            buffer.rewind()
            buffer.asFloatBuffer().get(destination)
        }

        fun unsupportedOutputs(
            outputs: List<OutputTensor>,
            interpreter: Interpreter,
        ): Nothing = throw IllegalArgumentException(unsupportedOutputsMessage(outputs, interpreter))

        fun unsupportedOutputsMessage(outputs: List<OutputTensor>, interpreter: Interpreter): String {
            val shapes = outputs.joinToString { output ->
                interpreter.getOutputTensor(output.index).shape().contentToString()
            }
            return "Unsupported plate model outputs: $shapes; expected scores [1,N], boxes [1,N,4], count [1] and classes [1,N]"
        }
    }
}
