package com.vbtdroid.core.metrics

import com.vbtdroid.core.phases.PhaseType
import com.vbtdroid.core.phases.RepSlice
import kotlin.math.abs

data class RepMetrics(
    val repIndex: Int,
    val durationS: Double,
    val romTotalM: Double,
    val romConcentricM: Double,
    val meanVelocityMps: Double,
    val mcvMps: Double,
    val mpvMps: Double?,
    val peakVelocityMps: Double?,
    val peakAccelerationMps2: Double?,
    val peakDecelerationMps2: Double?,
    val stickingPointPct: Double?,
    val velocityLossPct: Double?,
    val barPathHorizontalRangeM: Double,
    val barPathDeviationM: Double,
    val meanForceN: Double?,
    val peakForceN: Double?,
    val meanPowerW: Double?,
    val peakPowerW: Double?,
    val impulseNs: Double?,
    val workJ: Double?,
    val unavailableReasons: List<String> = emptyList(),
    val qualityWarnings: List<String> = emptyList(),
)

class RepMetricException(val reason: String) : IllegalArgumentException(reason)

object RepMetricsCalculator {
    private const val GRAVITY_MPS2 = 9.80665
    private const val MONOTONIC_TOLERANCE_M = 1e-6
    const val NON_MONOTONIC_CONCENTRIC = "NON_MONOTONIC_CONCENTRIC"
    const val ACCELERATION_UNAVAILABLE = "ACCELERATION_UNAVAILABLE"
    const val LOAD_REQUIRED = "LOAD_REQUIRED"

