package com.vbtdroid.core.metrics

import kotlin.math.abs
import kotlin.math.max

/**
 * The sticking region of one concentric phase: the descending segment from the first local
 * velocity peak (Vpeak1) to the following local minimum (Vmin).
 *
 * This follows the morphological VBT definition. It is deliberately not "any negative
 * acceleration": the terminal braking phase after Vpeak2 is also negative but is not sticking.
 */
data class StickingRegion(
    val peakIndex: Int,
    val minIndex: Int,
    val peakVelocityMps: Double,
    val minVelocityMps: Double,
    /** Vpeak1 position as a percentage of the concentric ROM. */
    val startPct: Double,
    /** Vmin position as a percentage of the concentric ROM. */
    val endPct: Double,
    /** Drop that had to be exceeded for this region to be reported. */
    val prominenceMps: Double,
)

object StickingRegionDetector {
    /**
     * Absolute minimum drop from Vpeak1 to Vmin. 3 cm/s was below the detector noise of 60 fps
     * clips (the plate box centre jitters by 1-3 px), which produced false bands on the first
     * samples of the concentric phase.
     */
    const val MIN_PROMINENCE_MPS = 0.05

    /** Relative prominence against the concentric peak velocity. */
    const val PROMINENCE_FRACTION = 0.08

    /** Extrema closer than this are the same morphological event, not separate peaks/valleys. */
    const val MIN_EXTREMUM_SEPARATION_SEC = 0.10

    /**
     * A real Vpeak1 cannot sit on the start of the concentric phase: the first samples are a
     * velocity ramp plus the phase boundary, and their tiny wobbles used to be taken as a peak.
     */
    const val MIN_START_PCT = 15.0

    /**
     * @param samples concentric samples, ascending time
     * @param startY yM of the first concentric sample
     * @param romConcentric positive vertical travel of the concentric phase
     */
    fun detect(
        samples: List<MotionSample>,
        startY: Double,
        romConcentric: Double,
        minProminenceMps: Double = MIN_PROMINENCE_MPS,
    ): StickingRegion? {
        if (samples.size < 5 || !startY.isFinite() || !romConcentric.isFinite() || romConcentric <= 0.0) {
            return null
        }
        val peakVelocity = samples.maxOf { it.vyMps }
        if (!peakVelocity.isFinite()) return null
        val prominence = max(
            if (minProminenceMps.isFinite()) minProminenceMps else MIN_PROMINENCE_MPS,
            peakVelocity * PROMINENCE_FRACTION,
        )
        val velocities = DoubleArray(samples.size) { samples[it].vyMps }

        val maxima = collapse(
            indices = localMaxima(velocities),
            samples = samples,
            separationSec = MIN_EXTREMUM_SEPARATION_SEC,
            better = { candidate, current -> velocities[candidate] > velocities[current] },
        )
        val minima = collapse(
            indices = localMinima(velocities),
            samples = samples,
            separationSec = MIN_EXTREMUM_SEPARATION_SEC,
            better = { candidate, current -> velocities[candidate] < velocities[current] },
        )

        for (peak in maxima) {
            val startPct = positionPct(samples, peak, startY, romConcentric) ?: continue
            if (startPct < MIN_START_PCT) continue
            if (velocities[peak] - velocities.first() < prominence) continue

            // Only the dip belonging to this peak counts: once velocity climbs above Vpeak1 the
            // bar has started Vpeak2 and any later valley is a different morphological event.
            var limit = samples.size
            for (index in peak + 1 until samples.size) {
                if (velocities[index] > velocities[peak]) {
                    limit = index
                    break
                }
            }
            val valley = minima.firstOrNull { index ->
                index > peak &&
                    index < limit &&
                    velocities[peak] - velocities[index] >= prominence
            } ?: continue

            // A peak-valley-peak structure is the reliable morphological signature. Without a
            // re-acceleration the "valley" is just a slowdown, not a sticking region.
            val rise = (valley + 1 until samples.size).maxOfOrNull { velocities[it] }
                ?: continue
            if (rise - velocities[valley] < prominence) continue

            var minIndex = valley
            while (minIndex + 1 < limit &&
                abs(velocities[minIndex + 1] - velocities[minIndex]) <= 1e-9
            ) {
                minIndex++
            }
            val endPct = positionPct(samples, minIndex, startY, romConcentric) ?: continue
            if (endPct <= startPct) continue
            return StickingRegion(
                peakIndex = peak,
                minIndex = minIndex,
                peakVelocityMps = velocities[peak],
                minVelocityMps = velocities[minIndex],
                startPct = startPct,
                endPct = endPct,
                prominenceMps = prominence,
            )
        }
        return null
    }

    private fun positionPct(
        samples: List<MotionSample>,
        index: Int,
        startY: Double,
        romConcentric: Double,
    ): Double? {
        val pct = (samples[index].yM - startY) / romConcentric * 100.0
        return pct.takeIf { it.isFinite() && it in 0.0..100.0 }
    }

    private fun localMaxima(velocities: DoubleArray): List<Int> = buildList {
        for (index in 1 until velocities.lastIndex) {
            if (velocities[index] >= velocities[index - 1] && velocities[index] > velocities[index + 1]) {
                add(index)
            }
        }
    }

    private fun localMinima(velocities: DoubleArray): List<Int> = buildList {
        for (index in 1 until velocities.lastIndex) {
            if (velocities[index] <= velocities[index - 1] && velocities[index] <= velocities[index + 1]) {
                add(index)
            }
        }
    }

    /** Merges extrema that are closer than [separationSec], keeping the more extreme of the two. */
    private fun collapse(
        indices: List<Int>,
        samples: List<MotionSample>,
        separationSec: Double,
        better: (candidate: Int, current: Int) -> Boolean,
    ): List<Int> {
        val kept = ArrayList<Int>(indices.size)
        for (index in indices) {
            val last = kept.lastOrNull()
            if (last != null && samples[index].tSec - samples[last].tSec < separationSec) {
                if (better(index, last)) kept[kept.lastIndex] = index
            } else {
                kept.add(index)
            }
        }
        return kept
    }
}
