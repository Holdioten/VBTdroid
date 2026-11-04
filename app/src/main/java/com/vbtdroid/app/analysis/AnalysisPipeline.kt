package com.vbtdroid.app.analysis

import android.graphics.RectF
import android.net.Uri
import com.vbtdroid.app.video.VideoDecoder
import com.vbtdroid.app.video.VideoLimits
import com.vbtdroid.app.vision.PlateTracker
import com.vbtdroid.app.vision.PoseEstimator
import com.vbtdroid.app.vision.PoseFrame
import com.vbtdroid.core.calibration.Calibration
import com.vbtdroid.core.io.FilterConfigExport
import com.vbtdroid.core.metrics.Kinematics
import com.vbtdroid.core.metrics.LvpEstimate
import com.vbtdroid.core.metrics.MotionSample
import com.vbtdroid.core.metrics.RepMetricException
import com.vbtdroid.core.metrics.RepMetrics
import com.vbtdroid.core.metrics.RepMetricsCalculator
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.model.LiftVariant
import com.vbtdroid.core.model.PlateObservation
import com.vbtdroid.core.phases.PhaseSegmenter
import com.vbtdroid.core.phases.RepSlice
import com.vbtdroid.core.quality.Quality
import com.vbtdroid.core.quality.QualityFlags
import com.vbtdroid.core.signal.TrackSmoother
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
    val variant: LiftVariant = LiftVariant.DEFAULT,
    /** When false, pose estimation is skipped completely and no joint-angle data is produced. */
    val analyzePose: Boolean = true,
)

