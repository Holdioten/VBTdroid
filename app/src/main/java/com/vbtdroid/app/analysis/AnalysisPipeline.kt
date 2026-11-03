package com.vbtdroid.app.analysis

import android.graphics.RectF
import android.net.Uri
import com.vbtdroid.app.video.VideoDecoder
import com.vbtdroid.app.vision.PlateTracker
import com.vbtdroid.app.vision.PoseEstimator
import com.vbtdroid.core.calibration.Calibration
import com.vbtdroid.core.metrics.Kinematics
import com.vbtdroid.core.metrics.MotionSample
import com.vbtdroid.core.metrics.RepMetrics
import com.vbtdroid.core.metrics.RepMetricsCalculator
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.model.PlateObservation
import com.vbtdroid.core.phases.PhaseSegmenter
import com.vbtdroid.core.phases.RepSlice
import com.vbtdroid.core.quality.Quality
import com.vbtdroid.core.quality.QualityFlags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class AnalysisRequest(
    val uri: Uri,
    val lift: Lift,
    val cameraAngleDeg: Double,
    val plateDiameterM: Double,
    val loadKg: Double?,
    val roi: RectF? = null,
    val cameraLevel: Boolean = true,
)

data class SetSummary(
    val repCount: Int,
    val mcvAvgMps: Double,
    val bestMcvMps: Double,
    val velocityLossPct: Double?,
    val totalVolumeKg: Double?,
    val totalWorkJ: Double?,
)

data class AnalysisResult(
    val reps: List<RepMetrics>,
    val summary: SetSummary,
    val quality: QualityFlags,
    val fps: Double,
    val motionSamples: List<MotionSample> = emptyList(),
    val repSlices: List<RepSlice> = emptyList(),
    val frameAnglesByRep: Map<Int, List<FrameAngles>> = emptyMap(),
    val lift: Lift = Lift.SQUAT,
    val loadKg: Double? = null,
)

/**
 * Runs decoding, plate tracking, calibration, segmentation and metric calculation.
 *
 * Ownership: every bitmap emitted by [decoder] is transferred to this pipeline and is recycled
 * immediately after that frame has been processed, including failure paths. Bitmaps supplied by
 * callers to tracker or pose fakes outside this flow remain caller-owned and are never recycled.
 */
