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
    val mpvMps: Double,
    val peakVelocityMps: Double,
    val peakAccelerationMps2: Double,
    val peakDecelerationMps2: Double,
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
)

object RepMetricsCalculator {
    private const val GRAVITY_MPS2 = 9.80665

    fun compute(
        rep: RepSlice,
        samples: List<MotionSample>,
        loadKg: Double?,
        bestMcvMps: Double? = null,
    ): RepMetrics {
        val repSamples = samples
            .asSequence()
            .filter { it.tSec >= rep.startSec && it.tSec <= rep.endSec }
            .sortedBy { it.tSec }
            .toList()
        require(repSamples.size >= 2) { "rep must contain at least two motion samples" }

        val concentricPhases = rep.phases.filter { it.type == PhaseType.CONCENTRIC }
        require(concentricPhases.isNotEmpty()) { "rep must contain a concentric phase" }
        val concentricSamples = repSamples.filter { sample ->
            concentricPhases.any { sample.tSec >= it.startSec && sample.tSec <= it.endSec }
        }
        require(concentricSamples.size >= 2) { "concentric phase must contain at least two motion samples" }

        val firstRep = repSamples.first()
        val lastRep = repSamples.last()
        val firstConcentric = concentricSamples.first()
        val lastConcentric = concentricSamples.last()
        val repDuration = lastRep.tSec - firstRep.tSec
        val concentricDuration = lastConcentric.tSec - firstConcentric.tSec
        require(repDuration > 0.0 && concentricDuration > 0.0) { "sample durations must be positive" }

        val repYs = repSamples.map { it.yM }
        val concentricYs = concentricSamples.map { it.yM }
        val repXs = repSamples.map { it.xM }
        val xOrigin = firstRep.xM
        val mcv = (lastConcentric.yM - firstConcentric.yM) / concentricDuration
        val peakVelocityIndex = concentricSamples.indices.maxBy { concentricSamples[it].vyMps }
        val peakVelocity = concentricSamples[peakVelocityIndex].vyMps
        val firstNegativeAccelerationIndex = concentricSamples.indexOfFirst { it.ayMps2 < 0.0 }
        val mpv = if (firstNegativeAccelerationIndex > 0) {
            val end = concentricSamples[firstNegativeAccelerationIndex]
            (end.yM - firstConcentric.yM) / (end.tSec - firstConcentric.tSec)
        } else {
            mcv
        }
        val romConcentric = concentricYs.maxOrNull()!! - concentricYs.minOrNull()!!

        val stickingPoint = if (romConcentric > 0.0) {
            (peakVelocityIndex + 1 until concentricSamples.lastIndex)
                .firstOrNull { index ->
                    val before = concentricSamples[index - 1].vyMps
                    val current = concentricSamples[index].vyMps
                    val after = concentricSamples[index + 1].vyMps
                    current <= before && current <= after && (current < before || current < after)
                }
                ?.let { index ->
                    (concentricSamples[index].yM - firstConcentric.yM) / romConcentric * 100.0
                }
        } else {
            null
        }
        val velocityLoss = bestMcvMps?.takeIf { it > 0.0 }
            ?.let { (it - mcv) / it * 100.0 }

        val positiveLoad = loadKg?.takeIf { it > 0.0 }
        val forceMetrics = positiveLoad?.let { mass ->
            val forces = concentricSamples.map { mass * (GRAVITY_MPS2 + it.ayMps2) }
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
            LoadMetrics(
                meanForceN = impulse / concentricDuration,
                peakForceN = forces.maxOrNull()!!,
                meanPowerW = trapezoidMean(powers, concentricSamples),
                peakPowerW = powers.maxOrNull()!!,
                impulseNs = impulse,
                workJ = work,
            )
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
            peakAccelerationMps2 = concentricSamples.maxOf { it.ayMps2 },
            peakDecelerationMps2 = concentricSamples.minOf { it.ayMps2 },
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
        )
    }

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
        val peakForceN: Double,
        val meanPowerW: Double,
        val peakPowerW: Double,
        val impulseNs: Double,
        val workJ: Double,
    )
}
