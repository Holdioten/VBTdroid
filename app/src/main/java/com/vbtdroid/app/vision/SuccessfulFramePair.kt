package com.vbtdroid.app.vision

/** Atomic successful image/center/time tuple. Failures deliberately do not advance any component. */
internal class SuccessfulFramePair<T>(private val release: (T) -> Unit) {
    var frame: T? = null
        private set
    var centerX: Float = 0f
        private set
    var centerY: Float = 0f
        private set
    var timeSec: Double? = null
        private set

    fun matched(frame: T, centerX: Float, centerY: Float, timeSec: Double) {
        this.frame?.let(release)
        this.frame = frame
        this.centerX = centerX
        this.centerY = centerY
        this.timeSec = timeSec
    }

    fun failed() = Unit

    fun clear() {
        frame?.let(release)
        frame = null
        centerX = 0f
        centerY = 0f
        timeSec = null
    }
}
