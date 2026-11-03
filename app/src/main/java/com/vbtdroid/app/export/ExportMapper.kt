package com.vbtdroid.app.export

import com.vbtdroid.app.analysis.AnalysisRequest
import com.vbtdroid.app.analysis.AnalysisResult
import com.vbtdroid.core.io.CalibrationExport
import com.vbtdroid.core.io.LvpExport
import com.vbtdroid.core.io.PhaseExport
import com.vbtdroid.core.io.RepExport
import com.vbtdroid.core.io.SetExport
import com.vbtdroid.core.io.SummaryExport
import com.vbtdroid.core.metrics.LvpEstimate
import com.vbtdroid.core.metrics.RepMetrics
import com.vbtdroid.core.model.MetricId
import com.vbtdroid.core.phases.PhaseType

/** Single mapping used by Room, JSON and history; request metadata is never dropped. */
object ExportMapper {
    fun toSetExport(
        result: AnalysisResult,
        request: AnalysisRequest? = null,
        lvp: LvpEstimate? = result.lvp,
    ): SetExport {
        val calibration = result.calibration
        return SetExport(
            lift = (request?.lift ?: result.lift).exportId,
            variant = (request?.variant ?: result.variant).exportId,
            loadKg = request?.loadKg ?: result.loadKg,
            plateDiameterMm = (request?.plateDiameterM ?: result.plateDiameterM) * 1000.0,
            cameraAngleDeg = request?.cameraAngleDeg ?: result.cameraAngleDeg,
            videoUri = request?.uri?.toString() ?: result.videoUri,
            createdAtEpochMs = result.createdAtEpochMs,
            reps = result.reps.map { rep ->
                val slice = result.repSlices.firstOrNull { it.index == rep.repIndex }
                RepExport(
                    index = rep.repIndex,
                    metrics = rep.toMetricMap(),
                    quality = mapOf(
                        "track_confidence" to result.quality.trackConfidence,
                        "occlusion_pct" to result.quality.occlusionPct,
                        "rep_confidence" to result.quality.repConfidence,
                        "calibration_confidence" to result.quality.calibrationConfidence,
                    ),
                    warnings = (
                        result.repWarnings[rep.repIndex].orEmpty() + rep.unavailableReasons + rep.qualityWarnings
                        ).distinct(),
                    phases = slice?.phases.orEmpty().map { phase ->
                        PhaseExport(
                            type = phase.type.toExportId(),
                            startSec = phase.startSec,
                            endSec = phase.endSec,
                            manual = phase.manual,
                        )
                    },
                )
            },
            warnings = buildList {
                addAll(result.quality.warnings)
                if (result.loadKg == null) add("LOAD_REQUIRED")
                if (!result.cameraLevel) add("CAMERA_NOT_LEVEL_ESTIMATE")
            }.distinct(),
            calibration = calibration?.let {
                CalibrationExport(
                    pxPerM = it.pxPerM,
                    cameraLevel = it.cameraLevel,
                    calibrationConfidence = result.quality.calibrationConfidence,
                )
            },
            quality = mapOf(
                "track_confidence" to result.quality.trackConfidence,
                "occlusion_pct" to result.quality.occlusionPct,
                "rep_confidence" to result.quality.repConfidence,
                "calibration_confidence" to result.quality.calibrationConfidence,
                "effective_fps" to result.fps,
            ),
            summary = SummaryExport(
                repCount = result.summary.repCount,
                mcvAvgMps = result.summary.mcvAvgMps,
                bestMcvMps = result.summary.bestMcvMps,
                velocityLossPct = result.summary.velocityLossPct,
                totalVolumeKg = result.summary.totalVolumeKg,
                totalWorkJ = result.summary.totalWorkJ,
            ),
            filters = result.filterConfig,
            lvp = lvp?.let {
                LvpExport(
                    slope = it.model?.slope,
                    intercept = it.model?.intercept,
                    r2 = it.model?.r2,
                    v1rmMps = it.v1rmMps,
                    v1rmSource = it.v1rmSource,
                    estimated1RmKg = it.model?.estimated1RmKg,
                    pointCount = it.pointCount,
                    unavailableReason = it.unavailableReason,
                )
            },
        )
    }

    private fun RepMetrics.toMetricMap(): Map<String, Double?> = linkedMapOf(
        MetricId.REP_DURATION.id to durationS,
        MetricId.ROM_TOTAL.id to romTotalM,
        MetricId.ROM_CONCENTRIC.id to romConcentricM,
        MetricId.MEAN_VELOCITY.id to meanVelocityMps,
        MetricId.MEAN_CONCENTRIC_VELOCITY.id to mcvMps,
        MetricId.MEAN_PROPULSIVE_VELOCITY.id to mpvMps,
        MetricId.PEAK_VELOCITY.id to peakVelocityMps,
        MetricId.PEAK_ACCELERATION.id to peakAccelerationMps2,
        MetricId.PEAK_DECELERATION.id to peakDecelerationMps2,
        MetricId.STICKING_POINT.id to stickingPointPct,
        MetricId.VELOCITY_LOSS.id to velocityLossPct,
        MetricId.BAR_PATH_HORIZONTAL_RANGE.id to barPathHorizontalRangeM,
        MetricId.BAR_PATH_DEVIATION.id to barPathDeviationM,
        MetricId.MEAN_FORCE.id to meanForceN,
        MetricId.PEAK_FORCE.id to peakForceN,
        MetricId.MEAN_POWER.id to meanPowerW,
        MetricId.PEAK_POWER.id to peakPowerW,
        MetricId.IMPULSE.id to impulseNs,
        MetricId.WORK.id to workJ,
    )

    private fun PhaseType.toExportId(): String = when (this) {
        PhaseType.ECCENTRIC -> "eccentric_phase"
        PhaseType.PAUSE -> "pause_phase"
        PhaseType.CONCENTRIC -> "concentric_phase"
        PhaseType.LOCKOUT -> "lockout_phase"
    }
}
