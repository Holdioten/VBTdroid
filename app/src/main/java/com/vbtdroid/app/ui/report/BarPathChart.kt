package com.vbtdroid.app.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.app.ui.theme.VbtTheme
import com.vbtdroid.core.metrics.MotionSample
import com.vbtdroid.core.phases.PhaseType
import com.vbtdroid.core.phases.RepSlice
import com.vbtdroid.core.phases.RepTimeline

/**
 * Bar path for one rep (spec §4.4 item 3). Start is a hollow dot, end a filled dot. When
 * [comparisonRep] is set (best rep), it is drawn in comparison orange on the same scale.
 */
@Composable
fun BarPathChart(
    samples: List<MotionSample>,
    rep: RepSlice? = null,
    modifier: Modifier = Modifier,
    comparisonRep: RepSlice? = null,
    height: Dp = 220.dp,
    chartBackground: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    /** Playback position in seconds; the trail is drawn up to this time. Null draws the whole rep. */
    progressSec: Double? = null,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val phases = phaseColors()
    val stageColor = { type: PhaseType? ->
        when (type) {
            PhaseType.ECCENTRIC -> phases.getValue(PhaseType.ECCENTRIC)
            PhaseType.CONCENTRIC -> phases.getValue(PhaseType.CONCENTRIC)
            PhaseType.PAUSE -> phases.getValue(PhaseType.PAUSE).copy(alpha = 0.7f)
            PhaseType.LOCKOUT -> phases.getValue(PhaseType.LOCKOUT).copy(alpha = 0.7f)
            null -> phases.getValue(PhaseType.PAUSE).copy(alpha = 0.5f)
        }
    }
    // Drawn runs are the current rep only, split by phase; the scale still uses the whole rep so the
    // path does not jump while it is revealed.
    val drawnRuns = rep?.let { RepTimeline.trailRuns(samples, it, progressSec ?: it.endSec) }.orEmpty()
    val compareColor = VbtTheme.colors.comparison
    val axisColor = VbtTheme.colors.outline
    val points = samples.filter {
        rep == null || (it.tSec >= rep.startSec && it.tSec <= rep.endSec)
    }
    val comparePoints = comparisonRep?.let { other ->
        samples.filter { it.tSec >= other.startSec && it.tSec <= other.endSec }
    }.orEmpty()
    val description = stringResource(R.string.report_bar_path_chart_description)
    Canvas(
        modifier.fillMaxWidth().height(height).background(chartBackground)
            .semantics { contentDescription = description },
    ) {
        if (points.size < 2) return@Canvas
        val all = points + comparePoints
        val minX = all.minOf { it.xM }
        val maxX = all.maxOf { it.xM }
        val minY = all.minOf { it.yM }
        val maxY = all.maxOf { it.yM }
        val xSpan = (maxX - minX).takeIf { it > 1e-9 } ?: 1.0
        val ySpan = (maxY - minY).takeIf { it > 1e-9 } ?: 1.0
        fun point(sample: MotionSample): Offset {
            val x = 12f + (sample.xM - minX).toFloat() / xSpan.toFloat() * (size.width - 24f)
            val y = size.height - 12f -
                (sample.yM - minY).toFloat() / ySpan.toFloat() * (size.height - 24f)
            return Offset(x, y)
        }

        drawLine(axisColor, Offset(12f, 0f), Offset(12f, size.height), strokeWidth = 1f)
        drawLine(
            axisColor,
            Offset(0f, size.height - 12f),
            Offset(size.width, size.height - 12f),
            strokeWidth = 1f,
        )
        if (comparePoints.size >= 2) {
            val comparePath = Path()
            comparePoints.forEachIndexed { index, sample ->
                val offset = point(sample)
                if (index == 0) comparePath.moveTo(offset.x, offset.y) else comparePath.lineTo(offset.x, offset.y)
            }
            drawPath(comparePath, compareColor.copy(alpha = 0.7f), style = Stroke(width = 2.dp.toPx()))
        }
        if (rep == null) {
            val path = Path()
            points.forEachIndexed { index, sample ->
                val offset = point(sample)
                if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
            }
            drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx()))
        } else {
            for (run in drawnRuns) {
                if (run.samples.size < 2) continue
                val path = Path()
                run.samples.forEachIndexed { index, sample ->
                    val offset = point(sample)
                    if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
                }
                drawPath(path, stageColor(run.type), style = Stroke(width = 3.dp.toPx()))
            }
            val drawn = drawnRuns.flatMap { it.samples }
            if (drawn.isNotEmpty()) {
                drawCircle(lineColor, radius = 5.dp.toPx(), center = point(drawn.first()), style = Stroke(width = 2.dp.toPx()))
                drawCircle(lineColor, radius = 5.dp.toPx(), center = point(drawn.last()))
            }
        }
    }
}
