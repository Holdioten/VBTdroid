package com.vbtdroid.app.ui.import

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoiGeometryTest {
    private val bitmapWidth = 1000
    private val bitmapHeight = 500
    private val roi = RectF(100f, 100f, 300f, 200f)

    @Test
    fun `minimum size is the larger of 24px and five percent of the short side`() {
        assertEquals(24f, RoiGeometry.minimumSize(400, 400), 1e-6f)
        assertEquals(25f, RoiGeometry.minimumSize(1000, 500), 1e-6f)
    }

    @Test
    fun `hit test detects corners inside and outside`() {
        val radius = 24f

        assertEquals(
            RoiHitTarget.TOP_LEFT,
            RoiGeometry.hitTest(roi, 105f, 105f, bitmapWidth, bitmapHeight, radius),
        )
        assertEquals(
            RoiHitTarget.BOTTOM_RIGHT,
            RoiGeometry.hitTest(roi, 295f, 195f, bitmapWidth, bitmapHeight, radius),
        )
        assertEquals(
            RoiHitTarget.INSIDE,
            RoiGeometry.hitTest(roi, 200f, 150f, bitmapWidth, bitmapHeight, radius),
        )
        assertEquals(
            RoiHitTarget.OUTSIDE,
            RoiGeometry.hitTest(roi, 500f, 400f, bitmapWidth, bitmapHeight, radius),
        )
        assertEquals(
            RoiHitTarget.OUTSIDE,
            RoiGeometry.hitTest(null, 200f, 150f, bitmapWidth, bitmapHeight, radius),
        )
    }

    @Test
    fun `move keeps size and clamps to bitmap bounds`() {
        val moved = RoiGeometry.move(roi, 20f, -30f, bitmapWidth, bitmapHeight)

        assertEquals(120f, moved.left, 1e-6f)
        assertEquals(70f, moved.top, 1e-6f)
        assertEquals(320f, moved.right, 1e-6f)
        assertEquals(170f, moved.bottom, 1e-6f)

        val clamped = RoiGeometry.move(roi, 5000f, 5000f, bitmapWidth, bitmapHeight)
        assertEquals(800f, clamped.left, 1e-6f)
        assertEquals(400f, clamped.top, 1e-6f)
        assertEquals(200f, clamped.width(), 1e-6f)
        assertEquals(100f, clamped.height(), 1e-6f)
    }

    @Test
    fun `resize moves only the dragged corner`() {
        val resized = RoiGeometry.resize(
            roi = roi,
            target = RoiHitTarget.TOP_LEFT,
            x = 50f,
            y = 60f,
            bitmapWidth = bitmapWidth,
            bitmapHeight = bitmapHeight,
        )

        assertEquals(RectF(50f, 60f, 300f, 200f), resized)
    }

    @Test
    fun `resize clamps the dragged corner to bitmap bounds`() {
        val resized = RoiGeometry.resize(
            roi = roi,
            target = RoiHitTarget.BOTTOM_RIGHT,
            x = 2000f,
            y = 2000f,
            bitmapWidth = bitmapWidth,
            bitmapHeight = bitmapHeight,
        )

        assertEquals(1000f, resized.right, 1e-6f)
        assertEquals(500f, resized.bottom, 1e-6f)
        assertEquals(100f, resized.left, 1e-6f)
        assertEquals(100f, resized.top, 1e-6f)
    }

    @Test
    fun `resize enforces the minimum size`() {
        val resized = RoiGeometry.resize(
            roi = roi,
            target = RoiHitTarget.BOTTOM_RIGHT,
            x = 101f,
            y = 101f,
            bitmapWidth = 1000,
            bitmapHeight = 1000,
            minSizePx = 50f,
        )

        assertEquals(150f, resized.right, 1e-6f)
        assertEquals(150f, resized.bottom, 1e-6f)
        assertEquals(100f, resized.left, 1e-6f)
        assertEquals(100f, resized.top, 1e-6f)
    }

    @Test
    fun `resize ignores non-corner targets and clamp normalizes rectangles`() {
        assertEquals(roi, RoiGeometry.resize(roi, RoiHitTarget.INSIDE, 10f, 10f, bitmapWidth, bitmapHeight))

        val normalized = RoiGeometry.clamp(RectF(500f, 400f, -20f, -30f), bitmapWidth, bitmapHeight)
        assertEquals(RectF(0f, 0f, 500f, 400f), normalized)
    }
}
