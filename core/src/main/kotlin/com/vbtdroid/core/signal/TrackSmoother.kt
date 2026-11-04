package com.vbtdroid.core.signal

import com.vbtdroid.core.model.PlateObservation
import kotlin.math.abs
import kotlin.math.max

/**
 * Offline cleanup for a whole plate track before metrics are derived.
 *
 * The app analyzes purpose-shot side-view clips: one unobstructed plate, controlled camera,
 * smooth bar motion. That makes a full forward-backward Kalman/RTS overkill; a robust
 * three-stage pass over the complete track is enough:
 *
 * 1. Radius gate: drop samples whose plate diameter deviates more than 15% from the track
 *    median. A plate cannot change size, so a different radius is a wrong object or a partial
 *    arc fit.
 * 2. Innovation gate: drop a sample whose center jumps farther than physically plausible for
 *    the bar (0.5 plate diameters, or [MAX_SPEED_DIAMETERS_PER_SEC] diameters per second when
 *    frames are slower than that). Catches teleports and identity switches.
 * 3. Hampel filter + Savitzky-Golay pass: replace/remove single-frame spikes, then smooth the
 *    surviving x/y series. Savitzky-Golay is symmetric, so it is zero-phase (equivalent to
 *    forward-backward filtering) and preserves velocity peaks better than a moving average.
 *
 * Output keeps the surviving timestamps and confidences; only x/y are smoothed. A track with
 * too few clean samples is returned unchanged rather than flattened.
 */
object TrackSmoother {
    const val MAX_RADIUS_DEVIATION = 0.15
    const val MAX_INNOVATION_DIAMETERS = 0.5
    const val MAX_SPEED_DIAMETERS_PER_SEC = 8.0
    const val HAMPEL_WINDOW = 5
    const val HAMPEL_SIGMAS = 3.0
    const val HAMPEL_FLOOR_PX = 1.0
    const val SMOOTH_WINDOW = 5
    const val SMOOTH_ORDER = 2

    fun smooth(observations: List<PlateObservation>): List<PlateObservation> {
        val ordered = observations
            .filter { observation ->
                observation.tSec.isFinite() &&
                    observation.xPx.isFinite() &&
                    observation.yPx.isFinite() &&
                    observation.majorAxisPx.isFinite() &&
                    observation.confidence.isFinite()
            }
            .sortedBy { it.tSec }
        if (ordered.size < 3) return ordered

        // Calibration is fixed from the first frames, so the radius gate must use that same
        // early reference. A whole-track median would let a later camera move dominate and
        // discard the good early reps instead of the invalid later ones.
        val referenceAxis = TrackContinuity.referenceAxisPx(ordered)
        if (!referenceAxis.isFinite() || referenceAxis <= 0.0) return ordered

        val radiusGated = ordered.filter { observation ->
            abs(observation.majorAxisPx - referenceAxis) / referenceAxis <= MAX_RADIUS_DEVIATION
        }
        if (radiusGated.size < 3) return ordered

        val innovationGated = innovationGate(radiusGated, referenceAxis)
        if (innovationGated.size < 3) return ordered

        val cleaned = hampel(innovationGated)
        if (cleaned.size < 3) return ordered

        val smoothedX = SavitzkyGolay.smooth(
            cleaned.map { it.xPx }.toDoubleArray(),
            window = SMOOTH_WINDOW,
            polyOrder = SMOOTH_ORDER,
        )
        val smoothedY = SavitzkyGolay.smooth(
            cleaned.map { it.yPx }.toDoubleArray(),
            window = SMOOTH_WINDOW,
            polyOrder = SMOOTH_ORDER,
        )
        return cleaned.mapIndexed { index, observation ->
            observation.copy(xPx = smoothedX[index], yPx = smoothedY[index])
        }
    }

    private fun innovationGate(
        observations: List<PlateObservation>,
        referenceAxisPx: Double,
    ): List<PlateObservation> {
        val accepted = ArrayList<PlateObservation>(observations.size)
        var velocityX = 0.0
        var velocityY = 0.0
        for (observation in observations) {
            val previous = accepted.lastOrNull()
            if (previous == null) {
                accepted += observation
                continue
            }
            val dt = (observation.tSec - previous.tSec).coerceAtLeast(1e-6)
            val predictedX = previous.xPx + velocityX * dt
            val predictedY = previous.yPx + velocityY * dt
            val maxJump = max(
                MAX_INNOVATION_DIAMETERS,
                MAX_SPEED_DIAMETERS_PER_SEC * dt,
            ) * referenceAxisPx
            val dx = observation.xPx - predictedX
            val dy = observation.yPx - predictedY
            if (dx * dx + dy * dy > maxJump * maxJump) continue
            velocityX = (observation.xPx - previous.xPx) / dt
            velocityY = (observation.yPx - previous.yPx) / dt
            accepted += observation
        }
        return accepted
    }

    /** Replaces single-frame spikes with the local median (keeps the timeline intact). */
    private fun hampel(observations: List<PlateObservation>): List<PlateObservation> {
        if (observations.size < HAMPEL_WINDOW) return observations
        val half = HAMPEL_WINDOW / 2
        return observations.mapIndexed { index, observation ->
            val from = (index - half).coerceAtLeast(0)
            val to = (index + half).coerceAtMost(observations.lastIndex)
            val windowX = observations.subList(from, to + 1).map { it.xPx }
            val windowY = observations.subList(from, to + 1).map { it.yPx }
            observation.copy(
                xPx = if (isOutlier(observation.xPx, windowX)) median(windowX) else observation.xPx,
                yPx = if (isOutlier(observation.yPx, windowY)) median(windowY) else observation.yPx,
            )
        }
    }

    private fun isOutlier(value: Double, window: List<Double>): Boolean {
        val center = median(window)
        val mad = median(window.map { abs(it - center) })
        val threshold = max(HAMPEL_FLOOR_PX, HAMPEL_SIGMAS * 1.4826 * mad)
        return abs(value - center) > threshold
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return Double.NaN
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }
}
