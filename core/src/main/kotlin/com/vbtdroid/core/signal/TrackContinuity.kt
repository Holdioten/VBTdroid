package com.vbtdroid.core.signal

import com.vbtdroid.core.model.PlateObservation
import kotlin.math.abs

/** Why a plate track stopped being usable. */
enum class TrackBreakReason {
    /** Apparent plate size changed: the camera moved closer/farther or zoomed. */
    SCALE_CHANGE,

    /** The track simply stops well before the end of the clip. */
    TRACK_ENDED,
}

/**
 * First moment after which the plate track can no longer be used for metrics.
 *
 * @property fromSec last timestamp with a trustworthy observation.
 */
data class TrackBreak(
    val fromSec: Double,
    val reason: TrackBreakReason,
)

/**
 * Finds where a plate track stops being trustworthy.
 *
 * The calibration is fixed from the first frames (plate diameter -> px/m), so a scale change
 * invalidates every later position: 15% apparent diameter change is already a 15% metric error.
 * Positions after such a change are not merged into the track; earlier reps stay valid.
 *
 * A track that ends long before the clip does is also reported, because the remaining reps were
 * never observed (typically the plate left the frame or tracking never recovered).
 */
object TrackContinuity {
    /** Relative plate-diameter change that invalidates the fixed calibration. */
    const val SCALE_TOLERANCE = 0.15

    /** How many early observations define the reference diameter. */
    const val REFERENCE_SAMPLES = 10

    /** A track ending this long before the clip end is reported as lost. */
    const val END_TOLERANCE_SEC = 2.0

    /** Robust reference diameter: median of the first few accepted observations. */
    fun referenceAxisPx(observations: List<PlateObservation>): Double {
        val radii = observations
            .asSequence()
            .filter { it.majorAxisPx.isFinite() && it.majorAxisPx > 0.0 }
            .map { it.majorAxisPx }
            .take(REFERENCE_SAMPLES)
            .sorted()
            .toList()
        if (radii.isEmpty()) return Double.NaN
        val middle = radii.size / 2
        return if (radii.size % 2 == 1) {
            radii[middle]
        } else {
            (radii[middle - 1] + radii[middle]) / 2.0
        }
    }

    /**
     * @param durationSec clip duration; pass null when unknown to skip the ended-early check.
     */
    fun detect(observations: List<PlateObservation>, durationSec: Double? = null): TrackBreak? {
        val ordered = observations
            .filter { it.tSec.isFinite() && it.majorAxisPx.isFinite() && it.majorAxisPx > 0.0 }
            .sortedBy { it.tSec }
        if (ordered.size < 2) return null

        val reference = referenceAxisPx(ordered)
        if (reference.isFinite() && reference > 0.0) {
            // Two consecutive deviating observations: a single bad detection is handled by the
            // radius gate and must not be reported as a camera move.
            val deviationIndex = ordered.indices.firstOrNull { index ->
                index + 1 < ordered.size &&
                    deviates(ordered[index], reference) &&
                    deviates(ordered[index + 1], reference)
            }
            if (deviationIndex != null) {
                val lastGood = ordered.take(deviationIndex).lastOrNull() ?: ordered.first()
                return TrackBreak(fromSec = lastGood.tSec, reason = TrackBreakReason.SCALE_CHANGE)
            }
        }

        if (durationSec != null && durationSec.isFinite() && durationSec > 0.0) {
            val last = ordered.last()
            if (durationSec - last.tSec > END_TOLERANCE_SEC) {
                return TrackBreak(fromSec = last.tSec, reason = TrackBreakReason.TRACK_ENDED)
            }
        }
        return null
    }

    private fun deviates(observation: PlateObservation, referencePx: Double): Boolean =
        abs(observation.majorAxisPx - referencePx) / referencePx > SCALE_TOLERANCE
}
