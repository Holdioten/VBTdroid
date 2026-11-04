package com.vbtdroid.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import android.net.Uri
import com.vbtdroid.app.analysis.AnalysisResult
import com.vbtdroid.app.video.VideoDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.FileDescriptor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Renders [AnalysisResult] overlays onto every decoded source frame and muxes a silent H.264 MP4.
 *
 * Decoding deliberately uses the same [VideoDecoder.decode] defaults as the analysis pipeline so
 * plate/pose pixel coordinates line up with the encoded frames.
 */
class VideoOverlayExporter {
    /** Writes the overlay MP4 to an app-private file (used for in-app preview). */
    suspend fun exportToFile(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        result: AnalysisResult,
        onProgress: (Float) -> Unit = {},
    ): Unit = render(context, sourceUri, result, onProgress) {
        outputFile.parentFile?.mkdirs()
        ParcelFileDescriptor.open(
            outputFile,
            ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE,
        )
    }

    private suspend fun render(
        context: Context,
        sourceUri: Uri,
        result: AnalysisResult,
        onProgress: (Float) -> Unit,
        openOutput: (Context) -> ParcelFileDescriptor,
    ): Unit = withContext(Dispatchers.IO) {
        require(result.observations.isNotEmpty()) { ERROR_NO_OBSERVATIONS }

        val appContext = context.applicationContext
        val decoder = VideoDecoder(appContext)
        val renderer = OverlayRenderer(appContext, result)
        val meta = runCatching { decoder.readMeta(sourceUri) }.getOrNull()
        val fps = result.fps.takeIf { it > 0.0 }
            ?: meta?.effectiveFps?.takeIf { it > 0.0 }
            ?: 30.0
        val estimatedFrames = ((meta?.durationSec ?: 0.0) * fps).roundToInt().coerceAtLeast(1)

        val output = openOutput(appContext)
        var session: EncoderSession? = null
        var target: Bitmap? = null
        var canvas: Canvas? = null
        var expectedWidth = 0
        var expectedHeight = 0
        var lastPtsUs = -1L
        var frameIndex = 0

        try {
            onProgress(0f)
            decoder.decode(sourceUri).collect { frame ->
                val decoded = frame.bitmap
                try {
                    if (session == null) {
                        expectedWidth = decoded.width
                        expectedHeight = decoded.height
                        require(expectedWidth > 1 && expectedHeight > 1) {
                            "Source video is too small to export"
                        }
                        val width = (expectedWidth / 2) * 2
                        val height = (expectedHeight / 2) * 2
                        target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        canvas = Canvas(target!!)
                        session = EncoderSession(output.fileDescriptor, width, height)
                    }
                    val activeTarget = target ?: error("Overlay target bitmap is missing")
                    check(decoded.width == expectedWidth && decoded.height == expectedHeight) {
                        "Source video frame size changed during export"
                    }
                    val activeCanvas = canvas ?: error("Overlay canvas is missing")
                    activeCanvas.drawBitmap(decoded, 0f, 0f, null)
                    renderer.draw(activeCanvas, activeTarget.width, activeTarget.height, frame.tSec)

                    val ptsUs = (frame.tSec * 1_000_000.0).roundToLong()
                        .let { if (it > lastPtsUs) it else lastPtsUs + 1L }
                    lastPtsUs = ptsUs
                    session?.encode(activeTarget, ptsUs)
                } finally {
                    if (!decoded.isRecycled) decoded.recycle()
                }
                frameIndex++
                onProgress((frameIndex.toFloat() / estimatedFrames).coerceIn(0f, 1f))
            }

            val active = session ?: error("No decodable video frames were found")
            active.finish()
            onProgress(1f)
        } finally {
            runCatching { session?.release() }
            runCatching { target?.recycle() }
            runCatching { output.close() }
        }
    }

