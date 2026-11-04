package com.vbtdroid.app.ui.report

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vbtdroid.app.R
import com.vbtdroid.app.ui.theme.Bg
import com.vbtdroid.app.ui.theme.MinTouchTarget
import com.vbtdroid.app.ui.theme.VbtSpacing
import java.io.File

/**
 * Preview of the cached overlay MP4: the burned-in cards, no report chrome. The report itself
 * keeps playing the original source video; this dialog is the only place the overlay is shown.
 */
@Composable
internal fun OverlayPreviewDialog(file: File, onDismiss: () -> Unit) {
    val playback = remember(file) { OverlayPlayback() }
    val uri = remember(file) { Uri.fromFile(file) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
            Box(Modifier.fillMaxSize()) {
                if (playback.failed) {
                    ReportPreview(
                        videoUri = uri.toString(),
                        modifier = Modifier.fillMaxSize(),
                        fill = true,
                    )
                } else {
                    CoverVideo(uri = uri, playback = playback, modifier = Modifier.fillMaxSize())
                }
                PlaybackBar(
                    playback = playback,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = VbtSpacing.m, vertical = VbtSpacing.m),
                )
                Surface(
                    shape = CircleShape,
                    color = GlassColor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(VbtSpacing.s)
                        .size(MinTouchTarget)
                        .clickable(onClick = onDismiss),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.report_overlay_close),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
