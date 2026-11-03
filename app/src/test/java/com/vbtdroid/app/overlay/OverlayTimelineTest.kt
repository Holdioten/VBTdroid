package com.vbtdroid.app.overlay

import com.vbtdroid.app.analysis.FrameAngles
import com.vbtdroid.app.vision.PoseFrame
import com.vbtdroid.core.metrics.MotionSample
import com.vbtdroid.core.metrics.Point2
import com.vbtdroid.core.model.PlateObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayTimelineTest {
    @Test
    fun `nearest observation picks closest sample and handles empty input`() {
        val observations = listOf(
            observation(0.0, 10.0),
            observation(1.0, 20.0),
            observation(2.0, 30.0),
        )

        assertEquals(20.0, OverlayTimeline.nearestObservation(observations, 0.9)?.xPx!!, 1e-9)
        assertEquals(30.0, OverlayTimeline.nearestObservation(observations, 5.0)?.xPx!!, 1e-9)
        assertEquals(10.0, OverlayTimeline.nearestObservation(observations, -5.0)?.xPx!!, 1e-9)
        assertNull(OverlayTimeline.nearestObservation(emptyList(), 1.0))
    }

    @Test
    fun `nearest pose frame and angle samples use timestamps`() {
        val poses = listOf(
            pose(0.0, 1.0),
            pose(0.5, 2.0),
            pose(1.0, 3.0),
        )
        val angles = listOf(
            angles(0.0, 90.0),
            angles(0.5, 100.0),
            angles(1.0, 110.0),
        )

        assertEquals(2.0, OverlayTimeline.nearestPoseFrame(poses, 0.6)?.landmarks?.get(0)?.y!!, 1e-9)
        assertEquals(100.0, OverlayTimeline.nearestFrameAngles(angles, 0.4)?.hipDeg!!, 1e-9)
        assertNull(OverlayTimeline.nearestPoseFrame(emptyList(), 0.2))
        assertNull(OverlayTimeline.nearestFrameAngles(emptyList(), 0.2))
    }

    @Test
    fun `smoothed motion samples drive nearest lookup and trail windows`() {
        val samples = listOf(
            motion(0.0, 0.0, 1.0),
            motion(0.5, 0.1, 1.1),
            motion(1.0, 0.2, 1.2),
            motion(1.5, 0.3, 1.3),
            motion(2.0, 0.4, 1.4),
        )

        assertEquals(1.2, OverlayTimeline.nearestMotionSample(samples, 0.9)?.yM!!, 1e-9)
        assertEquals(
            listOf(0.5, 1.0, 1.5),
            OverlayTimeline.trailSamples(samples, tSec = 1.5, windowSec = 1.0).map { it.tSec },
        )
        assertEquals(emptyList<MotionSample>(), OverlayTimeline.trailSamples(emptyList(), 1.0))
        assertNull(OverlayTimeline.nearestMotionSample(emptyList(), 1.0))
    }

    @Test
    fun `trail slices past window and preserves order`() {
        val observations = listOf(
            observation(0.0, 0.0),
            observation(0.5, 1.0),
            observation(1.0, 2.0),
            observation(1.5, 3.0),
            observation(2.0, 4.0),
        )

        val trail = OverlayTimeline.trail(observations, tSec = 1.5, windowSec = 1.0)

        assertEquals(listOf(0.5, 1.0, 1.5), trail.map { it.tSec })
        assertEquals(emptyList<PlateObservation>(), OverlayTimeline.trail(emptyList(), 1.0))
    }

    @Test
    fun `speed uses absolute vertical velocity of nearest sample`() {
        val samples = listOf(
            sample(0.0, -0.2),
            sample(0.5, 0.85),
            sample(1.0, 0.1),
        )

        assertEquals(0.85, OverlayTimeline.speedMps(samples, 0.45)!!, 1e-9)
        assertEquals(0.2, OverlayTimeline.speedMps(samples, -1.0)!!, 1e-9)
        assertNull(OverlayTimeline.speedMps(emptyList(), 0.0))
    }

    private fun observation(tSec: Double, x: Double) = PlateObservation(
        tSec = tSec,
        xPx = x,
        yPx = 100.0 - x,
        majorAxisPx = 45.0,
        confidence = 0.9,
    )

    private fun pose(tSec: Double, y: Double) = PoseFrame(
        tSec = tSec,
        landmarks = mapOf(0 to Point2(10.0, y)),
        visibility = mapOf(0 to 0.9f),
        frameWidth = 100,
        frameHeight = 100,
    )

    private fun angles(tSec: Double, hip: Double) = FrameAngles(
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

    private fun sample(tSec: Double, vy: Double) = MotionSample(
        tSec = tSec,
        xM = 0.0,
        yM = 0.0,
        vxMps = 0.0,
        vyMps = vy,
        axMps2 = 0.0,
        ayMps2 = 0.0,
    )

    private fun motion(tSec: Double, xM: Double, yM: Double) = MotionSample(
        tSec = tSec,
        xM = xM,
        yM = yM,
        vxMps = 0.0,
        vyMps = 0.0,
        axMps2 = null,
        ayMps2 = null,
    )
}