    private class EncoderSession(
        fileDescriptor: FileDescriptor,
        private val width: Int,
        private val height: Int,
    ) {
        private val codecInfo: MediaCodecInfo
        private val colorFormat: Int
        private val semiPlanar: Boolean
        private val codec: MediaCodec
        private val muxer: MediaMuxer
        private val bufferInfo = MediaCodec.BufferInfo()
        private val pixels = IntArray(width * height)
        private val yuv = ByteArray(width * height * 3 / 2)
        private var trackIndex = -1
        private var muxerStarted = false
        private var wroteSample = false
        private var sawEndOfStream = false
        private var lastPtsUs = 0L

        init {
            val selection = selectEncoder()
            codecInfo = selection.first
            colorFormat = selection.second
            semiPlanar = colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar

            var createdCodec: MediaCodec? = null
            var createdMuxer: MediaMuxer? = null
            try {
                val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                    setInteger(
                        MediaFormat.KEY_BIT_RATE,
                        (BASE_BITRATE * (width.toLong() * height) / (1920L * 1080L))
                            .toInt()
                            .coerceAtLeast(1_000_000),
                    )
                    setInteger(MediaFormat.KEY_FRAME_RATE, OUTPUT_FPS)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SEC)
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                }
                createdCodec = MediaCodec.createByCodecName(codecInfo.name).apply {
                    configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                }
                createdMuxer = MediaMuxer(
                    fileDescriptor,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                )
                createdCodec.start()
            } catch (error: Throwable) {
                runCatching { createdCodec?.stop() }
                runCatching { createdCodec?.release() }
                runCatching { createdMuxer?.release() }
                throw error
            }
            codec = createdCodec ?: error("Could not create the H.264 encoder")
            muxer = createdMuxer ?: error("Could not create the MP4 muxer")
        }

        fun encode(bitmap: Bitmap, ptsUs: Long) {
            convert(bitmap)
            feed(yuv, yuv.size, ptsUs, 0)
            lastPtsUs = ptsUs
            drain(endOfStream = false)
        }

        fun finish() {
            feed(EMPTY, 0, lastPtsUs + 1L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(endOfStream = true)
            check(sawEndOfStream) { "Encoder did not finish the output stream" }
            check(muxerStarted && wroteSample) { "Encoder produced no video output" }
        }

        fun release() {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }

        private fun feed(data: ByteArray, length: Int, ptsUs: Long, flags: Int) {
            while (true) {
                val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val buffer = codec.getInputBuffer(inputIndex)
                        ?: error("Encoder returned no input buffer")
                    buffer.clear()
                    if (length > 0) buffer.put(data, 0, length)
                    codec.queueInputBuffer(inputIndex, 0, length, ptsUs, flags)
                    return
                }
                drain(endOfStream = false)
            }
        }

        private fun drain(endOfStream: Boolean) {
            var idlePolls = 0
            while (true) {
                when (val outputIndex = codec.dequeueOutputBuffer(
                    bufferInfo,
                    if (endOfStream) TIMEOUT_US else 0L,
                )) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!endOfStream) return
                        if (++idlePolls > MAX_EOS_POLLS) return
                    }

                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxerStarted) { "Encoder changed format more than once" }
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }

                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit

                    else -> if (outputIndex >= 0) {
                        val isEndOfStream =
                            bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (bufferInfo.size > 0) {
                            check(muxerStarted) { "Encoder emitted data before the output format" }
                            val buffer = codec.getOutputBuffer(outputIndex)
                                ?: error("Encoder returned no output buffer")
                            buffer.position(bufferInfo.offset)
                            buffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, buffer, bufferInfo)
                            wroteSample = true
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (isEndOfStream) {
                            sawEndOfStream = true
                            return
                        }
                    }
                }
            }
        }

        private fun convert(bitmap: Bitmap) {
            check(bitmap.width == width && bitmap.height == height) {
                "Overlay bitmap size does not match the encoder"
            }
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val frameSize = width * height
            val chromaSize = frameSize / 4
            var uIndex = frameSize
            var vIndex = frameSize + chromaSize
            for (y in 0 until height) {
                val rowOffset = y * width
                val chromaRow = (y / 2) * (width / 2) * 2
                for (x in 0 until width) {
                    val color = pixels[rowOffset + x]
                    val r = color shr 16 and 0xff
                    val g = color shr 8 and 0xff
                    val b = color and 0xff
                    yuv[rowOffset + x] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).toByte()
                    if (y % 2 == 0 && x % 2 == 0) {
                        val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).toByte()
                        val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).toByte()
                        if (semiPlanar) {
                            val index = frameSize + chromaRow + x
                            yuv[index] = u
                            yuv[index + 1] = v
                        } else {
                            yuv[uIndex++] = u
                            yuv[vIndex++] = v
                        }
                    }
                }
            }
        }

        private fun selectEncoder(): Pair<MediaCodecInfo, Int> {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val infos = codecList.codecInfos
            val candidates = infos.map { info ->
                val isAvcEncoder = info.isEncoder && MIME_TYPE in info.supportedTypes
                EncoderCandidate(
                    name = info.name,
                    isEncoder = info.isEncoder,
                    supportsAvc = isAvcEncoder,
                    colorFormats = if (isAvcEncoder) {
                        runCatching { info.getCapabilitiesForType(MIME_TYPE).colorFormats.toList() }
                            .getOrDefault(emptyList())
                    } else {
                        emptyList()
                    },
                    isHardwareAccelerated = hardwareFlag { info.isHardwareAccelerated },
                    isSoftwareOnly = hardwareFlag { info.isSoftwareOnly },
                )
            }
            val selection = selectPreferredEncoder(candidates)
                ?: error("No H.264 encoder with YUV420 support was found")
            val info = infos.first { it.name == selection.name }
            Log.i(
                TAG,
                "Overlay encoder: ${selection.name} " +
                    "(hardware=${hardwareFlag { info.isHardwareAccelerated }}, " +
                    "softwareOnly=${hardwareFlag { info.isSoftwareOnly }})",
            )
            return info to selection.colorFormat
        }

        /** Platform flags only exist on API 29+; older devices fall back to the name heuristic. */
        private inline fun hardwareFlag(read: () -> Boolean): Boolean? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) runCatching(read).getOrNull() else null
    }

    companion object {
        const val ERROR_NO_OBSERVATIONS = "No plate observations available"

        private const val TAG = "VideoOverlayExporter"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val OUTPUT_FPS = 30
        private const val I_FRAME_INTERVAL_SEC = 1
        private const val BASE_BITRATE = 8_000_000L
        private const val TIMEOUT_US = 10_000L
        private const val MAX_EOS_POLLS = 100
        private val EMPTY = ByteArray(0)
    }
}