class AnalysisPipeline(
    private val decoder: VideoDecoder,
    private val tracker: PlateTracker,
    private val poseEstimator: PoseEstimator,
) {
    suspend fun analyze(
        request: AnalysisRequest,
        onProgress: (Float) -> Unit = {},
    ): AnalysisResult = withContext(Dispatchers.Default) {
        require(request.plateDiameterM.isFinite() && request.plateDiameterM > 0.0) {
            "Plate diameter must be positive and finite"
        }
        require(request.cameraAngleDeg.isFinite() && request.cameraAngleDeg > 0.0 &&
            request.cameraAngleDeg <= 90.0) {
            "Camera angle must be in (0, 90]"
        }
        require(request.loadKg == null || request.loadKg.isFinite()) { "Load must be finite" }

        val meta = decoder.readMeta(request.uri)
        val metadataFps = meta.effectiveFps.takeIf { it > 0.0 }
            ?: meta.nominalFps.takeIf { it > 0.0 }
            ?: 0.0
        val estimatedFrames = (meta.durationSec * metadataFps).roundToInt()
        val observations = mutableListOf<PlateObservation>()
        val poseAngles = mutableListOf<FrameAngles>()
        var decodedFrames = 0
        var missedFrames = 0
        var firstFrameSec: Double? = null
        var lastFrameSec: Double? = null
        var poseFailed = false
        var poseProducedAngles = false

        onProgress(0f)
        decoder.decode(request.uri).collect { frame ->
            try {
                decodedFrames++
                if (firstFrameSec == null) firstFrameSec = frame.tSec
                lastFrameSec = frame.tSec

                val observation = if (observations.isEmpty()) {
                    val roi = request.roi ?: tracker.initialRoi(frame.bitmap)
                        ?: throw IllegalStateException(
                            "No plate ROI available: select an ROI or configure a plate detector",
                        )
                    tracker.initialize(frame.bitmap, roi, frame.tSec)
                } else {
                    tracker.update(frame.bitmap, frame.tSec).also {
                        if (it == null) missedFrames++
                    }
                }

                if (observation != null &&
                    (observations.isEmpty() || observation.tSec > observations.last().tSec)
                ) {
                    observations += observation
                }

                // Pose is deliberately best-effort and never gates plate-derived bar metrics.
                if ((decodedFrames - 1) % POSE_FRAME_STRIDE == 0) {
                    try {
                        val pose = poseEstimator.estimate(frame.bitmap, frame.tSec)
                        if (pose != null) {
                            val angles = JointAngleExtractor.extract(
                                pose = pose,
                                lift = request.lift,
                                cameraAngleDeg = request.cameraAngleDeg,
                            )
                            poseAngles += angles
                            poseProducedAngles = true
                        }
                    } catch (_: RuntimeException) {
                        poseFailed = true
                    }
                }

                if (estimatedFrames > 0) {
                    onProgress((decodedFrames.toFloat() / estimatedFrames).coerceIn(0f, 1f))
                }
            } finally {
                if (!frame.bitmap.isRecycled) frame.bitmap.recycle()
            }
        }

        check(observations.size >= 2) { "At least two ordered plate observations are required" }
        val calibration = Calibration.fromPlate(
            plateDiameterM = request.plateDiameterM,
            majorAxisPx = observations.first().majorAxisPx,
            cameraAngleDeg = request.cameraAngleDeg,
        ).copy(cameraLevel = request.cameraLevel)
        val barSamples = observations.map(calibration::toMeters)
        val motionSamples = Kinematics.derive(barSamples)
        val segmentedReps = PhaseSegmenter.segment(motionSamples, request.lift)
        val reps = mutableListOf<RepMetrics>()
        val validSlices = mutableListOf<RepSlice>()
        val extraWarnings = mutableListOf<String>()
        var bestMcvSoFar: Double? = null

        for (slice in segmentedReps) {
            try {
                val metrics = RepMetricsCalculator.compute(
                    rep = slice,
                    samples = motionSamples,
                    loadKg = request.loadKg,
                    bestMcvMps = bestMcvSoFar,
                )
                reps += metrics
                validSlices += slice
                bestMcvSoFar = maxOf(bestMcvSoFar ?: 0.0, metrics.mcvMps)
            } catch (_: IllegalArgumentException) {
                extraWarnings += "REP_SKIPPED_${slice.index}"
            }
        }

        if (!poseProducedAngles) extraWarnings += "POSE_UNAVAILABLE"
        if (poseFailed && poseProducedAngles) extraWarnings += "POSE_PARTIAL"
        if (reps.isEmpty()) extraWarnings += "NO_VALID_REPS"

        val measuredFps = firstFrameSec?.let { first ->
            lastFrameSec?.takeIf { it > first && decodedFrames > 1 }?.let { last ->
                (decodedFrames - 1) / (last - first)
            }
        } ?: 0.0
        val fps = metadataFps.takeIf { it > 0.0 } ?: measuredFps
        val trackConfidence = observations.map { it.confidence.coerceIn(0.0, 1.0) }.average()
        val occlusionPct = if (decodedFrames > 0) missedFrames * 100.0 / decodedFrames else 100.0
        val repConfidence = if (segmentedReps.isEmpty()) 0.0 else reps.size.toDouble() / segmentedReps.size
        val evaluatedQuality = Quality.evaluate(
            trackConfidence = trackConfidence,
            occlusionPct = occlusionPct,
            repConfidence = repConfidence,
            calibrationConfidence = observations.first().confidence.coerceIn(0.0, 1.0),
            cameraLevel = request.cameraLevel,
        )
        val quality = evaluatedQuality.copy(
            warnings = (evaluatedQuality.warnings + extraWarnings).distinct(),
        )
        val anglesByRep = validSlices.mapNotNull { slice ->
            poseAngles.filter { it.tSec >= slice.startSec && it.tSec <= slice.endSec }
                .takeIf { it.isNotEmpty() }
                ?.let { slice.index to it }
        }.toMap()

        onProgress(1f)
        AnalysisResult(
            reps = reps,
            summary = summarize(reps, request.loadKg),
            quality = quality,
            fps = fps,
            motionSamples = motionSamples,
            repSlices = validSlices,
            frameAnglesByRep = anglesByRep,
            lift = request.lift,
            loadKg = request.loadKg,
        )
    }

    private companion object {
        const val POSE_FRAME_STRIDE = 3
    }
}

internal fun summarize(reps: List<RepMetrics>, loadKg: Double?): SetSummary {
    val best = reps.maxOfOrNull { it.mcvMps } ?: 0.0
    val last = reps.lastOrNull()?.mcvMps
    val positiveLoad = loadKg?.takeIf { it > 0.0 }
    return SetSummary(
        repCount = reps.size,
        mcvAvgMps = reps.takeIf { it.isNotEmpty() }?.map { it.mcvMps }?.average() ?: 0.0,
        bestMcvMps = best,
        velocityLossPct = if (best > 0.0 && last != null) (best - last) / best * 100.0 else null,
        totalVolumeKg = positiveLoad?.times(reps.size),
        totalWorkJ = positiveLoad?.let { reps.sumOf { it.workJ ?: 0.0 } },
    )
}
