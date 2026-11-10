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
)

object StickingRegionDetector {
    /** Minimum absolute drop from Vpeak1 to Vmin; rejects flat/noisy curves. */
    const val MIN_PROMINENCE_MPS = 0.03

    /** Relative prominence against the concentric peak velocity. */
    const val PROMINENCE_FRACTION = 0.05

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
        if (samples.size < 3 || !startY.isFinite() || !romConcentric.isFinite() || romConcentric <= 0.0) {
            return null
        }
        val peakVelocity = samples.maxOf { it.vyMps }
        if (!peakVelocity.isFinite()) return null
        val prominence = max(
            if (minProminenceMps.isFinite()) minProminenceMps else MIN_PROMINENCE_MPS,
            peakVelocity * PROMINENCE_FRACTION,
        )

        // First local peak after the start, not the global maximum: Vpeak2 can be higher than
        // Vpeak1, and the sticking region must still start at the first peak.
        var peak = -1
        for (index in 1 until samples.lastIndex) {
            val velocity = samples[index].vyMps
            if (velocity >= samples[index - 1].vyMps &&
                velocity > samples[index + 1].vyMps &&
                velocity - samples.first().vyMps >= prominence
            ) {
                peak = index
                break
            }
        }
        if (peak < 0) {
            peak = samples.indices.maxByOrNull { samples[it].vyMps } ?: return null
        }

        var min = -1
        for (index in peak + 1 until samples.lastIndex) {
            if (samples[index].vyMps <= samples[index - 1].vyMps &&
                samples[index].vyMps <= samples[index + 1].vyMps
            ) {
                min = index
                break
            }
        }
        if (min < 0) return null
        // Resolve a flat minimum to its last sample.
        while (min + 1 < samples.lastIndex &&
            abs(samples[min + 1].vyMps - samples[min].vyMps) <= 1e-9
        ) {
            min++
        }
        if (samples[peak].vyMps - samples[min].vyMps < prominence) return null

        val startPct = ((samples[peak].yM - startY) / romConcentric * 100.0).coerceIn(0.0, 100.0)
        val endPct = ((samples[min].yM - startY) / romConcentric * 100.0).coerceIn(0.0, 100.0)
        return StickingRegion(
            peakIndex = peak,
            minIndex = min,
            peakVelocityMps = samples[peak].vyMps,
            minVelocityMps = samples[min].vyMps,
            startPct = startPct,
            endPct = endPct,
        )
    }
}
