package com.vbtdroid.app.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateRoiTest {
    @Test
    fun `largest detection wins`() {
        val a = PlateDetection(10f, 10f, 80f, 80f, 0.9f)
        val b = PlateDetection(20f, 20f, 120f, 120f, 0.8f)

        assertEquals(b, PlateRoi.selectLargest(listOf(a, b)))
    }

    @Test
    fun `inner hub is rejected when diameter deviates`() {
        assertFalse(PlateRoi.isValidMajorAxis(90f, expectedPx = 180f))
        assertTrue(PlateRoi.isValidMajorAxis(180f, expectedPx = 180f))
    }

    @Test
    fun `circle selection rejects inner hub and picks nearest predicted center`() {
        val plate = CircleCandidate(centerX = 314f, centerY = 640f, radiusPx = 147f)
        val hub = CircleCandidate(centerX = 320f, centerY = 640f, radiusPx = 60f)
        val neighbour = CircleCandidate(centerX = 500f, centerY = 640f, radiusPx = 147f)

        val selected = PlateRoi.selectCircle(
            candidates = listOf(hub, neighbour, plate),
            expectedRadiusPx = 147f,
            predictedX = 310f,
            predictedY = 645f,
            maxDistancePx = 200f,
        )

        assertEquals(plate, selected)
    }

    @Test
    fun `circle selection rejects radius deviation beyond fifteen percent`() {
        // 170 px is 15.6% larger than 147 px: a different plate, not the tracked one.
        val oversized = CircleCandidate(centerX = 314f, centerY = 640f, radiusPx = 170f)

        assertTrue(
            PlateRoi.selectCircle(
                candidates = listOf(oversized),
                expectedRadiusPx = 147f,
                predictedX = 314f,
                predictedY = 640f,
                maxDistancePx = 200f,
            ) == null,
        )
    }

    @Test
    fun `circle selection rejects candidates outside the local search window`() {
        val far = CircleCandidate(centerX = 600f, centerY = 640f, radiusPx = 147f)

        assertTrue(
            PlateRoi.selectCircle(
                candidates = listOf(far),
                expectedRadiusPx = 147f,
                predictedX = 314f,
                predictedY = 640f,
                maxDistancePx = 100f,
            ) == null,
        )
    }

    @Test
    fun `circle selection ignores invalid inputs`() {
        val circle = CircleCandidate(314f, 640f, 147f)

        assertTrue(
            PlateRoi.selectCircle(listOf(circle), 0f, 314f, 640f, 200f) == null,
        )
        assertTrue(
            PlateRoi.selectCircle(listOf(CircleCandidate(Float.NaN, 640f, 147f)), 147f, 314f, 640f, 200f) == null,
        )
        assertTrue(
            PlateRoi.selectCircle(listOf(CircleCandidate(314f, 640f, Float.NaN)), 147f, 314f, 640f, 200f) == null,
        )
    }

    @Test
    fun `accepted observations never move the initialization diameter reference`() {
        val reference = PlateAxisReference(180f)

        // Radius/axis gate is 15%: 205 px (13.9%) passes, 210 px (16.7%) is another object.
        assertTrue(reference.accepts(205f))
        assertFalse(reference.accepts(210f))
        assertEquals(180f, reference.majorAxisPx)
    }

    @Test
    fun `local search radius covers the whole plate diameter plus margin`() {
        val diameter = 260f

        val radius = PlateRoi.localSearchRadiusPx(diameter)

        assertEquals(diameter * 1.35f, radius, 1e-3f)
        // The old 0.9x radius was smaller than the plate itself and only saw an arc.
        assertTrue(radius > diameter)
        assertTrue(diameter * 0.9f < diameter)
    }

    @Test
    fun `local search radius rejects an invalid reference`() {
        assertEquals(0f, PlateRoi.localSearchRadiusPx(0f), 0f)
        assertEquals(0f, PlateRoi.localSearchRadiusPx(-10f), 0f)
        assertEquals(0f, PlateRoi.localSearchRadiusPx(Float.NaN), 0f)
    }

    @Test
    fun `innovation gate scales with frame gap but never below half a plate`() {
        val diameter = 260f

        // 30 fps: the 8 diameters/s term is smaller than the half-diameter floor.
        assertEquals(130f, PlateRoi.maxInnovationPx(diameter, 1.0 / 30.0), 1e-3f)
        // 0.5 s gap: a fast bar may legitimately move 4 diameters (1.04 m at 260 px/plate).
        assertEquals(1040f, PlateRoi.maxInnovationPx(diameter, 0.5), 1e-3f)
        assertEquals(0f, PlateRoi.maxInnovationPx(0f, 1.0 / 30.0), 0f)
    }
}
