package com.vbtdroid.core.phases

import com.vbtdroid.core.metrics.MotionSample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RepTimelineTest {
    private val rep1 = RepSlice(
        index = 1, startSec = 1.0, endSec = 2.0,
        phases = listOf(
            Phase(PhaseType.ECCENTRIC, 1.0, 1.4),
            Phase(PhaseType.CONCENTRIC, 1.4, 1.9),
            Phase(PhaseType.LOCKOUT, 1.9, 2.0),
        ),
    )
    private val rep2 = RepSlice(
        index = 2, startSec = 2.0, endSec = 3.0,
        phases = listOf(
            Phase(PhaseType.ECCENTRIC, 2.0, 2.5),
            Phase(PhaseType.CONCENTRIC, 2.5, 3.0),
        ),
    )
    private val reps = listOf(rep1, rep2)

    private fun sample(t: Double) = MotionSample(t, 0.0, t, 0.0, 0.0, null, null)

    @Test
    fun noRepBeforeFirstOrAfterLast() {
        assertNull(RepTimeline.currentRepAt(reps, 0.5))
        assertNull(RepTimeline.currentRepAt(reps, 3.5))
        assertNull(RepTimeline.currentRepAt(emptyList(), 1.5))
        assertNull(RepTimeline.currentRepAt(reps, Double.NaN))
    }

    @Test
    fun startInclusiveEndExclusiveAtBoundary() {
        assertEquals(1, RepTimeline.currentRepAt(reps, 1.0)?.index)
        assertEquals(1, RepTimeline.currentRepAt(reps, 1.999)?.index)
        // Shared boundary belongs to the next rep: the card switches as soon as the next rep starts.
        assertEquals(2, RepTimeline.currentRepAt(reps, 2.0)?.index)
    }

    @Test
    fun lastRepIncludesItsEndInstant() {
        assertEquals(2, RepTimeline.currentRepAt(reps, 3.0)?.index)
    }

    @Test
    fun gapBetweenRepsIsNoRep() {
        val gapped = listOf(rep1, rep2.copy(startSec = 2.5))
        assertNull(RepTimeline.currentRepAt(gapped, 2.2))
    }

    @Test
    fun trailRepPersistsThroughGapUntilNextRepStarts() {
        val gapped = listOf(rep1, rep2.copy(startSec = 2.5))

        // Gap [2.0, 2.5): still rep 1, so its full trail stays visible.
        assertEquals(1, RepTimeline.trailRepAt(gapped, 2.0)?.index)
        assertEquals(1, RepTimeline.trailRepAt(gapped, 2.2)?.index)
        assertEquals(1, RepTimeline.trailRepAt(gapped, 2.499)?.index)
        // Next rep start clears it and draws the new rep.
        assertEquals(2, RepTimeline.trailRepAt(gapped, 2.5)?.index)

        val samples = (0..60).map { sample(1.0 + it * 0.05) }
        val betweenReps = RepTimeline.trailRepAt(gapped, 2.2)!!
        val trail = RepTimeline.trailRuns(samples, betweenReps, upToSec = 2.2)
        assertTrue(trail.flatMap { it.samples }.isNotEmpty())
        assertTrue(trail.flatMap { it.samples }.all { it.tSec <= rep1.endSec })
    }

    @Test
    fun trailRepBeforeFirstIsNullAndAfterLastStaysLast() {
        assertNull(RepTimeline.trailRepAt(reps, 0.5))
        assertEquals(2, RepTimeline.trailRepAt(reps, 3.5)?.index)
        assertNull(RepTimeline.trailRepAt(reps, Double.NaN))
    }

    @Test
    fun phaseAtFollowsRepBoundaries() {
        assertEquals(PhaseType.ECCENTRIC, RepTimeline.phaseAt(rep1, 1.2)?.type)
        assertEquals(PhaseType.CONCENTRIC, RepTimeline.phaseAt(rep1, 1.4)?.type)
        assertEquals(PhaseType.LOCKOUT, RepTimeline.phaseAt(rep1, 2.0)?.type)
        assertNull(RepTimeline.phaseAt(rep1, 2.5))
    }

    @Test
    fun trailRunsSplitByPhaseAndStopAtRepEnd() {
        val samples = (0..40).map { sample(1.0 + it * 0.05) } // 1.00 .. 3.00
        val runs = RepTimeline.trailRuns(samples, rep1, upToSec = 2.5)
        // Only rep 1 samples, clipped at rep end (2.0): nothing from rep 2 leaks in.
        assertTrue(runs.all { run -> run.samples.all { it.tSec in 1.0..2.0 } })
        assertEquals(listOf(PhaseType.ECCENTRIC, PhaseType.CONCENTRIC, PhaseType.LOCKOUT),
            runs.map { it.type }.distinct())
    }

    @Test
    fun trailRunsAreProgressive() {
        val samples = (0..40).map { sample(1.0 + it * 0.05) }
        val early = RepTimeline.trailRuns(samples, rep1, upToSec = 1.2).flatMap { it.samples }
        val late = RepTimeline.trailRuns(samples, rep1, upToSec = 1.8).flatMap { it.samples }
        assertTrue(early.last().tSec <= 1.2 + 1e-9)
        assertTrue(late.size > early.size)
    }

    @Test
    fun trailForRepWithNoSamplesIsEmpty() {
        assertTrue(RepTimeline.trailRuns(listOf(sample(5.0)), rep1, 2.0).isEmpty())
        assertTrue(RepTimeline.trailRuns(emptyList(), rep1, 2.0).isEmpty())
    }

    @Test
    fun trailRunsBridgeRunsWithoutGaps() {
        val samples = (0..40).map { sample(1.0 + it * 0.05) }
        val runs = RepTimeline.trailRuns(samples, rep1, upToSec = 2.0)
        for (i in 1 until runs.size) {
            assertEquals(runs[i - 1].samples.last().tSec, runs[i].samples.first().tSec, 1e-9)
        }
    }
}
