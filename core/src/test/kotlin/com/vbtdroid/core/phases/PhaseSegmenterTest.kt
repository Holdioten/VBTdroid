package com.vbtdroid.core.phases

import com.vbtdroid.core.metrics.MotionSample
import com.vbtdroid.core.model.Lift
import kotlin.math.roundToInt
import kotlin.math.sin
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
    fun `noisy signal with jitter and velocity bumps keeps the true rep count`() {
        val samples = sequence(
            Move(-0.5, 6, -0.05),
            Move(-0.06, 1, -0.01),
            Move(0.04, 1, 0.01),
            Move(-0.07, 1, -0.01),
            Move(0.03, 1, 0.01),
            Move(0.5, 6, 0.05),
            Move(-0.06, 1, -0.01),
            Move(0.0, 3),
            Move(-0.5, 6, -0.05),
            Move(0.07, 1, 0.01),
            Move(-0.04, 1, -0.01),
            Move(0.5, 6, 0.05),
            Move(0.0, 4),
        )

        val reps = PhaseSegmenter.segment(samples, Lift.SQUAT)

        assertEquals(2, reps.size)
        assertTrue(reps.all { it.endSec > it.startSec })
    }

    @Test
    fun `stationary jitter alone does not create a rep`() {
        val samples = sequence(
            Move(0.04, 2, 0.01),
            Move(-0.07, 1, -0.01),
            Move(0.06, 1, 0.01),
            Move(-0.04, 2, -0.005),
            Move(0.07, 1, 0.01),
            Move(-0.06, 1, -0.01),
            Move(0.0, 4),
        )

        val result = PhaseSegmenter.segmentDetailed(samples, Lift.SQUAT)

        assertTrue(result.reps.isEmpty())
    }

    @Test
    fun `monotonic velocity without a turnaround is rejected`() {
        val moves = (1..12).map { Move(0.06 * it, 1, 0.01) }
        val samples = sequence(*moves.toTypedArray())

        val result = PhaseSegmenter.segmentDetailed(samples, Lift.DEADLIFT)

        assertTrue(result.reps.isEmpty())
        assertTrue(result.warnings.contains(PhaseSegmenter.INCOMPLETE_REP))
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
    fun `travel below the app ROM floor is rejected`() {
        // 0.15 m: accepted by the old 0.10 m default, rejected by the 0.20 m app default.
        val samples = sequence(
            Move(-0.5, 6, -0.025),
            Move(0.5, 6, 0.025),
        )

        val result = PhaseSegmenter.segmentDetailed(samples, Lift.SQUAT)

        assertTrue(result.reps.isEmpty())
        assertTrue(result.warnings.contains(PhaseSegmenter.INCOMPLETE_REP))
    }

    private fun deadliftRep(dy: Double = 0.05, steps: Int = 10) = arrayOf(
        Move(0.5, steps, dy),
        Move(0.0, 4),
        Move(-0.5, steps, -dy),
    )

    /** Short pull with a deceleration tail: passes min duration but travels ~0.22 m. */
    private fun quarterPull() = arrayOf(
        Move(0.5, 8, 0.02625),
        Move(0.2, 2, 0.005),
        Move(0.0, 4),
        Move(-0.5, 8, -0.02625),
        Move(-0.2, 2, -0.005),
    )

    @Test
    fun `quarter pull is dropped while six true deadlift reps survive`() {
        val samples = sequence(
            *quarterPull(),
            *deadliftRep(),
            *deadliftRep(),
            *deadliftRep(),
            *deadliftRep(),
            *deadliftRep(),
            *deadliftRep(),
        )

        val result = PhaseSegmenter.segmentDetailed(samples, Lift.DEADLIFT)

        assertEquals(6, result.reps.size)
        assertTrue(result.warnings.contains(PhaseSegmenter.REP_ROM_OUTLIER))
        result.reps.forEach { rep ->
            val rom = samples.filter { it.tSec in rep.startSec..rep.endSec }
                .let { it.maxOf { sample -> sample.yM } - it.minOf { sample -> sample.yM } }
            assertEquals(0.5, rom, 1e-9)
        }
        assertEquals((1..6).toList(), result.reps.map { it.index })
    }

    @Test
    fun `median ROM filter waits for at least three candidates`() {
        val samples = sequence(
            *quarterPull(),
            *deadliftRep(),
        )

        val result = PhaseSegmenter.segmentDetailed(samples, Lift.DEADLIFT)

        assertEquals(2, result.reps.size)
        assertFalse(result.warnings.contains(PhaseSegmenter.REP_ROM_OUTLIER))
    }

    @Test
    fun `eight continuous reps are inferred from the signal alone`() {
        val moves = (1..8).flatMap {
            listOf(Move(-0.5, 6, -0.05), Move(0.5, 6, 0.05))
        }.toTypedArray()

        val result = PhaseSegmenter.segmentDetailed(sequence(*moves), Lift.SQUAT)

        // Count comes from the track; there is no target count anywhere in the segmenter.
        assertEquals(8, result.reps.size)
        assertFalse(result.warnings.contains(PhaseSegmenter.REP_ROM_OUTLIER))
        assertEquals((1..8).toList(), result.reps.map { it.index })
    }

    @Test
    fun `three reps with a middle partial pull keep the two full reps`() {
        val samples = sequence(
            *deadliftRep(),
            *quarterPull(),
            *deadliftRep(),
        )

        val result = PhaseSegmenter.segmentDetailed(samples, Lift.DEADLIFT)

        assertEquals(2, result.reps.size)
        assertTrue(result.warnings.contains(PhaseSegmenter.REP_ROM_OUTLIER))
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
    fun `six positive velocity runs plus a quarter pull infer six deadlift reps`() {
        val smallPull = arrayOf(
            Move(0.0, 4),
            Move(0.5, 6, 0.02),
            Move(0.0, 6),
        )
        val samples = sequence(
            *smallPull,
            *deadliftRep(),
            *deadliftRep(),
            *deadliftRep(),
            *deadliftRep(),
            *deadliftRep(),
            *deadliftRep(),
        )

        val result = PhaseSegmenter.segmentDetailed(samples, Lift.DEADLIFT)

        assertEquals(6, result.reps.size)
        assertEquals((1..6).toList(), result.reps.map { it.index })
        assertTrue(result.warnings.contains(PhaseSegmenter.INCOMPLETE_REP))
        result.reps.forEach { rep -> assertEquals(PhaseType.CONCENTRIC, rep.phases.first().type) }
    }

    @Test
    fun `velocity dip inside a deadlift rep does not split it`() {
        val samples = sequence(
            Move(0.0, 4),
            Move(0.5, 5, 0.05),
            Move(0.0, 2),
            Move(0.5, 5, 0.05),
            Move(0.0, 6),
        )

        val reps = PhaseSegmenter.segment(samples, Lift.DEADLIFT)

        assertEquals(1, reps.size)
        assertEquals(PhaseType.CONCENTRIC, reps.single().phases.first().type)
        assertEquals(0.8, reps.single().endSec - reps.single().startSec, 1e-9)
    }

    @Test
    fun `deadlift with no positive velocity runs infers no reps`() {
        val samples = sequence(
            Move(-0.5, 10, -0.05),
            Move(0.0, 6),
        )

        val result = PhaseSegmenter.segmentDetailed(samples, Lift.DEADLIFT)

        assertTrue(result.reps.isEmpty())
    }

    private data class CycleSegment(val durationSec: Double, val fromY: Double, val toY: Double)

    /**
     * Squat-like cycles in the app's upward-positive y convention: start at the top, descend,
     * pause at the bottom, stand up, pause at the top. `flatVelocity` simulates a degraded
     * velocity track so only the y-extrema fallback can count the reps.
     */
    private fun yCycleSamples(
        count: Int,
        stepSec: Double = 1.0 / 30.0,
        amplitudeM: Double = 0.55,
        noiseM: Double = 0.0,
        flatVelocity: Boolean = false,
    ): List<MotionSample> {
        val segments = buildList {
            add(CycleSegment(0.40, 0.0, 0.0))
            repeat(count) {
                add(CycleSegment(0.55, 0.0, -amplitudeM))
                add(CycleSegment(0.20, -amplitudeM, -amplitudeM))
                add(CycleSegment(0.55, -amplitudeM, 0.0))
                add(CycleSegment(0.25, 0.0, 0.0))
            }
        }
        val raw = mutableListOf<MotionSample>()
        var t = 0.0
        var sampleIndex = 0
        for (segment in segments) {
            val steps = maxOf(1, (segment.durationSec / stepSec).roundToInt())
            for (step in 0 until steps) {
                val fraction = step.toDouble() / steps
                val y = segment.fromY + (segment.toY - segment.fromY) * fraction +
                    noiseM * sin(sampleIndex * 2.399)
                raw += MotionSample(t, 0.0, y, 0.0, 0.0, 0.0, 0.0)
                t += stepSec
                sampleIndex++
            }
        }
        if (flatVelocity) return raw
        return raw.mapIndexed { index, sample ->
            val previous = raw[maxOf(0, index - 1)]
            val next = raw[minOf(raw.lastIndex, index + 1)]
            val dt = next.tSec - previous.tSec
            val vy = if (dt > 0.0) (next.yM - previous.yM) / dt else 0.0
            sample.copy(vyMps = vy)
        }
    }

    private fun ramp(from: Double, to: Double, steps: Int): List<Double> =
        (0 until steps).map { from + (to - from) * it / steps }

    @Test
    fun `six noisy cycles recover from y extrema when the velocity track is flat`() {
        val samples = yCycleSamples(count = 6, noiseM = 0.008, flatVelocity = true)

        val result = PhaseSegmenter.segmentDetailed(samples, Lift.SQUAT)

        assertEquals(6, result.reps.size)
        assertEquals((1..6).toList(), result.reps.map { it.index })
        result.reps.forEach { rep ->
            assertEquals(
                listOf(PhaseType.ECCENTRIC, PhaseType.CONCENTRIC),
                rep.phases.map { it.type },
            )
            val repSamples = samples.filter { it.tSec in rep.startSec..rep.endSec }
            assertEquals(
                0.55,
                repSamples.maxOf { it.yM } - repSamples.minOf { it.yM },
                0.03,
            )
        }
    }

    @Test
    fun `eight noisy cycles are still eight bench reps with a realistic velocity track`() {
        val samples = yCycleSamples(count = 8, noiseM = 0.006)

        val result = PhaseSegmenter.segmentDetailed(samples, Lift.BENCH)

        assertEquals(8, result.reps.size)
        assertEquals((1..8).toList(), result.reps.map { it.index })
        assertTrue(result.reps.all { rep -> rep.phases.any { it.type == PhaseType.CONCENTRIC } })
    }

    @Test
    fun `extrema fallback ignores incomplete leading and trailing edges`() {
        val y = buildList {
            addAll(ramp(-0.25, -0.55, 8)) // starts mid-descent: no top for this bottom
            addAll(List(4) { -0.55 })
            addAll(ramp(-0.55, 0.0, 12))
            addAll(List(3) { 0.0 })
            addAll(ramp(0.0, -0.55, 12)) // first complete cycle
            addAll(List(3) { -0.55 })
            addAll(ramp(-0.55, 0.0, 12))
            addAll(List(3) { 0.0 })
            addAll(ramp(0.0, -0.55, 12))
            addAll(List(3) { -0.55 })
            addAll(ramp(-0.55, -0.25, 8)) // ends mid-ascent: no top for the last bottom
        }
        val samples = y.mapIndexed { index, value ->
            MotionSample(index * 0.05, 0.0, value, 0.0, 0.0, 0.0, 0.0)
        }

        val result = PhaseSegmenter.segmentDetailed(samples, Lift.SQUAT)

        assertEquals(1, result.reps.size)
        assertTrue(result.reps.single().phases.any { it.type == PhaseType.ECCENTRIC })
        assertTrue(result.reps.single().phases.any { it.type == PhaseType.CONCENTRIC })
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