/** One H.264 encoder as seen by [VideoOverlayExporter], reduced to what selection needs. */
internal data class EncoderCandidate(
    val name: String,
    val isEncoder: Boolean = true,
    val supportsAvc: Boolean = true,
    /** YUV420 formats reported by the codec, best first is not implied. */
    val colorFormats: List<Int> = emptyList(),
    /** API 29+ flag; null when unknown or unavailable. */
    val isHardwareAccelerated: Boolean? = null,
    /** API 29+ flag; null when unknown or unavailable. */
    val isSoftwareOnly: Boolean? = null,
)

internal data class EncoderSelection(val name: String, val colorFormat: Int)

/**
 * Picks the fastest usable H.264 encoder.
 *
 * Hardware acceleration wins over color-format convenience: a software encoder that happens to
 * accept semi-planar input must not shadow a vendor encoder. When the platform flags are missing
 * (API < 29) a conservative codec-name heuristic is used, and a software encoder is still selected
 * if nothing else exists.
 */
internal fun selectPreferredEncoder(candidates: List<EncoderCandidate>): EncoderSelection? {
    fun formatRank(formats: List<Int>): Int = when {
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar in formats -> 0
        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar in formats -> 1
        else -> Int.MAX_VALUE
    }

    fun hardwareRank(candidate: EncoderCandidate): Int {
        if (candidate.isHardwareAccelerated == true && candidate.isSoftwareOnly != true) return 0
        if (candidate.isHardwareAccelerated == false || candidate.isSoftwareOnly == true) return 2
        // Unknown flags: trust vendor naming, but keep true software codecs last.
        return if (looksSoftwareEncoder(candidate.name)) 2 else 1
    }

    return candidates
        .filter { it.isEncoder && it.supportsAvc && formatRank(it.colorFormats) != Int.MAX_VALUE }
        .withIndex()
        .minWithOrNull(
            compareBy(
                { hardwareRank(it.value) },
                { formatRank(it.value.colorFormats) },
                { it.index },
            ),
        )
        ?.value
        ?.let { candidate ->
            val colorFormat =
                if (MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar in candidate.colorFormats) {
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                } else {
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                }
            EncoderSelection(candidate.name, colorFormat)
        }
}

/** Names used by Google/ffmpeg/software-only AVC encoders; excludes vendor hardware names. */
private fun looksSoftwareEncoder(name: String): Boolean {
    val lower = name.lowercase()
    return lower.startsWith("omx.google") ||
        lower.startsWith("c2.android") ||
        lower.startsWith("omx.ffmpeg") ||
        lower.contains(".sw.") ||
        lower.contains("software")
}

