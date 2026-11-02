package com.vbtdroid.core.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JointAnglesTest {
    @Test
    fun `right angle`() {
        val a = Point2(0.0, 0.0)
        val b = Point2(1.0, 0.0)
        val c = Point2(1.0, 1.0)

        assertEquals(90.0, JointAngles.angle(a, b, c), 1e-9)
    }

    @Test
    fun `45 degree image angle at 45 degree camera corrects to 45`() {
        assertEquals(45.0, JointAngles.correctAzimuth(35.264389, 45.0), 1e-6)
    }

    @Test
    fun `side view does not change angle`() {
        assertEquals(60.0, JointAngles.correctAzimuth(60.0, 90.0), 1e-9)
    }

    @Test
    fun `degenerate vector returns zero`() {
        val point = Point2(1.0, 2.0)

        assertEquals(0.0, JointAngles.angle(point, point, point), 0.0)
    }

    @Test
    fun `right image angle remains right after correction`() {
        assertEquals(90.0, JointAngles.correctAzimuth(90.0, 45.0), 1e-9)
    }

    @Test
    fun `angles above right angle use supplementary correction`() {
        assertEquals(135.0, JointAngles.correctAzimuth(144.735611, 45.0), 1e-6)
    }
}
