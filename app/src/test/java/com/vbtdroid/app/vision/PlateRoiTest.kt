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
    fun `accepted observations never move the initialization diameter reference`() {
        val reference = PlateAxisReference(180f)

        assertTrue(reference.accepts(220f))
        assertFalse(reference.accepts(230f))
        assertEquals(180f, reference.majorAxisPx)
    }
}
