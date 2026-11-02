package com.vbtdroid.app.vision

import android.graphics.Bitmap
import com.vbtdroid.core.metrics.Point2

data class PoseFrame(
    val tSec: Double,
    val landmarks: Map<Int, Point2>,
    val visibility: Map<Int, Float>,
)

interface PoseEstimator {
    fun estimate(bitmap: Bitmap, tSec: Double): PoseFrame?
}
