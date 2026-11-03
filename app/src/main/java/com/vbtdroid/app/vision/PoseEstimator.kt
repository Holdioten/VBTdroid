package com.vbtdroid.app.vision

import android.graphics.Bitmap
import com.vbtdroid.core.metrics.Point2

/**
 * Landmark coordinates are pixels by default. [normalized] exists for adapters/tests and is
 * converted with the real frame dimensions before any angle math.
 */
data class PoseFrame(
    val tSec: Double,
    val landmarks: Map<Int, Point2>,
    val visibility: Map<Int, Float>,
    val frameWidth: Int = 1,
    val frameHeight: Int = 1,
    val normalized: Boolean = false,
) {
    fun pixelPoint(index: Int): Point2? {
        val point = landmarks[index] ?: return null
        if (!normalized) return point
        require(frameWidth > 0 && frameHeight > 0) { "Normalized landmarks require frame dimensions" }
        return Point2(point.x * frameWidth, point.y * frameHeight)
    }
}

interface PoseEstimator {
    fun estimate(bitmap: Bitmap, tSec: Double): PoseFrame?
    fun reset() = Unit
}
