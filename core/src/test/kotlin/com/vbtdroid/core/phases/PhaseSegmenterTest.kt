package com.vbtdroid.core.phases

import com.vbtdroid.core.metrics.MotionSample
import com.vbtdroid.core.model.Lift
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhaseSegmenterTest {
    private data class Move(val vy: Double, val steps: Int, val dy: Double = 0.0)

    private fun sequence(vararg moves: Move, stepSec: Double = 0.05): List<MotionSample> {
        var t = 0.0
        var y = 0.0
        return buildList {
            moves.forEach { move ->
                repeat(move.steps) {
                    add(MotionSample(t, 0.0, y, 0.0, move.vy, 0.0, 0.0))
                    t += stepSec
                    y += move.dy
                }
            }
        }
    }

    private fun eccentricRep(stepSec: Double = 0.05) = arrayOf(
        Move(-0.5, 6, -0.05),
        Move(0.0, 4),
        Move(0.5, 6, 0.05),
    )

    @Test
    fun `squat rep has eccentric pause concentric lockout`() {
        val samples = sequence(*eccentricRep(), Move(0.0, 6))

        val reps = PhaseSegmenter.segment(samples, Lift.SQUAT)

        assertEquals(1, reps.size)
        assertEquals(
            listOf(PhaseType.ECCENTRIC, PhaseType.PAUSE, PhaseType.CONCENTRIC, PhaseType.LOCKOUT),
            reps[0].phases.map { it.type },
        )
    }

    @Test
    fun `deadlift starts at concentric and treats descent as separator`() {
        val samples = sequence(
            Move(0.0, 4),
            Move(0.5, 8, 0.05),
            Move(0.0, 6),
        )

        val reps = PhaseSegmenter.segment(samples, Lift.DEADLIFT)

        assertEquals(1, reps.size)
        assertEquals(PhaseType.CONCENTRIC, reps[0].phases.first().type)
        assertEquals(PhaseType.LOCKOUT, reps[0].phases.last().type)
    }

    @Test
    fun `deadlift without lockout is still a valid rep`() {
        val samples = sequence(
            Move(0.0, 4),
            Move(0.5, 10, 0.05),
            Move(-0.5, 6, -0.05),
        )

        val result = PhaseSegmenter.segmentDetailed(samples, Lift.DEADLIFT)

        assertEquals(1, result.reps.size)
        assertEquals(listOf(PhaseType.CONCENTRIC), result.reps.single().phases.map { it.type })
        assertTrue(result.warnings.contains(PhaseSegmenter.NO_LOCKOUT))
    }

    @Test
    fun `short spikes are ignored`() {
        val samples = sequence(
            Move(0.0, 5),
            Move(0.5, 1, 0.02),
            Move(0.0, 10),
        )

        assertTrue(PhaseSegmenter.segment(samples, Lift.DEADLIFT).isEmpty())
    }

    @Test
    fun `slow oscillation around eccentric threshold does not split rep`() {
        val samples = sequence(
            Move(-0.5, 10, -0.03),
            Move(-0.04, 4),
            Move(-0.02, 4),
            Move(-0.04, 4),
            Move(-0.02, 4),
            Move(0.0, 2),
            Move(0.5, 8, 0.05),
            Move(0.0, 6),
        )

        val reps = PhaseSegmenter.segment(samples, Lift.SQUAT)

        assertEquals(1, reps.size)
        assertEquals(
            listOf(PhaseType.ECCENTRIC, PhaseType.PAUSE, PhaseType.CONCENTRIC, PhaseType.LOCKOUT),
            reps.single().phases.map { it.type },
        )
    }

    @Test
    fun `single run shorter than minimum duration is ignored`() {
        val samples = sequence(Move(0.5, 2, 0.02))

        assertTrue(PhaseSegmenter.segment(samples, Lift.DEADLIFT).isEmpty())
    }

    @Test
    fun `two complete reps are both returned`() {
        val samples = sequence(
            *eccentricRep(),
            Move(0.0, 6),
            *eccentricRep(),
            Move(0.0, 6),
        )

        val reps = PhaseSegmenter.segment(samples, Lift.SQUAT)

        assertEquals(2, reps.size)
        assertTrue(reps.all { it.phases.last().type == PhaseType.LOCKOUT })
    }

    @Test
    fun `trailing rep without lockout is still valid and only warned`() {
        val samples = sequence(*eccentricRep())

        val result = PhaseSegmenter.segmentDetailed(samples, Lift.SQUAT)

        assertEquals(1, result.reps.size)
        assertEquals(
            listOf(PhaseType.ECCENTRIC, PhaseType.PAUSE, PhaseType.CONCENTRIC),
            result.reps.single().phases.map { it.type },
        )
        assertTrue(result.warnings.contains(PhaseSegmenter.NO_LOCKOUT))
        assertFalse(result.warnings.contains(PhaseSegmenter.INCOMPLETE_REP))
    }

    @Test
    fun `continuous three reps without top pauses are all counted`() {
        val samples = sequence(
            Move(-0.5, 6, -0.05),
            Move(0.5, 6, 0.05),
            Move(-0.5, 6, -0.05),
            Move(0.5, 6, 0.05),
            Move(-0.5, 6, -0.05),
            Move(0.5, 6, 0.05),
        )

        val reps = PhaseSegmenter.segment(samples, Lift.SQUAT)

        assertEquals(3, reps.size)
        assertTrue(reps.all { rep -> rep.phases.none { it.type == PhaseType.LOCKOUT } })
    }

    @Test
    fun `noisy small bumps do not create extra reps`() {
        val samples = sequence(
            Move(-0.5, 6, -0.05),
            Move(0.5, 4, 0.05),
            Move(0.0, 2),
            Move(0.5, 4, 0.05),
            Move(-0.04, 2),
            Move(0.5, 4, 0.05),
        )

        val reps = PhaseSegmenter.segment(samples, Lift.SQUAT)

        assertEquals(1, reps.size)
        assertTrue(reps.single().phases.none { it.type == PhaseType.LOCKOUT })
    }

    @Test
    fun `too short rep is rejected as incomplete`() {
        val samples = sequence(
            Move(-0.5, 4, -0.05),
            Move(0.5, 2, 0.05),
        )

        val result = PhaseSegmenter.segmentDetailed(samples, Lift.SQUAT)

        assertTrue(result.reps.isEmpty())
        assertTrue(result.warnings.contains(PhaseSegmenter.INCOMPLETE_REP))
    }

    @Test
    fun `too little vertical travel is rejected`() {
        val samples = sequence(
            Move(-0.5, 6, -0.015),
            Move(0.5, 6, 0.015),
        )

        val result = PhaseSegmenter.segmentDetailed(samples, Lift.SQUAT)

        assertTrue(result.reps.isEmpty())
        assertTrue(result.warnings.contains(PhaseSegmenter.INCOMPLETE_REP))
    }

    @Test
    fun `long pause does not split a rep`() {
        val samples = sequence(
            Move(-0.5, 6, -0.05),
            Move(0.0, 10),
            Move(0.5, 6, 0.05),
        )

        val reps = PhaseSegmenter.segment(samples, Lift.SQUAT)

        assertEquals(1, reps.size)
        assertEquals(
            listOf(PhaseType.ECCENTRIC, PhaseType.PAUSE, PhaseType.CONCENTRIC),
            reps.single().phases.map { it.type },
        )
    }

    @Test
    fun `short pause between same direction runs is merged`() {
        val samples = sequence(
            Move(-0.5, 6, -0.05),
            Move(0.0, 2),
            Move(-0.5, 6, -0.05),
            Move(0.5, 6, 0.05),
        )

        val reps = PhaseSegmenter.segment(samples, Lift.SQUAT)

        assertEquals(1, reps.size)
        assertEquals(
            listOf(PhaseType.ECCENTRIC, PhaseType.CONCENTRIC),
            reps.single().phases.map { it.type },
        )
    }

    @Test
    fun `zero point ten second lockout survives generic short run merge`() {
        val samples = sequence(*eccentricRep(), Move(0.0, 3))

        val rep = PhaseSegmenter.segment(samples, Lift.SQUAT).single()

        assertEquals(PhaseType.LOCKOUT, rep.phases.last().type)
        assertEquals(0.10, rep.phases.last().endSec - rep.phases.last().startSec, 1e-9)
    }

    @Test
    fun `zero point twelve second lockout remains distinct and capped`() {
        val samples = sequence(
            Move(-0.5, 6, -0.05),
            Move(0.5, 6, 0.05),
            Move(0.0, 3),
            stepSec = 0.06,
        )

        val rep = PhaseSegmenter.segment(samples, Lift.SQUAT).single()

        assertEquals(PhaseType.LOCKOUT, rep.phases.last().type)
        assertEquals(0.10, rep.phases.last().endSec - rep.phases.last().startSec, 1e-9)
    }

    @Test
    fun `phase and rep durations are always positive`() {
        val samples = sequence(
            *eccentricRep(),
            Move(0.0, 6),
            *eccentricRep(),
            Move(0.0, 6),
        )

        val reps = PhaseSegmenter.segment(samples, Lift.SQUAT)

        assertTrue(reps.isNotEmpty())
        reps.forEach { rep ->
            assertTrue(rep.endSec > rep.startSec)
            rep.phases.forEach { phase -> assertTrue(phase.endSec > phase.startSec) }
            rep.phases.zipWithNext().forEach { (left, right) ->
                assertTrue(right.startSec >= left.endSec)
            }
        }
    }
}
