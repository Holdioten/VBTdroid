package com.vbtdroid.app.ui.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Regression: cursor x is linear in time, clamped to nothing outside the rep. */
class ChartGeometryTest {
    @Test
    fun startAndEndMapToPaddingEdges() {
        assertEquals(12f, timeToX(1.0, 1.0, 3.0, 200f)!!, 1e-4f)
        assertEquals(188f, timeToX(3.0, 1.0, 3.0, 200f)!!, 1e-4f)
    }

    @Test
    fun midpointIsCentred() {
        assertEquals(100f, timeToX(2.0, 1.0, 3.0, 200f)!!, 1e-4f)
    }

    @Test
    fun linearInTime() {
        val a = timeToX(1.5, 1.0, 3.0, 300f)!!
        val b = timeToX(2.5, 1.0, 3.0, 300f)!!
        val c = timeToX(2.0, 1.0, 3.0, 300f)!!
        assertEquals(c - a, b - c, 1e-3f)
    }

    @Test
    fun outsideRangeHasNoCursor() {
        assertNull(timeToX(0.99, 1.0, 3.0, 200f))
        assertNull(timeToX(3.01, 1.0, 3.0, 200f))
    }

    @Test
    fun emptyOrDegenerateSpanHasNoCursor() {
        assertNull(timeToX(1.0, 1.0, 1.0, 200f))
        assertNull(timeToX(1.0, 2.0, 1.0, 200f))
        assertNull(timeToX(Double.NaN, 1.0, 3.0, 200f))
        assertNull(timeToX(2.0, 1.0, 3.0, 10f, paddingPx = 12f))
    }
}
