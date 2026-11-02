package com.vbtdroid.core.calibration

import com.vbtdroid.core.model.PlateObservation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CalibrationTest {
    @Test
    fun `450mm plate at 225px gives 500 px per metre`() {
        val c = Calibration.fromPlate(0.45, 225.0, 90.0)
        assertEquals(500.0, c.pxPerM, 1e-9)
    }

    @Test
    fun `45 degree camera expands horizontal displacement`() {
        val c = Calibration.fromPlate(0.45, 225.0, 45.0)
        val s = c.toMeters(PlateObservation(0.0, 100.0, -50.0, 225.0, 1.0))
        assertEquals(0.282842712, s.xM, 1e-6)
        assertEquals(0.1, s.yM, 1e-9)
    }

    @Test
    fun `rejects invalid plate geometry`() {
        assertThrows(IllegalArgumentException::class.java) {
            Calibration.fromPlate(0.0, 225.0, 45.0)
        }
    }
}
