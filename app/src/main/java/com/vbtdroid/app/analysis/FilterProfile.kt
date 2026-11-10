package com.vbtdroid.app.analysis

import com.vbtdroid.core.model.Lift

/**
 * Signal-processing profiles shared by every analysis.
 *
 * Windows are expressed in **seconds**, never in samples: at 60 fps a 9-sample window is 0.15 s
 * while at 30 fps it is 0.30 s, which made the same lift look different depending on the phone
 * or the recording mode. The factor is the only per-lift difference; it compensates for the
 * larger high-frequency content of bench-press tracks (both arms stabilise the bar, and the near
 * plate has more pixels per metre).
 */
object FilterProfile {
    /** Mean/propulsive velocity window: suppresses detector jitter without flattening the peak. */
    const val MEAN_WINDOW_SEC = 0.30

    /** Peak velocity window: shorter so the true peak is not attenuated. */
    const val PEAK_WINDOW_SEC = 0.15

    /** Pixel-space anti-spike window applied to the raw track before differentiation. */
    const val TRACK_SMOOTH_WINDOW_SEC = 0.18

    fun meanWindowSec(lift: Lift): Double = MEAN_WINDOW_SEC * factor(lift)

    fun peakWindowSec(lift: Lift): Double = PEAK_WINDOW_SEC * factor(lift)

    fun trackSmoothWindowSec(lift: Lift): Double = TRACK_SMOOTH_WINDOW_SEC * factor(lift)

    private fun factor(lift: Lift): Double = when (lift) {
        Lift.BENCH -> 1.15
        Lift.SQUAT, Lift.DEADLIFT -> 1.0
    }
}
