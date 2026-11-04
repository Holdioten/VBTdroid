package com.vbtdroid.app.vision

import kotlin.math.abs

object PlateRoi {
    /**
     * Local search ROI radius around the predicted plate center.
     *
     * The reference major axis is the full plate diameter, so a factor below 1.0 (~0.9 used
     * before PC tuning) clipped the plate and left the ellipse fit with a partial arc. 1.35
     * covers the whole diameter plus margin for per-frame center prediction error.
     */
    const val LOCAL_SEARCH_RADIUS_SCALE = 1.35f

    fun localSearchRadiusPx(referenceMajorAxisPx: Float): Float {
        if (!referenceMajorAxisPx.isFinite() || referenceMajorAxisPx <= 0f) return 0f
        return referenceMajorAxisPx * LOCAL_SEARCH_RADIUS_SCALE
    }

    fun selectLargest(detections: List<PlateDetection>): PlateDetection? =
        detections.maxByOrNull { it.widthPx.coerceAtLeast(0f) * it.heightPx.coerceAtLeast(0f) }

    fun isValidMajorAxis(
        majorAxisPx: Float,
        expectedPx: Float,
        tolerance: Float = 0.25f,
    ): Boolean {
        if (!majorAxisPx.isFinite() || !expectedPx.isFinite() || !tolerance.isFinite()) return false
        if (majorAxisPx <= 0f || expectedPx <= 0f || tolerance < 0f) return false
        return abs(majorAxisPx - expectedPx) / expectedPx <= tolerance
    }
}

/** 初始化后固定的参考直径；接收观测不会改变参考值。 */
internal class PlateAxisReference(
    val majorAxisPx: Float,
    private val tolerance: Float = 0.25f,
) {
    init {
        require(majorAxisPx.isFinite() && majorAxisPx > 0f) { "Reference plate diameter must be positive and finite" }
        require(tolerance.isFinite() && tolerance >= 0f) { "Axis tolerance must be non-negative and finite" }
    }

    fun accepts(candidateMajorAxisPx: Float): Boolean =
        PlateRoi.isValidMajorAxis(candidateMajorAxisPx, majorAxisPx, tolerance)
}