    /**
     * Computes mean metrics from [samples] and peak metrics from independently smoothed [peakSamples].
     * Invalid/non-monotonic concentric paths are rejected with an explicit quality reason.
     */
    fun compute(
        rep: RepSlice,
        samples: List<MotionSample>,
        loadKg: Double?,
        bestMcvMps: Double? = null,
        peakSamples: List<MotionSample> = samples,
    ): RepMetrics {
        val repSamples = samples.window(rep.startSec, rep.endSec)
        require(repSamples.size >= 2) { "rep must contain at least two motion samples" }
        require(repSamples.zipWithNext().all { (first, second) -> second.tSec > first.tSec }) {
            "rep timestamps must be strictly increasing"
        }

        val concentricPhases = rep.phases.filter { it.type == PhaseType.CONCENTRIC }
        require(concentricPhases.isNotEmpty()) { "rep must contain a concentric phase" }
        val concentricSamples = repSamples.inPhases(concentricPhases.map { it.startSec to it.endSec })
        require(concentricSamples.size >= 2) { "concentric phase must contain at least two motion samples" }

        val firstRep = repSamples.first()
        val lastRep = repSamples.last()
        val firstConcentric = concentricSamples.first()
        val lastConcentric = concentricSamples.last()
        val repDuration = lastRep.tSec - firstRep.tSec
        val concentricDuration = lastConcentric.tSec - firstConcentric.tSec
        require(repDuration > 0.0 && concentricDuration > 0.0) { "sample durations must be positive" }

        val romConcentric = lastConcentric.yM - firstConcentric.yM
        val nonMonotonic = concentricSamples.zipWithNext().any { (current, next) ->
            next.yM + MONOTONIC_TOLERANCE_M < current.yM
        }
        if (romConcentric <= 0.0) throw RepMetricException(NON_MONOTONIC_CONCENTRIC)

        val repYs = repSamples.map { it.yM }
        val repXs = repSamples.map { it.xM }
        val xOrigin = firstRep.xM
        val mcv = romConcentric / concentricDuration
        val meanPeakVelocityIndex = concentricSamples.indices.maxByOrNull { concentricSamples[it].vyMps }!!
        val allMeanAccelerationsAvailable = concentricSamples.all { it.ayMps2 != null }
        val firstNegativeAccelerationIndex = if (allMeanAccelerationsAvailable) {
            concentricSamples.indexOfFirst { (it.ayMps2 ?: 0.0) < 0.0 }
        } else {
            -1
        }
        val mpv = when {
            !allMeanAccelerationsAvailable -> null
            firstNegativeAccelerationIndex > 0 -> {
                val end = concentricSamples[firstNegativeAccelerationIndex]
                (end.yM - firstConcentric.yM) / (end.tSec - firstConcentric.tSec)
            }
            else -> mcv
        }

        val stickingPoint = (meanPeakVelocityIndex + 1 until concentricSamples.lastIndex)
            .firstOrNull { index ->
                val before = concentricSamples[index - 1].vyMps
                val current = concentricSamples[index].vyMps
                val after = concentricSamples[index + 1].vyMps
                current <= before && current <= after && (current < before || current < after)
            }
            ?.let { index ->
                (concentricSamples[index].yM - firstConcentric.yM) / romConcentric * 100.0
            }
        val velocityLoss = bestMcvMps?.takeIf { it > 0.0 }?.let { (it - mcv) / it * 100.0 }

        val concentricPeakSamples = peakSamples.window(rep.startSec, rep.endSec)
            .inPhases(concentricPhases.map { it.startSec to it.endSec })
        val peakVelocity = concentricPeakSamples.maxOfOrNull { it.vyMps }
        val allPeakAccelerationsAvailable = concentricPeakSamples.isNotEmpty() &&
            concentricPeakSamples.all { it.ayMps2 != null }
        val peakAcceleration = if (allPeakAccelerationsAvailable) {
            concentricPeakSamples.maxOf { it.ayMps2!! }
        } else {
            null
        }
        val peakDeceleration = if (allPeakAccelerationsAvailable) {
            concentricPeakSamples.minOf { it.ayMps2!! }
        } else {
            null
        }

        val reasons = mutableListOf<String>()
        if (!allMeanAccelerationsAvailable || !allPeakAccelerationsAvailable) reasons += ACCELERATION_UNAVAILABLE
        if (loadKg == null) reasons += LOAD_REQUIRED

        val positiveLoad = loadKg?.takeIf { it > 0.0 && it.isFinite() }
        val forceMetrics = if (positiveLoad != null && allMeanAccelerationsAvailable) {
            val mass = positiveLoad
            val forces = concentricSamples.map { mass * (GRAVITY_MPS2 + it.ayMps2!!) }
            val powers = forces.indices.map { forces[it] * concentricSamples[it].vyMps }
            var impulse = 0.0
            var work = 0.0
            for (index in 0 until concentricSamples.lastIndex) {
                val current = concentricSamples[index]
                val next = concentricSamples[index + 1]
                val meanForce = (forces[index] + forces[index + 1]) / 2.0
                impulse += meanForce * (next.tSec - current.tSec)
                work += meanForce * (next.yM - current.yM)
            }
            val peakForce = if (allPeakAccelerationsAvailable) {
                concentricPeakSamples.maxOf { mass * (GRAVITY_MPS2 + it.ayMps2!!) }
            } else {
                null
            }
            val peakPower = if (allPeakAccelerationsAvailable) {
                concentricPeakSamples.maxOf { mass * (GRAVITY_MPS2 + it.ayMps2!!) * it.vyMps }
            } else {
                null
            }
            LoadMetrics(
                meanForceN = impulse / concentricDuration,
                peakForceN = peakForce,
                meanPowerW = trapezoidMean(powers, concentricSamples),
                peakPowerW = peakPower,
                impulseNs = impulse,
                workJ = work,
            )
        } else {
            null
        }

        return RepMetrics(
            repIndex = rep.index,
            durationS = rep.endSec - rep.startSec,
            romTotalM = repYs.maxOrNull()!! - repYs.minOrNull()!!,
            romConcentricM = romConcentric,
            meanVelocityMps = (lastRep.yM - firstRep.yM) / repDuration,
            mcvMps = mcv,
            mpvMps = mpv,
            peakVelocityMps = peakVelocity,
            peakAccelerationMps2 = peakAcceleration,
            peakDecelerationMps2 = peakDeceleration,
            stickingPointPct = stickingPoint,
            velocityLossPct = velocityLoss,
            barPathHorizontalRangeM = repXs.maxOrNull()!! - repXs.minOrNull()!!,
            barPathDeviationM = repXs.maxOf { abs(it - xOrigin) },
            meanForceN = forceMetrics?.meanForceN,
            peakForceN = forceMetrics?.peakForceN,
            meanPowerW = forceMetrics?.meanPowerW,
            peakPowerW = forceMetrics?.peakPowerW,
            impulseNs = forceMetrics?.impulseNs,
            workJ = forceMetrics?.workJ,
            unavailableReasons = reasons.distinct(),
            qualityWarnings = if (nonMonotonic) listOf(NON_MONOTONIC_CONCENTRIC) else emptyList(),
        )
    }

    private fun List<MotionSample>.window(startSec: Double, endSec: Double): List<MotionSample> =
        asSequence().filter { it.tSec >= startSec && it.tSec <= endSec }.sortedBy { it.tSec }.toList()

    private fun List<MotionSample>.inPhases(phases: List<Pair<Double, Double>>): List<MotionSample> =
        filter { sample -> phases.any { (start, end) -> sample.tSec >= start && sample.tSec <= end } }

    private fun trapezoidMean(values: List<Double>, samples: List<MotionSample>): Double {
        var integral = 0.0
        for (index in 0 until samples.lastIndex) {
            integral += (values[index] + values[index + 1]) / 2.0 *
                (samples[index + 1].tSec - samples[index].tSec)
        }
        return integral / (samples.last().tSec - samples.first().tSec)
    }

    private data class LoadMetrics(
        val meanForceN: Double,
        val peakForceN: Double?,
        val meanPowerW: Double,
        val peakPowerW: Double?,
        val impulseNs: Double,
        val workJ: Double,
    )
}
