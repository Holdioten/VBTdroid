package com.vbtdroid.core.replay

import com.vbtdroid.core.calibration.Calibration
import com.vbtdroid.core.metrics.Kinematics
import com.vbtdroid.core.metrics.RepMetricException
import com.vbtdroid.core.metrics.RepMetrics
import com.vbtdroid.core.metrics.RepMetricsCalculator
import com.vbtdroid.core.metrics.StickingRegionDetector
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.model.PlateObservation
import com.vbtdroid.core.phases.PhaseSegmenter
import com.vbtdroid.core.phases.PhaseType
import com.vbtdroid.core.phases.RepSlice
import com.vbtdroid.core.signal.TrackContinuity
import com.vbtdroid.core.signal.TrackSmoother
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Dev-only replay: takes detector observations exported by the PC script and runs the exact core
 * pipeline (smoothing, fixed calibration, kinematics, segmentation, metrics). Disabled unless
 * VBT_REPLAY_DIR points at a folder containing observations_far.json / observations_near.json.
 */
class ObservationReplayTest {
    private val json = Json

    @Test
    fun `replay recorded observations`() {
        val dir = System.getenv("VBT_REPLAY_DIR") ?: return
        val results = buildJsonObject {
            for (label in listOf("far", "near")) {
                val file = File(dir, "observations_$label.json")
                if (!file.exists()) continue
                put(label, replay(file, loadKg = 107.5, cameraAngleDeg = 45.0))
            }
        }
        File(dir, "replay_result.json").writeText(results.toString())
    }

