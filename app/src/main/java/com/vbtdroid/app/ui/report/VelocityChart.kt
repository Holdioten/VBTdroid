package com.vbtdroid.app.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.app.ui.theme.VbtTheme
import com.vbtdroid.core.metrics.MotionSample
import com.vbtdroid.core.phases.PhaseType
import com.vbtdroid.core.phases.RepSlice
import kotlin.math.abs

/**
 * Vertical velocity over one rep. Phase bands sit behind the curve at 12% alpha; the dashed line is
 * the rep's MCV. The peak label is a text overlay, not drawn on the canvas.
 */
@Composable
fun VelocityChart(
    samples: List<MotionSample>,
    rep: RepSlice? = null,
    meanVelocityMps: Double? = null,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    chartBackground: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val axisColor = VbtTheme.colors.outline
    val phaseColors = phaseColors()
    val points = samples.filter {
        rep == null || (it.tSec >= rep.startSec && it.tSec <= rep.endSec)
    }
    val peak = points.maxOfOrNull { it.vyMps }
    val description = stringResource(
        R.string.report_velocity_chart_description,
        meanVelocityMps?.let { "%.2f".format(it) } ?: stringResource(R.string.report_value_missing),
    )
    Box(modifier.fillMaxWidth().height(height).background(chartBackground)) {
        Canvas(Modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
            if (points.size < 2) return@Canvas
            val minTime = points.minOf { it.tSec }
            val maxTime = points.maxOf { it.tSec }
            var minVelocity = minOf(0.0, points.minOf { it.vyMps })
            var maxVelocity = maxOf(0.0, points.maxOf { it.vyMps })
            if (abs(maxVelocity - minVelocity) < 1e-9) {
                minVelocity -= 0.5
                maxVelocity += 0.5
            }
            val timeSpan = (maxTime - minTime).takeIf { it > 0.0 } ?: 1.0
            val velocitySpan = maxVelocity - minVelocity
            fun xFor(t: Double): Float = 12f + (t - minTime).toFloat() / timeSpan.toFloat() * (size.width - 24f)
            fun yFor(v: Double): Float = size.height - 12f -
                (v - minVelocity).toFloat() / velocitySpan.toFloat() * (size.height - 24f)

            // Phase bands (spec §4.4 item 2).
            rep?.phases?.forEach { phase ->
                val left = xFor(phase.startSec)
                val right = xFor(phase.endSec)
                drawRect(
                    color = phaseColors.getValue(phase.type).copy(alpha = 0.12f),
                    topLeft = Offset(left, 0f),
                    size = androidx.compose.ui.geometry.Size((right - left).coerceAtLeast(0f), size.height),
                )
            }

            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            val zeroY = yFor(0.0)
            drawLine(axisColor, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1f, pathEffect = dash)
            meanVelocityMps?.takeIf { it.isFinite() }?.let { mean ->
                val meanY = yFor(mean)
                drawLine(
                    lineColor.copy(alpha = 0.4f),
                    Offset(0f, meanY),
                    Offset(size.width, meanY),
                    strokeWidth = 1f,
                    pathEffect = dash,
                )
            }
            val path = Path()
            points.forEachIndexed { index, sample ->
                val x = xFor(sample.tSec)
                val y = yFor(sample.vyMps)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx()))
        }
        peak?.let {
            Text(
                text = stringResource(R.string.report_peak_velocity_label, "%.2f".format(it)),
                style = MaterialTheme.typography.labelSmall,
                color = VbtTheme.colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            )
        }
    }
}

@Composable
internal fun phaseColors(): Map<PhaseType, Color> {
    val colors = VbtTheme.colors
    return mapOf(
        PhaseType.ECCENTRIC to colors.phaseEccentric,
        PhaseType.PAUSE to colors.phasePause,
        PhaseType.CONCENTRIC to colors.phaseConcentric,
        PhaseType.LOCKOUT to colors.phaseLockout,
    )
}
