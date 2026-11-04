package com.vbtdroid.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayChartGeometryTest {
    @Test
    fun `bar path fit keeps one scale for both axes`() {
        // 2 m wide, 1 m tall chart; the height is the limiting axis, so both use 100 px/m.
        val fit = fitOverlayBarPath(
            minX = 0.0,
            maxX = 2.0,
            minY = 0.0,
            maxY = 1.0,
            widthPx = 200f,
            heightPx = 100f,
        )

        assertEquals(100f, fit.pxPerMetre, 1e-4f)
        assertEquals(0f, fit.x(0.0), 1e-4f)
        assertEquals(200f, fit.x(2.0), 1e-4f)
        assertEquals(100f, fit.y(0.0), 1e-4f)
        assertEquals(0f, fit.y(1.0), 1e-4f)
    }

    @Test
    fun `vertical path stays finite and centred`() {
        val fit = fitOverlayBarPath(
            minX = 0.1,
            maxX = 0.1,
            minY = 0.0,
            maxY = 0.5,
            widthPx = 100f,
            heightPx = 100f,
        )

        assertEquals(true, fit.pxPerMetre.isFinite())
        assertEquals(50f, fit.x(0.1), 1e-3f)
        assertEquals(100f, fit.y(0.0), 1e-3f)
        assertEquals(0f, fit.y(0.5), 1e-3f)
    }

    @Test
    fun `time mapping only returns a cursor inside the rep`() {
        assertEquals(10f, overlayTimeToX(0.0, 0.0, 2.0, 10f, 100f)!!, 1e-4f)
        assertEquals(60f, overlayTimeToX(1.0, 0.0, 2.0, 10f, 100f)!!, 1e-4f)
        assertEquals(110f, overlayTimeToX(2.0, 0.0, 2.0, 10f, 100f)!!, 1e-4f)
        assertNull(overlayTimeToX(-0.1, 0.0, 2.0, 10f, 100f))
        assertNull(overlayTimeToX(2.1, 0.0, 2.0, 10f, 100f))
        assertNull(overlayTimeToX(1.0, 1.0, 1.0, 10f, 100f))
    }
}
