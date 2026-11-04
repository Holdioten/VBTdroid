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

    /** Physical bars do not exceed roughly 8 plate diameters per second. */
    const val MAX_BAR_SPEED_DIAMETERS_PER_SEC = 8.0

    /** Minimum frame-to-frame innovation gate, in plate diameters. */
    const val MIN_INNOVATION_DIAMETERS = 0.5

    fun localSearchRadiusPx(referenceMajorAxisPx: Float): Float {
        if (!referenceMajorAxisPx.isFinite() || referenceMajorAxisPx <= 0f) return 0f
        return referenceMajorAxisPx * LOCAL_SEARCH_RADIUS_SCALE
    }

    /**
     * Largest believable center innovation for one frame. Uses the reference plate diameter as
     * the length scale, so the gate is independent of resolution: half a diameter by default,
     * and a full diameter per 0.125 s of frame gap (8 diameters/s).
     */
    fun maxInnovationPx(referenceMajorAxisPx: Float, dtSec: Double): Float {
        if (!referenceMajorAxisPx.isFinite() || referenceMajorAxisPx <= 0f) return 0f
        val dt = if (dtSec.isFinite() && dtSec > 0.0) dtSec else 0.0
        val allowed = maxOf(
            MIN_INNOVATION_DIAMETERS,
            MAX_BAR_SPEED_DIAMETERS_PER_SEC * dt,
        )
        return (referenceMajorAxisPx * allowed).toFloat()
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
    private val tolerance: Float = 0.15f,
) {
    init {
        require(majorAxisPx.isFinite() && majorAxisPx > 0f) { "Reference plate diameter must be positive and finite" }
        require(tolerance.isFinite() && tolerance >= 0f) { "Axis tolerance must be non-negative and finite" }
    }

    fun accepts(candidateMajorAxisPx: Float): Boolean =
        PlateRoi.isValidMajorAxis(candidateMajorAxisPx, majorAxisPx, tolerance)
}
