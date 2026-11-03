package com.vbtdroid.app.video

data class VideoMeta(
    val durationSec: Double,
    val width: Int,
    val height: Int,
    val rotationDeg: Int,
    val nominalFps: Double,
    val effectiveFps: Double,
)

object VideoLimits {
    const val MAX_DURATION_SEC = 10.0 * 60.0

    fun requireSupportedDuration(durationSec: Double) {
        require(durationSec.isFinite() && durationSec > 0.0) { "Video duration is unavailable" }
        require(durationSec <= MAX_DURATION_SEC) {
            "Video exceeds the 10-minute analysis limit"
        }
    }
}
