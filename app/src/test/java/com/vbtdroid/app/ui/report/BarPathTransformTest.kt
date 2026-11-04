package com.vbtdroid.app.ui.report

import org.junit.Assert.assertEquals
import org.junit.Test

class BarPathTransformTest {
    @Test
    fun `x and y use the same pixels per metre`() {
        val fit = fitBarPath(
            minX = 0.0, maxX = 0.5,
            minY = 0.0, maxY = 1.0,
            widthPx = 400f, heightPx = 220f, paddingPx = 12f,
        )

        // Height limits the fit: 196 px / 1.0 m.
        assertEquals(196f, fit.pxPerM, 1e-4f)
        val horizontalMetre = fit.x(1.0) - fit.x(0.0)
        val verticalMetre = fit.y(0.0, 220f) - fit.y(1.0, 220f)
        assertEquals(horizontalMetre, verticalMetre, 1e-4f)
    }

    @Test
    fun `narrow data is letterboxed and centred instead of stretched`() {
        val fit = fitBarPath(
            minX = 0.0, maxX = 0.05,
            minY = 0.0, maxY = 1.0,
            widthPx = 400f, heightPx = 220f, paddingPx = 12f,
        )

        // Vertical still drives the scale; the 0.05 m horizontal span stays 0.05 m wide.
        assertEquals(196f, fit.pxPerM, 1e-4f)
        assertEquals(0.05f * 196f, fit.x(0.05) - fit.x(0.0), 1e-3f)
        // Centred: equal free space left and right.
        val freeLeft = fit.x(0.0) - 12f
        val freeRight = (400f - 12f) - fit.x(0.05)
        assertEquals(freeLeft, freeRight, 1e-3f)
        // Vertical span fills the usable height, so no top letterbox.
        assertEquals(12f, fit.topPx, 1e-3f)
    }

    @Test
    fun `wide data is letterboxed vertically`() {
        val fit = fitBarPath(
            minX = 0.0, maxX = 1.0,
            minY = 0.0, maxY = 0.05,
            widthPx = 400f, heightPx = 220f, paddingPx = 12f,
        )

        assertEquals(376f, fit.pxPerM, 1e-4f)
        assertEquals(12f, fit.leftPx, 1e-3f)
        val expectedTop = 12f + (196f - 0.05f * 376f) / 2f
        assertEquals(expectedTop, fit.topPx, 1e-3f)
    }
}
