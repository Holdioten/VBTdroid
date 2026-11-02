package com.vbtdroid.app.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoDecoder(context: Context) {
    private val appContext = context.applicationContext

    fun decode(uri: Uri, maxLongSide: Int = 1920): Flow<Frame> = callbackFlow {
        require(maxLongSide > 0) { "maxLongSide must be positive" }
        val job = launch(Dispatchers.IO) {
            var extractor: MediaExtractor? = null
            var codec: MediaCodec? = null
            try {
                extractor = MediaExtractor().apply { setDataSource(appContext, uri, null) }
                val trackIndex = findVideoTrack(extractor)
                extractor.selectTrack(trackIndex)
                val format = extractor.getTrackFormat(trackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME)
                    ?: error("Video track has no MIME type")
                val rotation = readRotation(uri)
                codec = MediaCodec.createDecoderByType(mime).apply {
                    configure(format, null, null, 0)
                    start()
                }

                val info = MediaCodec.BufferInfo()
                var inputEos = false
                var outputEos = false
                val emittedPts = HashSet<Long>()
                while (!outputEos) {
                    if (!inputEos) {
                        val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)
                                ?: error("Decoder returned no input buffer")
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputEos = true
                            } else {
                                codec.queueInputBuffer(
                                    inputIndex, 0, sampleSize, extractor.sampleTime,
                                    extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC
                                )
                                extractor.advance()
                            }
                        }
                    }

                    when (val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                        MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                        MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                        else -> if (outputIndex >= 0) {
                            val isEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            val ptsUs = info.presentationTimeUs
                            var frame: Frame? = null
                            if (info.size > 0 && ptsUs !in emittedPts) {
                                val image = codec.getOutputImage(outputIndex)
                                val bitmap = image?.use { imageToBitmap(it, maxLongSide, rotation) }
                                if (bitmap != null) {
                                    emittedPts.add(ptsUs)
                                    frame = Frame(ptsUs / 1_000_000.0, bitmap)
                                }
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                            frame?.let { send(it) }
                            if (isEos) outputEos = true
                        }
                    }
                }
                close()
            } catch (error: Throwable) {
                close(error)
            } finally {
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
                runCatching { extractor?.release() }
            }
        }
        awaitClose { job.cancel() }
    }

    suspend fun readMeta(uri: Uri): VideoMeta = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, uri)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()?.let { ((it % 360) + 360) % 360 } ?: 0
            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val durationSec = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toDoubleOrNull()?.div(1000.0) ?: 0.0
            val nominalFps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toDoubleOrNull()?.takeIf { it > 0.0 }
                ?: readTrackFrameRate(uri)
            val (firstPtsUs, lastPtsUs, count) = scanPresentationTimes(uri)
            val effectiveFps = if (count > 1 && lastPtsUs > firstPtsUs) {
                (count - 1).toDouble() * 1_000_000.0 / (lastPtsUs - firstPtsUs)
            } else {
                0.0
            }
            val swapsDimensions = rotation == 90 || rotation == 270
            VideoMeta(
                durationSec = durationSec,
                width = if (swapsDimensions) rawHeight else rawWidth,
                height = if (swapsDimensions) rawWidth else rawHeight,
                rotationDeg = rotation,
                nominalFps = nominalFps,
                effectiveFps = effectiveFps
            )
        } finally {
            retriever.release()
        }
    }

    private fun readTrackFrameRate(uri: Uri): Double {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(appContext, uri, null)
            val format = extractor.getTrackFormat(findVideoTrack(extractor))
            if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble()
            } else {
                0.0
            }
        } finally {
            extractor.release()
        }
    }

    private fun scanPresentationTimes(uri: Uri): Triple<Long, Long, Long> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(appContext, uri, null)
            extractor.selectTrack(findVideoTrack(extractor))
            val presentationTimes = HashSet<Long>()
            while (extractor.sampleTrackIndex >= 0) {
                val pts = extractor.sampleTime
                if (pts >= 0) presentationTimes.add(pts)
                extractor.advance()
            }
            if (presentationTimes.isEmpty()) return Triple(0L, 0L, 0L)
            return Triple(presentationTimes.min(), presentationTimes.max(), presentationTimes.size.toLong())
        } finally {
            extractor.release()
        }
    }

    private fun readRotation(uri: Uri): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()?.let { ((it % 360) + 360) % 360 } ?: 0
        } finally {
            retriever.release()
        }
    }

    private fun findVideoTrack(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) return index
        }
        error("No video track found")
    }

    private fun imageToBitmap(image: android.media.Image, maxLongSide: Int, rotation: Int): Bitmap {
        val crop = image.cropRect
        val width = crop.width()
        val height = crop.height()
        val pixels = IntArray(width * height)
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        for (y in 0 until height) {
            val sourceY = y + crop.top
            for (x in 0 until width) {
                val sourceX = x + crop.left
                val yValue = yBuffer.get(sourceY * yRowStride + sourceX * yPixelStride).toInt() and 0xff
                val uvX = sourceX / 2
                val uvY = sourceY / 2
                val u = (uBuffer.get(uvY * uvRowStride + uvX * uvPixelStride).toInt() and 0xff) - 128
                val v = (vBuffer.get(uvY * vPlane.rowStride + uvX * vPlane.pixelStride).toInt() and 0xff) - 128
                val yAdjusted = (yValue - 16).coerceAtLeast(0)
                val red = (1.164f * yAdjusted + 1.596f * v).toInt().coerceIn(0, 255)
                val green = (1.164f * yAdjusted - 0.813f * v - 0.391f * u).toInt().coerceIn(0, 255)
                val blue = (1.164f * yAdjusted + 2.018f * u).toInt().coerceIn(0, 255)
                pixels[y * width + x] = (0xff shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        val raw = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val matrix = Matrix().apply { if (rotation != 0) postRotate(rotation.toFloat()) }
        val rotated = if (rotation != 0) Bitmap.createBitmap(raw, 0, 0, width, height, matrix, true) else raw
        if (rotated !== raw) raw.recycle()
        val longestSide = maxOf(rotated.width, rotated.height)
        if (longestSide <= maxLongSide) return rotated
        val scale = maxLongSide.toFloat() / longestSide
        val scaled = Bitmap.createScaledBitmap(
            rotated,
            (rotated.width * scale).toInt().coerceAtLeast(1),
            (rotated.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== rotated) rotated.recycle()
        return scaled
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
    }
}