data class SetSummary(
    val repCount: Int,
    val mcvAvgMps: Double?,
    val bestMcvMps: Double?,
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
    val peakMotionSamples: List<MotionSample> = emptyList(),
    val repSlices: List<RepSlice> = emptyList(),
    val frameAnglesByRep: Map<Int, List<FrameAngles>> = emptyMap(),
    val observations: List<PlateObservation> = emptyList(),
    val poseFrames: List<PoseFrame> = emptyList(),
    val frameAngles: List<FrameAngles> = emptyList(),
    val analyzePose: Boolean = true,
    val lift: Lift = Lift.SQUAT,
    val variant: LiftVariant = LiftVariant.DEFAULT,
    val loadKg: Double? = null,
    val videoUri: String = "",
    val plateDiameterM: Double = 0.45,
    val cameraAngleDeg: Double = 90.0,
    val cameraLevel: Boolean = true,
    val calibration: Calibration? = null,
    val filterConfig: FilterConfigExport? = null,
    val repWarnings: Map<Int, List<String>> = emptyMap(),
    val lvp: LvpEstimate? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val savedSetId: String? = null,
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
        require(request.loadKg == null || (request.loadKg.isFinite() && request.loadKg > 0.0)) {
            "Load must be positive and finite"
        }
        tracker.reset()
        if (request.analyzePose) poseEstimator.reset()

        val meta = decoder.readMeta(request.uri)
        VideoLimits.requireSupportedDuration(meta.durationSec)
        val metadataFps = meta.effectiveFps.takeIf { it > 0.0 }
            ?: meta.nominalFps.takeIf { it > 0.0 }
            ?: 0.0
        val estimatedFrames = (meta.durationSec * metadataFps).roundToInt()
        val observations = mutableListOf<PlateObservation>()
        val poseFrames = mutableListOf<PoseFrame>()
        val frameAngles = mutableListOf<FrameAngles>()
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
                if (request.analyzePose && (decodedFrames - 1) % POSE_FRAME_STRIDE == 0) {
                    try {
                        val pose = poseEstimator.estimate(frame.bitmap, frame.tSec)
                        if (pose != null) {
                            poseFrames += pose
                            val angles = JointAngleExtractor.extract(
                                pose = pose,
                                lift = request.lift,
                                cameraAngleDeg = request.cameraAngleDeg,
                            )
                            frameAngles += angles
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
        // The videos are purpose-shot with one unobstructed plate, so the whole track can be
        // cleaned offline: radius/innovation gates remove wrong-object frames, then a zero-phase
        // Savitzky-Golay pass smooths the bar path before any metric is derived.
        val cleanedObservations = TrackSmoother.smooth(observations)
        val calibration = Calibration.fromPlate(
            plateDiameterM = request.plateDiameterM,
            majorAxisPx = cleanedObservations.first().majorAxisPx,
            cameraAngleDeg = request.cameraAngleDeg,
        ).copy(cameraLevel = request.cameraLevel)
        val barSamples = cleanedObservations.map(calibration::toMeters)
        val motionSamples = Kinematics.derive(barSamples, window = MEAN_FILTER_WINDOW, polyOrder = FILTER_ORDER)
        val peakMotionSamples = Kinematics.derive(barSamples, window = PEAK_FILTER_WINDOW, polyOrder = FILTER_ORDER)
        val segmentation = PhaseSegmenter.segmentDetailed(motionSamples, request.lift)
        val segmentedReps = segmentation.reps
        val computation = computeValidReps(
            segmentedReps = segmentedReps,
            motionSamples = motionSamples,
            loadKg = request.loadKg,
            peakMotionSamples = peakMotionSamples,
        )
        val reps = computation.reps
        val validSlices = computation.validSlices
        val extraWarnings = (computation.warnings + segmentation.warnings).toMutableList()

        val poseConfidence = frameAngles.map { it.confidence }.average().takeIf { it.isFinite() } ?: 0.0
        if (request.analyzePose) {
            if (!poseProducedAngles) {
                extraWarnings += POSE_UNAVAILABLE
            } else {
                if (poseFailed) extraWarnings += POSE_PARTIAL
                if (poseConfidence < POSE_LOW_CONFIDENCE_THRESHOLD) {
                    extraWarnings += POSE_LOW_CONFIDENCE
                }
            }
        }
        if (reps.isEmpty()) extraWarnings += "NO_VALID_REPS"

        val measuredFps = firstFrameSec?.let { first ->
            lastFrameSec?.takeIf { it > first && decodedFrames > 1 }?.let { last ->
                (decodedFrames - 1) / (last - first)
            }
        } ?: 0.0
        val fps = metadataFps.takeIf { it > 0.0 } ?: measuredFps
        // Quality stays honest about the raw track; metrics use the cleaned one.
        val trackConfidence = observations.map { it.confidence.coerceIn(0.0, 1.0) }.average()
        val lowConfidenceFrames = observations.count { it.confidence < 0.6 }
        val occlusionPct = if (decodedFrames > 0) {
            (missedFrames + lowConfidenceFrames) * 100.0 / decodedFrames
        } else {
            100.0
        }
        val repConfidence = if (validSlices.isEmpty()) {
            0.0
        } else {
            validSlices.map { PhaseSegmenter.confidence(it) }.average()
        }
        val evaluatedQuality = Quality.evaluate(
            trackConfidence = trackConfidence,
            occlusionPct = occlusionPct,
            repConfidence = repConfidence,
            calibrationConfidence = observations.first().confidence.coerceIn(0.0, 1.0),
            cameraLevel = request.cameraLevel,
            loadProvided = request.loadKg != null,
        )
        val quality = evaluatedQuality.copy(
            // Occlusion degrades confidence but no longer hides a requested joint-angle section.
            warnings = (evaluatedQuality.warnings + extraWarnings)
                .filterNot { it == ANGLES_UNAVAILABLE_OCCLUSION }
                .distinct(),
            anglesAvailable = request.analyzePose && poseProducedAngles,
            metricReasons = evaluatedQuality.metricReasons - "joint_angles",
        )
        // Smooth the pose series once; both the report and the burned-in overlay use it.
        val smoothedFrameAngles = JointAngleSmoother.smooth(frameAngles)
        val anglesByRep = if (!request.analyzePose) {
            emptyMap()
        } else {
            validSlices.mapNotNull { slice ->
                smoothedFrameAngles.filter { it.tSec >= slice.startSec && it.tSec <= slice.endSec }
                    .takeIf { it.isNotEmpty() }
                    ?.let { slice.index to it }
            }.toMap()
        }

        onProgress(1f)
        AnalysisResult(
            reps = reps,
            summary = summarize(reps, request.loadKg),
            quality = quality,
            fps = fps,
            motionSamples = motionSamples,
            peakMotionSamples = peakMotionSamples,
            repSlices = validSlices,
            filterConfig = FilterConfigExport(
                meanWindow = MEAN_FILTER_WINDOW,
                meanPolyOrder = FILTER_ORDER,
                peakWindow = PEAK_FILTER_WINDOW,
                peakPolyOrder = FILTER_ORDER,
                meanAppliesTo = listOf("mcv_mps", "mpv_mps", "mean_force_n", "mean_power_w"),
                peakAppliesTo = listOf("peak_velocity_mps", "peak_acceleration_mps2", "peak_force_n", "peak_power_w"),
            ),
            frameAnglesByRep = anglesByRep,
            observations = cleanedObservations.toList(),
            poseFrames = poseFrames.toList(),
            frameAngles = smoothedFrameAngles,
            analyzePose = request.analyzePose,
            lift = request.lift,
            variant = request.variant,
            loadKg = request.loadKg,
            videoUri = request.uri.toString(),
            plateDiameterM = request.plateDiameterM,
            cameraAngleDeg = request.cameraAngleDeg,
            cameraLevel = request.cameraLevel,
            calibration = calibration,
            repWarnings = reps.associate { rep ->
                rep.repIndex to buildList {
                    addAll(rep.unavailableReasons)
                    addAll(rep.qualityWarnings)
                    if (request.loadKg == null) add("LOAD_REQUIRED")
                    if (!request.cameraLevel) add("CAMERA_NOT_LEVEL_ESTIMATE")
                    if (trackConfidence < 0.6) add("LOW_TRACK_CONFIDENCE")
                    if (occlusionPct > 20.0) add("ANGLES_UNAVAILABLE_OCCLUSION")
                    if (validSlices.firstOrNull { it.index == rep.repIndex }?.phases?.any { it.manual } == true) {
                        add("MANUAL_PHASES")
                    }
                }.distinct()
            },
        )
    }

    private companion object {
        const val POSE_FRAME_STRIDE = 3
        const val MEAN_FILTER_WINDOW = 9
        const val PEAK_FILTER_WINDOW = 5
        const val FILTER_ORDER = 2
        const val POSE_LOW_CONFIDENCE_THRESHOLD = 0.6
        const val POSE_UNAVAILABLE = "POSE_UNAVAILABLE"
        const val POSE_PARTIAL = "POSE_PARTIAL"
        const val POSE_LOW_CONFIDENCE = "POSE_LOW_CONFIDENCE"
        const val ANGLES_UNAVAILABLE_OCCLUSION = "ANGLES_UNAVAILABLE_OCCLUSION"
    }
}

internal data class RepComputation(
    val reps: List<RepMetrics>,
    val validSlices: List<RepSlice>,
    val warnings: List<String>,
)

/** Computes every MCV before assigning velocity loss against the full-set best. */
internal fun computeValidReps(
    segmentedReps: List<RepSlice>,
    motionSamples: List<MotionSample>,
    loadKg: Double?,
    peakMotionSamples: List<MotionSample> = motionSamples,
): RepComputation {
    val valid = mutableListOf<Pair<RepSlice, RepMetrics>>()
    val warnings = mutableListOf<String>()
    for (slice in segmentedReps) {
        try {
            val metrics = RepMetricsCalculator.compute(
                rep = slice,
                samples = motionSamples,
                loadKg = loadKg,
                peakSamples = peakMotionSamples,
            )
            valid += slice to metrics
        } catch (error: RepMetricException) {
            warnings += "REP_${slice.index}_${error.reason}"
        } catch (_: IllegalArgumentException) {
            warnings += "REP_SKIPPED_${slice.index}"
        }
    }
    return RepComputation(
        reps = withFullSetVelocityLoss(valid.map { it.second }),
        validSlices = valid.map { it.first },
        warnings = warnings,
    )
}

internal fun withFullSetVelocityLoss(reps: List<RepMetrics>): List<RepMetrics> {
    val best = reps.maxOfOrNull { it.mcvMps }?.takeIf { it > 0.0 } ?: return reps
    return reps.map { rep ->
        rep.copy(velocityLossPct = (best - rep.mcvMps) / best * 100.0)
    }
}

internal fun summarize(reps: List<RepMetrics>, loadKg: Double?): SetSummary {
    val best = reps.maxOfOrNull { it.mcvMps }
    val last = reps.lastOrNull()?.mcvMps
    val positiveLoad = loadKg?.takeIf { it > 0.0 }
    return SetSummary(
        repCount = reps.size,
        mcvAvgMps = reps.takeIf { it.isNotEmpty() }?.map { it.mcvMps }?.average(),
        bestMcvMps = best,
        velocityLossPct = if (best != null && best > 0.0 && last != null) {
            (best - last) / best * 100.0
        } else {
            null
        },
        totalVolumeKg = positiveLoad?.times(reps.size),
        totalWorkJ = positiveLoad?.let {
            reps.mapNotNull { rep -> rep.workJ }.takeIf { work -> work.isNotEmpty() }?.sum()
        },
    )
}
