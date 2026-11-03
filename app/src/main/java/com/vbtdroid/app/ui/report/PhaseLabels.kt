package com.vbtdroid.app.ui.report

import com.vbtdroid.app.R
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.phases.PhaseType

/**
 * Lift-specific stage labels for the UI. The underlying [PhaseType] and metrics are unchanged;
 * only the displayed name depends on the lift.
 */
internal fun phaseLabelRes(lift: Lift, type: PhaseType): Int = when (lift) {
    Lift.SQUAT -> when (type) {
        PhaseType.ECCENTRIC -> R.string.phase_squat_eccentric
        PhaseType.PAUSE -> R.string.phase_squat_pause
        PhaseType.CONCENTRIC -> R.string.phase_squat_concentric
        PhaseType.LOCKOUT -> R.string.phase_lockout
    }
    Lift.BENCH -> when (type) {
        PhaseType.ECCENTRIC -> R.string.phase_bench_eccentric
        PhaseType.PAUSE -> R.string.phase_bench_pause
        PhaseType.CONCENTRIC -> R.string.phase_bench_concentric
        PhaseType.LOCKOUT -> R.string.phase_lockout
    }
    Lift.DEADLIFT -> when (type) {
        PhaseType.ECCENTRIC -> R.string.phase_deadlift_eccentric
        PhaseType.PAUSE -> R.string.phase_deadlift_pause
        PhaseType.CONCENTRIC -> R.string.phase_deadlift_concentric
        PhaseType.LOCKOUT -> R.string.phase_lockout
    }
}
