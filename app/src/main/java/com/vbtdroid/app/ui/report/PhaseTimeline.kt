package com.vbtdroid.app.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.core.phases.PhaseType
import com.vbtdroid.core.phases.RepSlice
import androidx.compose.foundation.gestures.detectDragGestures
import kotlin.math.abs

/** Draggable phase boundaries. Every edit marks the adjacent phases as manual. */
@Composable
fun PhaseTimeline(
    rep: RepSlice,
    onRepChange: (RepSlice) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var workingRep by remember(rep) { mutableStateOf(rep) }
    val latestRep by rememberUpdatedState(workingRep)
    val latestValidRep by rememberUpdatedState(rep)
    val latestCallback by rememberUpdatedState(onRepChange)
    var widthPx by remember { mutableStateOf(1) }
    var activeBoundary by remember { mutableStateOf<Int?>(null) }
    val colors = phaseColors()
    val boundaryColor = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(rep.index) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val current = latestRep
                        val duration = (current.endSec - current.startSec).takeIf { it > 0.0 }
                            ?: return@detectDragGestures
                        activeBoundary = (1 until current.phases.size).minByOrNull { index ->
                            val seconds = current.phases[index].startSec - current.startSec
                            abs(offset.x - (seconds / duration * widthPx).toFloat())
                        }
                    },
                    onDragEnd = { activeBoundary = null },
                    onDragCancel = { activeBoundary = null },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val boundary = activeBoundary ?: return@detectDragGestures
                        val current = latestRep
                        val duration = (current.endSec - current.startSec).takeIf { it > 0.0 }
                            ?: return@detectDragGestures
                        val left = current.phases.getOrNull(boundary - 1) ?: return@detectDragGestures
                        val right = current.phases.getOrNull(boundary) ?: return@detectDragGestures
                        val oldBoundary = right.startSec
                        val requested = oldBoundary + dragAmount.x / widthPx * duration
                        val minimum = left.startSec + MIN_MANUAL_PHASE_SEC
                        val maximum = right.endSec - MIN_MANUAL_PHASE_SEC
                        if (minimum >= maximum) return@detectDragGestures
                        val newBoundary = requested.coerceIn(minimum, maximum)
                        if (newBoundary <= left.startSec || newBoundary >= right.endSec) {
                            return@detectDragGestures
                        }
                        val phases = current.phases.mapIndexed { index, phase ->
                            when (index) {
                                boundary - 1 -> phase.copy(endSec = newBoundary, manual = true)
                                boundary -> phase.copy(startSec = newBoundary, manual = true)
                                else -> phase
                            }
                        }
                        val updated = current.copy(phases = phases)
                        workingRep = resolveManualRepEdit(updated, latestValidRep, latestCallback)
                    },
                )
            },
    ) {
        val phases = workingRep.phases
        val duration = (workingRep.endSec - workingRep.startSec).takeIf { it > 0.0 } ?: return@Canvas
        phases.forEach { phase ->
            val left = ((phase.startSec - workingRep.startSec) / duration * size.width).toFloat()
            val right = ((phase.endSec - workingRep.startSec) / duration * size.width).toFloat()
            drawRect(
                color = colors.getValue(phase.type),
                topLeft = Offset(left, 0f),
                size = Size((right - left).coerceAtLeast(0f), size.height),
            )
        }
        for (index in 1 until phases.size) {
            val x = ((phases[index].startSec - workingRep.startSec) / duration * size.width).toFloat()
            drawLine(boundaryColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 4f)
        }
    }
    Row(Modifier.fillMaxWidth()) {
        workingRep.phases.forEach { phase ->
            val duration = (phase.endSec - phase.startSec).coerceAtLeast(0.001)
            Text(
                text = phaseDisplayName(phase.type),
                modifier = Modifier.weight(duration.toFloat()).padding(horizontal = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun phaseColors(): Map<PhaseType, Color> = mapOf(
    PhaseType.ECCENTRIC to MaterialTheme.colorScheme.tertiaryContainer,
    PhaseType.PAUSE to MaterialTheme.colorScheme.surfaceVariant,
    PhaseType.CONCENTRIC to MaterialTheme.colorScheme.primaryContainer,
    PhaseType.LOCKOUT to MaterialTheme.colorScheme.secondaryContainer,
)

@Composable
private fun phaseDisplayName(type: PhaseType): String = stringResource(
    when (type) {
        PhaseType.ECCENTRIC -> R.string.phase_eccentric
        PhaseType.PAUSE -> R.string.phase_pause
        PhaseType.CONCENTRIC -> R.string.phase_concentric
        PhaseType.LOCKOUT -> R.string.phase_lockout
    },
)

private const val MIN_MANUAL_PHASE_SEC = 0.001
