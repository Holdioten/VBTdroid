package com.vbtdroid.app.ui.report

import com.vbtdroid.app.R
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.phases.PhaseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Regression: stage labels are lift-specific and keep the shared lockout label. */
class PhaseLabelsTest {
    @Test
    fun squatLabels() {
        assertEquals(R.string.phase_squat_eccentric, phaseLabelRes(Lift.SQUAT, PhaseType.ECCENTRIC))
        assertEquals(R.string.phase_squat_pause, phaseLabelRes(Lift.SQUAT, PhaseType.PAUSE))
        assertEquals(R.string.phase_squat_concentric, phaseLabelRes(Lift.SQUAT, PhaseType.CONCENTRIC))
    }

    @Test
    fun benchLabels() {
        assertEquals(R.string.phase_bench_eccentric, phaseLabelRes(Lift.BENCH, PhaseType.ECCENTRIC))
        assertEquals(R.string.phase_bench_pause, phaseLabelRes(Lift.BENCH, PhaseType.PAUSE))
        assertEquals(R.string.phase_bench_concentric, phaseLabelRes(Lift.BENCH, PhaseType.CONCENTRIC))
    }

    @Test
    fun deadliftLabels() {
        assertEquals(R.string.phase_deadlift_pause, phaseLabelRes(Lift.DEADLIFT, PhaseType.PAUSE))
        assertEquals(R.string.phase_deadlift_concentric, phaseLabelRes(Lift.DEADLIFT, PhaseType.CONCENTRIC))
    }

    @Test
    fun lockoutIsSharedAcrossLifts() {
        Lift.entries.forEach { lift ->
            assertEquals(R.string.phase_lockout, phaseLabelRes(lift, PhaseType.LOCKOUT))
        }
    }

    @Test
    fun squatAndBenchPauseDiffer() {
        assertNotEquals(phaseLabelRes(Lift.SQUAT, PhaseType.PAUSE), phaseLabelRes(Lift.BENCH, PhaseType.PAUSE))
    }
}
