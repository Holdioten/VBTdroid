package com.vbtdroid.app.vision

import kotlin.math.abs

object PlateRoi {
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
