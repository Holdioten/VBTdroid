package com.vbtdroid.app.ui.report

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.app.ui.components.ChipTone
import com.vbtdroid.app.ui.components.StatusChip
import com.vbtdroid.app.ui.theme.Bg
import com.vbtdroid.app.ui.theme.VbtTheme
import com.vbtdroid.app.video.VideoDecoder
import com.vbtdroid.core.metrics.MotionSample
import com.vbtdroid.core.phases.RepSlice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Report header image: first frame (decoded off the main thread) with a schematic bar path for the
 * selected rep (primary) and optional best rep (comparison orange). Falls back to a gradient when
 * the frame cannot be read.
 */
@Composable
fun ReportPreview(
    videoUri: String,
    selected: RepSlice?,
    best: RepSlice?,
    samples: List<MotionSample>,
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
    val compare = VbtTheme.colors.comparison
    val primary = MaterialTheme.colorScheme.primary

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
        Canvas(Modifier.fillMaxSize()) {
            fun trail(rep: RepSlice?): List<Offset> {
                val points = samples.filter { rep != null && it.tSec >= rep.startSec && it.tSec <= rep.endSec }
                return points.map { Offset(it.xM.toFloat(), it.yM.toFloat()) }
            }
            val all = trail(selected) + trail(best)
            if (all.size < 2) return@Canvas
            val minX = all.minOf { it.x }
            val maxX = all.maxOf { it.x }
            val minY = all.minOf { it.y }
            val maxY = all.maxOf { it.y }
            val pad = 28.dp.toPx()
            val spanX = (maxX - minX).takeIf { it > 1e-6f } ?: 1f
            val spanY = (maxY - minY).takeIf { it > 1e-6f } ?: 1f
            val scale = minOf((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY)
            val offsetX = (size.width - spanX * scale) / 2f
            val offsetY = (size.height - spanY * scale) / 2f
            fun map(p: Offset) = Offset(
                offsetX + (p.x - minX) * scale,
                size.height - (offsetY + (p.y - minY) * scale),
            )
            fun draw(points: List<Offset>, color: Color, width: Float) {
                if (points.size < 2) return
                val path = Path()
                points.map(::map).forEachIndexed { index, p ->
                    if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(path, color, style = Stroke(width = width.dp.toPx()))
            }
            if (best != null && best.index != selected?.index) draw(trail(best), compare.copy(alpha = 0.7f), 2f)
            draw(trail(selected), primary, 3f)
            trail(selected).lastOrNull()?.let { drawCircle(primary, radius = 5.dp.toPx(), center = map(it)) }
        }
        if (current == null) {
            Text(
                text = stringResource(R.string.report_preview_placeholder),
                style = MaterialTheme.typography.labelSmall,
                color = VbtTheme.colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
        }
        StatusChip(
            text = stringResource(R.string.report_trajectory_schematic),
            tone = ChipTone.NEUTRAL,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        )
    }
}
