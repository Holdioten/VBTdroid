package com.vbtdroid.app.ui.report

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * The analysis service writes the overlay MP4 to cache and records its absolute path on the result.
 * Returns the file only when it can actually be played back; otherwise null (first-frame fallback).
 */
fun overlayVideoFile(path: String?): File? {
    if (path.isNullOrBlank()) return null
    val file = File(path)
    return file.takeIf { it.isFile && it.canRead() && it.length() > 0L }
}

/**
 * Playback state for the overlay video behind the report. Plain fields are written by the view;
 * observable fields drive the controls. One instance per report.
 */
@Stable
class OverlayPlayback {
    internal var view: android.widget.VideoView? = null
    internal var targetMs: Int = 0
    var playing by mutableStateOf(false)
    var positionMs by mutableFloatStateOf(0f)
    var durationMs by mutableFloatStateOf(0f)
    /** Set when the player reports an error; the report then falls back to the first frame. */
    var failed by mutableStateOf(false)

    fun togglePlay() {
        val v = view ?: return
        if (v.isPlaying) v.pause() else v.start()
        playing = v.isPlaying
    }

    /** Seeks now if prepared, otherwise on prepare. */
    fun seekTo(ms: Int) {
        targetMs = ms.coerceAtLeast(0)
        view?.seekTo(targetMs)
        positionMs = targetMs.toFloat()
    }

    fun release() {
        view?.stopPlayback()
        view = null
    }
}
