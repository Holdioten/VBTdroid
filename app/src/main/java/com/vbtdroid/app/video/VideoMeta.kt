package com.vbtdroid.app.video

data class VideoMeta(
    val durationSec: Double,
    val width: Int,
    val height: Int,
    val rotationDeg: Int,
    val nominalFps: Double,
    val effectiveFps: Double
)