    private fun replay(file: File, loadKg: Double, cameraAngleDeg: Double) = buildJsonObject {
        val root = json.parseToJsonElement(file.readText()).jsonObject
        val observations = root.getValue("observations").jsonArray.map { element ->
            val sample = element.jsonObject
            PlateObservation(
                tSec = sample.getValue("t").jsonPrimitive.double,
                xPx = sample.getValue("x").jsonPrimitive.double,
                yPx = sample.getValue("y").jsonPrimitive.double,
                majorAxisPx = sample.getValue("d").jsonPrimitive.double,
                confidence = sample.getValue("conf").jsonPrimitive.double,
            )
        }
        val durationSec = observations.last().tSec
        val trackBreak = TrackContinuity.detect(observations, durationSec)
        val smoothWindow = System.getenv("VBT_SMOOTH_WINDOW")?.toIntOrNull() ?: 5
        val meanWindow = System.getenv("VBT_MEAN_WINDOW")?.toIntOrNull() ?: 9
        val peakWindow = System.getenv("VBT_PEAK_WINDOW")?.toIntOrNull() ?: 5
        val cleaned = TrackSmoother.smooth(observations, smoothWindow = smoothWindow)
        val calibration = Calibration.fromPlate(
            plateDiameterM = 0.45,
            majorAxisPx = cleaned.first().majorAxisPx,
            cameraAngleDeg = cameraAngleDeg,
        )
        val barSamples = cleaned.map(calibration::toMeters)
        val motion = Kinematics.derive(barSamples, window = 9, polyOrder = 2)
        val peakMotion = Kinematics.derive(barSamples, window = 5, polyOrder = 2)
        val segmentation = PhaseSegmenter.segmentDetailed(motion, Lift.BENCH)
        val warnings = segmentation.warnings.toMutableList()

        val passed = mutableListOf<Pair<RepSlice, RepMetrics>>()
        for (slice in segmentation.reps) {
            try {
                passed += slice to RepMetricsCalculator.compute(
                    rep = slice,
                    samples = motion,
                    loadKg = loadKg,
                    peakSamples = peakMotion,
                )
            } catch (error: RepMetricException) {
                warnings += "REP_${slice.index}_${error.reason}"
            } catch (_: IllegalArgumentException) {
                warnings += "REP_SKIPPED_${slice.index}"
            }
        }
        val bestMcv = passed.maxOfOrNull { it.second.mcvMps }
        val reps = passed.map { (slice, metrics) ->
            val withLoss = if (bestMcv != null && bestMcv > 0.0) {
                metrics.copy(velocityLossPct = (bestMcv - metrics.mcvMps) / bestMcv * 100.0)
            } else {
                metrics
            }
            slice to withLoss
        }

        put("pxPerM", calibration.pxPerM)
        put("durationSec", durationSec)
        put("sampleCount", observations.size)
        put("trackBreak", trackBreak?.reason?.name ?: "NONE")
        put("segmentationWarnings", buildJsonArray { warnings.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
        put("repCount", reps.size)
        put("mcvAvgMps", reps.map { it.second.mcvMps }.average())
        put("bestMcvMps", bestMcv ?: Double.NaN)
        put(
            "velocityLossPct",
            if (bestMcv != null && bestMcv > 0.0 && reps.isNotEmpty()) {
                (bestMcv - reps.last().second.mcvMps) / bestMcv * 100.0
            } else {
                Double.NaN
            },
        )
        put("totalVolumeKg", loadKg * reps.size)
        put("totalWorkJ", reps.mapNotNull { it.second.workJ }.sum())
        put("reps", buildJsonArray {
            reps.forEach { (slice, metrics) ->
                add(buildJsonObject {
                    put("index", slice.index)
                    put("startSec", slice.startSec)
                    put("endSec", slice.endSec)
                    put("phases", buildJsonArray {
                        slice.phases.forEach { phase ->
                            add(buildJsonObject {
                                put("type", phase.type.name)
                                put("startSec", phase.startSec)
                                put("endSec", phase.endSec)
                            })
                        }
                    })
                    put("durationS", metrics.durationS)
                    put("romTotalM", metrics.romTotalM)
                    put("romConcentricM", metrics.romConcentricM)
                    put("meanVelocityMps", metrics.meanVelocityMps)
                    put("mcvMps", metrics.mcvMps)
                    put("mpvMps", metrics.mpvMps ?: Double.NaN)
                    put("peakVelocityMps", metrics.peakVelocityMps ?: Double.NaN)
                    put("peakAccelerationMps2", metrics.peakAccelerationMps2 ?: Double.NaN)
                    put("peakDecelerationMps2", metrics.peakDecelerationMps2 ?: Double.NaN)
                    put("stickingPointPct", metrics.stickingPointPct ?: Double.NaN)
                    val concentricPhases = slice.phases.filter { it.type == PhaseType.CONCENTRIC }
                    val concentricWindow = motion.filter { sample ->
                        concentricPhases.any { sample.tSec >= it.startSec && sample.tSec <= it.endSec }
                    }
                    val region = if (concentricWindow.size >= 3) {
                        StickingRegionDetector.detect(
                            samples = concentricWindow,
                            startY = concentricWindow.first().yM,
                            romConcentric = concentricWindow.maxOf { it.yM } - concentricWindow.minOf { it.yM },
                        )
                    } else {
                        null
                    }
                    put("stickingRegion", buildJsonObject {
                        if (region == null) {
                            put("available", false)
                        } else {
                            put("available", true)
                            put("startPct", region.startPct)
                            put("endPct", region.endPct)
                            put("peakVelocityMps", region.peakVelocityMps)
                            put("minVelocityMps", region.minVelocityMps)
                            put("peakIndex", region.peakIndex)
                            put("minIndex", region.minIndex)
                        }
                    })
                    put("velocityLossPct", metrics.velocityLossPct ?: Double.NaN)
                    put("barPathHorizontalRangeM", metrics.barPathHorizontalRangeM)
                    put("barPathDeviationM", metrics.barPathDeviationM)
                    put("meanForceN", metrics.meanForceN ?: Double.NaN)
                    put("peakForceN", metrics.peakForceN ?: Double.NaN)
                    put("meanPowerW", metrics.meanPowerW ?: Double.NaN)
                    put("peakPowerW", metrics.peakPowerW ?: Double.NaN)
                    put("impulseNs", metrics.impulseNs ?: Double.NaN)
                    put("workJ", metrics.workJ ?: Double.NaN)
                    put("unavailableReasons", buildJsonArray {
                        metrics.unavailableReasons.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                    })
                    put("qualityWarnings", buildJsonArray {
                        metrics.qualityWarnings.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                    })
                    // Concentric velocity curve exactly as seen by the sticking detector.
                    val concentric = slice.phases.filter { it.type == PhaseType.CONCENTRIC }
                    val series = motion.filter { sample ->
                        concentric.any { sample.tSec >= it.startSec && sample.tSec <= it.endSec }
                    }
                    put("concentricT", buildJsonArray { series.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.tSec)) } })
                    put("concentricVy", buildJsonArray { series.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.vyMps)) } })
                })
            }
        })
    }
}
