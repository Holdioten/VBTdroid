package com.vbtdroid.app.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.vbtdroid.core.metrics.MotionSample
import com.vbtdroid.core.phases.RepSlice
import kotlin.math.abs

@Composable
fun VelocityChart(
    samples: List<MotionSample>,
    rep: RepSlice? = null,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outline
    val background = MaterialTheme.colorScheme.surfaceVariant
    val points = samples.filter {
        rep == null || (it.tSec >= rep.startSec && it.tSec <= rep.endSec)
    }
    Canvas(
        modifier.fillMaxWidth().height(180.dp).background(background),
    ) {
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
        fun point(sample: MotionSample): Offset {
            val x = 12f + (sample.tSec - minTime).toFloat() / timeSpan.toFloat() * (size.width - 24f)
            val y = size.height - 12f -
                (sample.vyMps - minVelocity).toFloat() / velocitySpan.toFloat() * (size.height - 24f)
            return Offset(x, y)
        }

        val zeroY = size.height - 12f -
            (0.0 - minVelocity).toFloat() / velocitySpan.toFloat() * (size.height - 24f)
        drawLine(axisColor, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1f)
        val path = Path()
        points.forEachIndexed { index, sample ->
            val offset = point(sample)
            if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
        }
        drawPath(path, lineColor, style = Stroke(width = 3f))
    }
}
