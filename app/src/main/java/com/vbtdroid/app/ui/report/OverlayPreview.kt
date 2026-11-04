package com.vbtdroid.app.ui.report

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vbtdroid.app.analysis.AnalysisResult
import com.vbtdroid.app.overlay.VideoOverlayExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * Generates the overlay MP4 into app cache for in-app preview.
 *
 * Each start gets a generation number and its own output file. Closing or restarting bumps the
 * generation, so a render that finishes late deletes its own file and never shows up in the UI.
 */
class OverlayPreviewController(
    private val scope: CoroutineScope,
    private val cacheRoot: File,
) {
    var open by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var file by mutableStateOf<File?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var generation = 0
    private var job: Job? = null

    fun start(context: Context, source: Uri, result: AnalysisResult) {
        generation++
        val mine = generation
        job?.cancel()
        file?.delete()
        open = true
        busy = true
        progress = 0f
        file = null
        error = null
        val output = File(cacheRoot, "overlay-preview/vbtdroid-overlay-preview-$mine.mp4")
        job = scope.launch {
            val failure = runCatching {
                VideoOverlayExporter().exportToFile(context, source, output, result) { value ->
                    if (mine == generation) progress = value
                }
            }.exceptionOrNull()
            if (mine != generation) {
                output.delete()
                return@launch
            }
            busy = false
            if (failure == null) {
                file = output
            } else {
                output.delete()
                error = failure.message ?: failure::class.java.simpleName
            }
        }
    }

    /** Closes the preview and removes the cached file (spec: 关闭 deletes the cache). */
    fun close() {
        generation++
        job?.cancel()
        job = null
        file?.delete()
        file = null
        busy = false
        error = null
        open = false
    }
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
