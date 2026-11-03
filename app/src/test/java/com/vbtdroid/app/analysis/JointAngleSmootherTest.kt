package com.vbtdroid.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JointAngleSmootherTest {
    @Test
    fun `median three removes a single frame spike`() {
        val frames = listOf(
            angles(0.0, 90.0),
            angles(0.1, 92.0),
            angles(0.2, 160.0),
            angles(0.3, 94.0),
            angles(0.4, 96.0),
        )

        val smoothed = JointAngleSmoother.smooth(frames)

        assertEquals(listOf(90.0, 92.0, 94.0, 96.0, 96.0), smoothed.map { it.hipDeg })
        assertEquals(frames.map { it.tSec }, smoothed.map { it.tSec })
        assertEquals(frames.map { it.confidence }, smoothed.map { it.confidence })
    }

    @Test
    fun `null angles stay null and never inherit neighbour values`() {
        val frames = listOf(
            angles(0.0, 90.0),
            angles(0.1, null),
            angles(0.2, 94.0),
            angles(0.3, null),
            angles(0.4, 98.0),
        )

        val smoothed = JointAngleSmoother.smooth(frames)

        assertNull(smoothed[1].hipDeg)
        assertNull(smoothed[3].hipDeg)
        assertEquals(94.0, smoothed[2].hipDeg)
    }

    @Test
    fun `window five smooths a longer spike while keeping edges`() {
        val frames = listOf(
            angles(0.0, 90.0),
            angles(0.1, 90.0),
            angles(0.2, 91.0),
            angles(0.3, 150.0),
            angles(0.4, 92.0),
            angles(0.5, 93.0),
            angles(0.6, 94.0),
        )

        val smoothed = JointAngleSmoother.smooth(frames, window = 5)

        assertEquals(90.0, smoothed.first().hipDeg)
        assertEquals(94.0, smoothed.last().hipDeg)
        assertEquals(92.0, smoothed[3].hipDeg)
    }

    @Test
    fun `single frame list and window one are unchanged`() {
        val frames = listOf(angles(0.0, 90.0))

        assertEquals(frames, JointAngleSmoother.smooth(frames))
        assertEquals(frames, JointAngleSmoother.smooth(frames, window = 1))
        assertEquals(emptyList<FrameAngles>(), JointAngleSmoother.smooth(emptyList()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `even window is rejected`() {
        JointAngleSmoother.smooth(listOf(angles(0.0, 90.0)), window = 4)
    }

    private fun angles(tSec: Double, hip: Double?) = FrameAngles(
        tSec = tSec,
        hipDeg = hip,
        kneeDeg = hip,
        ankleDeg = hip,
        trunkDeg = hip,
        shoulderDeg = hip,
        elbowDeg = hip,
        wristDeg = hip,
        confidence = 0.9,
    )
}
