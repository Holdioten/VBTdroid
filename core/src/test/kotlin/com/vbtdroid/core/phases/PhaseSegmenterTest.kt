package com.vbtdroid.core.phases

import com.vbtdroid.core.metrics.MotionSample
import com.vbtdroid.core.model.Lift
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhaseSegmenterTest {
    private fun sample(t: Double, vy: Double) =
        MotionSample(t, 0.0, 0.0, 0.0, vy, 0.0, 0.0)

    @Test
    fun `squat rep has eccentric pause concentric lockout`() {
        val samples = buildList {
            for (i in 0..30) add(sample(i * 0.05, -0.5))
            for (i in 31..35) add(sample(i * 0.05, 0.0))
            for (i in 36..66) add(sample(i * 0.05, 0.5))
            for (i in 67..72) add(sample(i * 0.05, 0.0))
        }

        val reps = PhaseSegmenter.segment(samples, Lift.SQUAT)

        assertEquals(1, reps.size)
        assertEquals(
            listOf(PhaseType.ECCENTRIC, PhaseType.PAUSE, PhaseType.CONCENTRIC, PhaseType.LOCKOUT),
            reps[0].phases.map { it.type },
        )
    }

    @Test
    fun `deadlift starts at concentric`() {
        val samples = buildList {
            for (i in 0..10) add(sample(i * 0.05, 0.0))
            for (i in 11..40) add(sample(i * 0.05, 0.5))
            for (i in 41..46) add(sample(i * 0.05, 0.0))
        }

        val reps = PhaseSegmenter.segment(samples, Lift.DEADLIFT)

        assertEquals(1, reps.size)
        assertEquals(PhaseType.CONCENTRIC, reps[0].phases.first().type)
    }

    @Test
    fun `short spikes are ignored`() {
        val samples = buildList {
            for (i in 0..10) add(sample(i * 0.05, 0.0))
            add(sample(0.55, 0.5))
            for (i in 12..20) add(sample(i * 0.05, 0.0))
        }

        assertTrue(PhaseSegmenter.segment(samples, Lift.DEADLIFT).isEmpty())
    }
}
