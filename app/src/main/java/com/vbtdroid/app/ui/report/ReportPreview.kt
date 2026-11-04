package com.vbtdroid.app.ui.report

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.app.ui.theme.Bg
import com.vbtdroid.app.ui.theme.VbtTheme
import com.vbtdroid.app.video.VideoDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Report header image: the video's first frame (decoded off the main thread), with a gradient
 * fallback when the frame cannot be read. No bar trail is drawn here: before the first rep starts
 * there is no trail, and playback only exists once the overlay preview has been generated.
 */
@Composable
fun ReportPreview(
    videoUri: String,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
) {
    val context = LocalContext.current
    val decoder = remember(context) { VideoDecoder(context) }
    var frame by remember(videoUri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(videoUri) {
        frame = if (videoUri.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { decoder.readFirstFrame(Uri.parse(videoUri)) }.getOrNull()
            }
        }
    }
    val current = frame
    DisposableEffect(current) {
        onDispose { current?.takeIf { !it.isRecycled }?.recycle() }
    }
    val description = stringResource(R.string.report_preview_description)

    Box(
        modifier
            .then(if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(220.dp))
            .clip(if (fill) RoundedCornerShape(0.dp) else MaterialTheme.shapes.large)
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerLow),
                ),
            )
            .semantics { contentDescription = description },
    ) {
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Bg.copy(alpha = 0.4f)))
        }
        if (current == null) {
            Text(
                text = stringResource(R.string.report_preview_placeholder),
                style = MaterialTheme.typography.labelSmall,
                color = VbtTheme.colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
        }
    }
}
