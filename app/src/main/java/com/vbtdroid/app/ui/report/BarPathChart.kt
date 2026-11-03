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

@Composable
fun BarPathChart(
    samples: List<MotionSample>,
    rep: RepSlice? = null,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.secondary
    val axisColor = MaterialTheme.colorScheme.outline
    val background = MaterialTheme.colorScheme.surfaceVariant
    val points = samples.filter {
        rep == null || (it.tSec >= rep.startSec && it.tSec <= rep.endSec)
    }
    Canvas(
        modifier.fillMaxWidth().height(180.dp).background(background),
    ) {
        if (points.size < 2) return@Canvas
        val minX = points.minOf { it.xM }
        val maxX = points.maxOf { it.xM }
        val minY = points.minOf { it.yM }
        val maxY = points.maxOf { it.yM }
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
        val path = Path()
        points.forEachIndexed { index, sample ->
            val offset = point(sample)
            if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
        }
        drawPath(path, lineColor, style = Stroke(width = 3f))
    }
}
