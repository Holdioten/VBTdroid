package com.vbtdroid.app.vision

import com.vbtdroid.core.metrics.Point2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PosePersonSelectorTest {
    @Test
    fun `candidate nearest the bar wins over a larger background person`() {
        val candidates = listOf(
            candidate(centerX = 700.0, centerY = 220.0, size = 180.0),
            candidate(centerX = 300.0, centerY = 300.0, size = 110.0),
        )

        assertEquals(1, PosePersonSelector.select(candidates, Point2(310.0, 305.0)))
    }

    @Test
    fun `without a bar hint the largest skeleton is chosen`() {
        val candidates = listOf(
            candidate(centerX = 200.0, centerY = 200.0, size = 80.0),
            candidate(centerX = 600.0, centerY = 200.0, size = 180.0),
            candidate(centerX = 900.0, centerY = 200.0, size = 120.0),
        )

        assertEquals(1, PosePersonSelector.select(candidates, barCenter = null))
    }

    @Test
    fun `near-tied distances prefer the larger skeleton`() {
        // 100 px vs 108 px from the bar: inside the tie band, so size decides.
        val candidates = listOf(
            candidate(centerX = 100.0, centerY = 0.0, size = 70.0),
            candidate(centerX = 108.0, centerY = 0.0, size = 160.0),
        )

        assertEquals(1, PosePersonSelector.select(candidates, Point2(0.0, 0.0)))
    }

    @Test
    fun `candidates implausibly far from the bar are rejected`() {
        val candidates = listOf(candidate(centerX = 900.0, centerY = 0.0, size = 200.0))

        assertNull(PosePersonSelector.select(candidates, Point2(0.0, 0.0), maxDistancePx = 300.0))
    }

    @Test
    fun `far candidates are skipped when a plausible one exists`() {
        val candidates = listOf(
            candidate(centerX = 900.0, centerY = 0.0, size = 220.0),
            candidate(centerX = 120.0, centerY = 20.0, size = 90.0),
        )

        assertEquals(1, PosePersonSelector.select(candidates, Point2(0.0, 0.0), maxDistancePx = 300.0))
    }

    @Test
    fun `missing shoulders fall back to the hip midpoint`() {
        val candidates = listOf(
            PoseCandidate(landmarks = mapOf(23 to Point2(700.0, 300.0), 24 to Point2(720.0, 300.0))),
            PoseCandidate(landmarks = mapOf(23 to Point2(300.0, 300.0), 24 to Point2(320.0, 300.0))),
        )

        assertEquals(1, PosePersonSelector.select(candidates, Point2(315.0, 300.0)))
    }

    @Test
    fun `empty candidates yield null`() {
        assertNull(PosePersonSelector.select(emptyList(), Point2(0.0, 0.0)))
    }

    /** Builds a symmetric skeleton whose torso midpoint is exactly (centerX, centerY). */
    private fun candidate(centerX: Double, centerY: Double, size: Double): PoseCandidate {
        val half = size / 2.0
        return PoseCandidate(
            landmarks = mapOf(
                11 to Point2(centerX - half * 0.4, centerY - half * 0.2),
                12 to Point2(centerX + half * 0.4, centerY - half * 0.2),
                23 to Point2(centerX - half * 0.3, centerY + half * 0.2),
                24 to Point2(centerX + half * 0.3, centerY + half * 0.2),
                27 to Point2(centerX - half * 0.2, centerY + half * 0.5),
                28 to Point2(centerX + half * 0.2, centerY + half * 0.5),
                31 to Point2(centerX - half * 0.2, centerY + half * 0.5),
                32 to Point2(centerX + half * 0.2, centerY + half * 0.5),
            ),
        )
    }
}
