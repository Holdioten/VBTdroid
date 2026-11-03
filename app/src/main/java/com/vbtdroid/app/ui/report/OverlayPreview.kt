package com.vbtdroid.app.ui.report

import android.content.Context
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vbtdroid.app.R
import com.vbtdroid.app.analysis.AnalysisResult
import com.vbtdroid.app.overlay.VideoOverlayExporter
import com.vbtdroid.app.ui.components.MessageBanner
import com.vbtdroid.app.ui.components.BannerTone
import com.vbtdroid.app.ui.theme.Bg
import com.vbtdroid.app.ui.theme.MinTouchTarget
import com.vbtdroid.app.ui.theme.VbtSpacing
import com.vbtdroid.app.ui.theme.VbtTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

/** Plain holder so the VideoView reference does not trigger recomposition. */
private class VideoRef {
    var view: VideoView? = null
}

@Composable
fun OverlayPreviewDialog(
    controller: OverlayPreviewController,
    saving: Boolean,
    onSave: () -> Unit,
) {
    if (!controller.open) return
    Dialog(
        onDismissRequest = controller::close,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
            Column(
                Modifier.fillMaxSize().padding(VbtSpacing.l),
                verticalArrangement = Arrangement.spacedBy(VbtSpacing.m),
            ) {
                Text(
                    stringResource(R.string.report_overlay_preview_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val current = controller.file
                    when {
                        current != null -> OverlayPlayer(current)
                        controller.busy -> GeneratingState(controller.progress)
                        controller.error != null -> MessageBanner(
                            text = if (controller.error == VideoOverlayExporter.ERROR_NO_OBSERVATIONS) {
                                stringResource(R.string.report_export_overlay_no_data)
                            } else {
                                stringResource(R.string.report_export_overlay_failed, controller.error.orEmpty())
                            },
                            tone = BannerTone.ERROR,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
                    OutlinedButton(
                        onClick = controller::close,
                        modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
                    ) {
                        Text(stringResource(R.string.report_overlay_close))
                    }
                    Button(
                        onClick = onSave,
                        enabled = controller.file != null && !saving,
                        modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
                    ) {
                        Text(stringResource(R.string.report_overlay_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneratingState(progress: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VbtSpacing.s),
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        Text(
            stringResource(R.string.report_export_overlay_progress, (progress * 100f).toInt()),
            style = MaterialTheme.typography.bodyMedium,
            color = VbtTheme.colors.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(horizontal = VbtSpacing.xl),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun OverlayPlayer(file: File) {
    val ref = remember(file) { VideoRef() }
    var playing by remember(file) { mutableStateOf(false) }
    var position by remember(file) { mutableFloatStateOf(0f) }
    var duration by remember(file) { mutableFloatStateOf(0f) }

    // Poll the VideoView for position; no listeners, so the UI never blocks on playback.
    LaunchedEffect(file) {
        while (true) {
            val view = ref.view
            if (view != null && view.duration > 0) {
                duration = view.duration.toFloat()
                position = view.currentPosition.toFloat()
                playing = view.isPlaying
            }
            delay(200)
        }
    }
    DisposableEffect(file) {
        onDispose { ref.view?.stopPlayback() }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { context ->
                VideoView(context).apply {
                    setVideoURI(Uri.fromFile(file))
                    setOnPreparedListener { player ->
                        player.isLooping = true
                        start()
                    }
                    ref.view = this
                }
            },
            update = { view -> ref.view = view },
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s),
        ) {
            OutlinedButton(
                onClick = {
                    val view = ref.view ?: return@OutlinedButton
                    if (view.isPlaying) view.pause() else view.start()
                    playing = view.isPlaying
                },
                modifier = Modifier.heightIn(min = MinTouchTarget),
            ) {
                Text(stringResource(if (playing) R.string.report_overlay_pause else R.string.report_overlay_play))
            }
            Slider(
                value = if (duration > 0f) position / duration else 0f,
                onValueChange = { fraction ->
                    val view = ref.view ?: return@Slider
                    view.seekTo((fraction * duration).toInt())
                    position = fraction * duration
                },
                enabled = duration > 0f,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
