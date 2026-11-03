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

    @Test
    fun `slow oscillation around eccentric threshold does not split rep`() {
        val samples = buildList {
            for (i in 0..10) add(sample(i * 0.05, -0.5))
            for (i in 11..14) add(sample(i * 0.05, -0.04))
            for (i in 15..18) add(sample(i * 0.05, -0.02))
            for (i in 19..22) add(sample(i * 0.05, -0.04))
            for (i in 23..26) add(sample(i * 0.05, -0.02))
            for (i in 27..31) add(sample(i * 0.05, 0.0))
            for (i in 32..62) add(sample(i * 0.05, 0.5))
            for (i in 63..69) add(sample(i * 0.05, 0.0))
        }

        val reps = PhaseSegmenter.segment(samples, Lift.SQUAT)

        assertEquals(1, reps.size)
        assertEquals(
            listOf(PhaseType.ECCENTRIC, PhaseType.PAUSE, PhaseType.CONCENTRIC, PhaseType.LOCKOUT),
            reps.single().phases.map { it.type },
        )
    }

    @Test
    fun `single run shorter than minimum duration is ignored`() {
        val samples = listOf(sample(0.0, 0.5), sample(0.05, 0.5))

        assertTrue(PhaseSegmenter.segment(samples, Lift.DEADLIFT).isEmpty())
    }

    @Test
    fun `two complete reps are both returned`() {
        val samples = buildList {
            var t = 0.0
            repeat(2) {
                repeat(4) { add(sample(t, -0.5)); t += 0.05 }
                repeat(3) { add(sample(t, 0.0)); t += 0.05 }
                repeat(4) { add(sample(t, 0.5)); t += 0.05 }
                repeat(3) { add(sample(t, 0.0)); t += 0.05 }
            }
        }

        val reps = PhaseSegmenter.segment(samples, Lift.SQUAT)

        assertEquals(2, reps.size)
        assertTrue(reps.all { it.phases.last().type == PhaseType.LOCKOUT })
    }

    @Test
    fun `trailing rep without lockout is incomplete and warned`() {
        val samples = buildList {
            for (i in 0..4) add(sample(i * 0.05, -0.5))
            for (i in 5..9) add(sample(i * 0.05, 0.5))
        }

        val result = PhaseSegmenter.segmentDetailed(samples, Lift.SQUAT)

        assertTrue(result.reps.isEmpty())
        assertEquals(listOf(PhaseSegmenter.INCOMPLETE_REP_NO_LOCKOUT), result.warnings)
    }

    @Test
    fun `zero point ten second lockout survives generic short run merge`() {
        val samples = lockoutSamples(lockoutSteps = 3)

        val rep = PhaseSegmenter.segment(samples, Lift.SQUAT).single()

        assertEquals(PhaseType.LOCKOUT, rep.phases.last().type)
        assertEquals(0.10, rep.phases.last().endSec - rep.phases.last().startSec, 1e-9)
    }

    @Test
    fun `zero point twelve second lockout remains distinct`() {
        val samples = lockoutSamples(lockoutSteps = 3, stepSec = 0.06)

        val rep = PhaseSegmenter.segment(samples, Lift.SQUAT).single()

        assertEquals(PhaseType.LOCKOUT, rep.phases.last().type)
        assertEquals(0.12, rep.phases.last().endSec - rep.phases.last().startSec, 1e-9)
    }

    private fun lockoutSamples(lockoutSteps: Int, stepSec: Double = 0.05) = buildList {
        var t = 0.0
        repeat(4) { add(sample(t, -0.5)); t += stepSec }
        repeat(4) { add(sample(t, 0.5)); t += stepSec }
        repeat(lockoutSteps) { add(sample(t, 0.0)); t += stepSec }
    }
}
