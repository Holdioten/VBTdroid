package com.vbtdroid.app.ui.import

import android.graphics.RectF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** What the user grabbed at the start of a drag on the ROI editor. */
enum class RoiHitTarget {
    OUTSIDE,
    INSIDE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    ;

    val isCorner: Boolean
        get() = this == TOP_LEFT || this == TOP_RIGHT || this == BOTTOM_LEFT || this == BOTTOM_RIGHT
}

/**
 * Pure ROI geometry in bitmap pixel coordinates. All functions clamp to the bitmap bounds so the
 * Compose layer only has to map pointer positions and forward the results.
 */
object RoiGeometry {
    const val MIN_SIZE_PX = 24f
    const val MIN_SIZE_FRACTION = 0.05f

    fun minimumSize(bitmapWidth: Int, bitmapHeight: Int): Float =
        max(MIN_SIZE_PX, MIN_SIZE_FRACTION * min(bitmapWidth, bitmapHeight))

    fun clamp(rect: RectF, bitmapWidth: Int, bitmapHeight: Int): RectF {
        val width = bitmapWidth.toFloat()
        val height = bitmapHeight.toFloat()
        val left = min(rect.left, rect.right).coerceIn(0f, width)
        val right = max(rect.left, rect.right).coerceIn(0f, width)
        val top = min(rect.top, rect.bottom).coerceIn(0f, height)
        val bottom = max(rect.top, rect.bottom).coerceIn(0f, height)
        return RectF(left, top, right, bottom)
    }

    fun hitTest(
        roi: RectF?,
        x: Float,
        y: Float,
        bitmapWidth: Int,
        bitmapHeight: Int,
        touchRadiusPx: Float,
    ): RoiHitTarget {
        if (roi == null || roi.isEmpty || bitmapWidth <= 0 || bitmapHeight <= 0) return RoiHitTarget.OUTSIDE
        val radius = max(0f, touchRadiusPx)
        val corners = listOf(
            RoiHitTarget.TOP_LEFT to (roi.left to roi.top),
            RoiHitTarget.TOP_RIGHT to (roi.right to roi.top),
            RoiHitTarget.BOTTOM_LEFT to (roi.left to roi.bottom),
            RoiHitTarget.BOTTOM_RIGHT to (roi.right to roi.bottom),
        )
        corners.firstOrNull { (_, corner) -> hypot(x - corner.first, y - corner.second) <= radius }
            ?.let { return it.first }
        return if (roi.contains(x, y)) RoiHitTarget.INSIDE else RoiHitTarget.OUTSIDE
    }

    fun move(
        roi: RectF,
        dx: Float,
        dy: Float,
        bitmapWidth: Int,
        bitmapHeight: Int,
    ): RectF {
        val width = roi.width()
        val height = roi.height()
        val maxLeft = max(0f, bitmapWidth - width)
        val maxTop = max(0f, bitmapHeight - height)
        val left = (roi.left + dx).coerceIn(0f, maxLeft)
        val top = (roi.top + dy).coerceIn(0f, maxTop)
        return RectF(left, top, left + width, top + height)
    }

    fun resize(
        roi: RectF,
        target: RoiHitTarget,
        x: Float,
        y: Float,
        bitmapWidth: Int,
        bitmapHeight: Int,
        minSizePx: Float = minimumSize(bitmapWidth, bitmapHeight),
    ): RectF {
        if (!target.isCorner) return RectF(roi)
        val width = bitmapWidth.toFloat()
        val height = bitmapHeight.toFloat()
        val minSize = max(0f, minSizePx)
        val pointerX = x.coerceIn(0f, width)
        val pointerY = y.coerceIn(0f, height)
        return when (target) {
            RoiHitTarget.TOP_LEFT -> RectF(
                pointerX.coerceIn(0f, max(0f, roi.right - minSize)),
                pointerY.coerceIn(0f, max(0f, roi.bottom - minSize)),
                roi.right,
                roi.bottom,
            )
            RoiHitTarget.TOP_RIGHT -> RectF(
                roi.left,
                pointerY.coerceIn(0f, max(0f, roi.bottom - minSize)),
                pointerX.coerceIn(min(roi.left + minSize, width), width),
                roi.bottom,
            )
            RoiHitTarget.BOTTOM_LEFT -> RectF(
                pointerX.coerceIn(0f, max(0f, roi.right - minSize)),
                roi.top,
                roi.right,
                pointerY.coerceIn(min(roi.top + minSize, height), height),
            )
            RoiHitTarget.BOTTOM_RIGHT -> RectF(
                roi.left,
                roi.top,
                pointerX.coerceIn(min(roi.left + minSize, width), width),
                pointerY.coerceIn(min(roi.top + minSize, height), height),
            )
            else -> RectF(roi)
        }
    }
}
