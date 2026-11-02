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
