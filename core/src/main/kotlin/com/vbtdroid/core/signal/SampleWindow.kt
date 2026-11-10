package com.vbtdroid.core.signal

import kotlin.math.roundToInt

/**
 * Converts a smoothing window expressed in **seconds** into an odd sample count for the actual
 * frame rate.
 *
 * Sample-count windows silently change the smoothing time when the frame rate changes: at 60 fps
 * a 9-sample window covers 0.15 s, at 30 fps it covers 0.30 s, so the same clip produces
 * different velocity peaks on different devices. Every filter window in the pipeline is therefore
 * specified in seconds and converted here with the median frame interval.
 */
object SampleWindow {
    const val MIN_SAMPLES = 3

    fun samplesFor(
        timesSec: DoubleArray,
        windowSec: Double,
        minSamples: Int = MIN_SAMPLES,
    ): Int {
        require(minSamples >= 1) { "minSamples must be positive" }
        if (timesSec.isEmpty()) return minSamples
        if (timesSec.size <= minSamples) return timesSec.size
        if (!windowSec.isFinite() || windowSec <= 0.0) {
            return minSamples.coerceAtMost(timesSec.size)
        }
        val deltas = ArrayList<Double>(timesSec.size - 1)
        for (index in 1 until timesSec.size) {
            val dt = timesSec[index] - timesSec[index - 1]
            if (dt.isFinite() && dt > 0.0) deltas.add(dt)
        }
        if (deltas.isEmpty()) return minSamples.coerceAtMost(timesSec.size)
        deltas.sort()
        val median = deltas[deltas.size / 2]
        if (!median.isFinite() || median <= 0.0) {
            return minSamples.coerceAtMost(timesSec.size)
        }
        var count = (windowSec / median).roundToInt().coerceAtLeast(minSamples)
        if (count % 2 == 0) count += 1
        return count.coerceAtMost(timesSec.size)
    }
}
