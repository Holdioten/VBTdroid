package com.vbtdroid.app.ui.import

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.app.ui.theme.Bg
import com.vbtdroid.app.ui.theme.VbtTheme
import kotlin.math.max
import kotlin.math.min

/**
 * First-frame ROI editor.
 *
 * Dragging inside moves the box, dragging near a corner resizes it, and dragging outside draws a
 * new box. Geometry lives in [RoiGeometry]; this composable only maps pointer coordinates.
 */
@Composable
fun RoiSelector(
    bitmap: Bitmap,
    roi: RectF?,
    onRoiChanged: (RectF) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentRoi by rememberUpdatedState(roi)
    val currentOnRoiChanged by rememberUpdatedState(onRoiChanged)
    val handleTouchRadiusPx = with(LocalDensity.current) { HANDLE_TOUCH_RADIUS.toPx() }
    val minSizePx = remember(bitmap.width, bitmap.height) {
        RoiGeometry.minimumSize(bitmap.width, bitmap.height)
    }
    var activeHandle by remember { mutableStateOf<RoiHitTarget?>(null) }
    val roiColor = MaterialTheme.colorScheme.primary
    val handleColor = MaterialTheme.colorScheme.primary
    val activeHandleColor = VbtTheme.colors.speed
    val handleStrokeColor = VbtTheme.colors.skeleton
    val dimColor = Bg.copy(alpha = 0.45f)

    Box(
        modifier.fillMaxWidth()
            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
            .pointerInput(bitmap) {
                var target: RoiHitTarget? = null
                var startPoint = Offset.Zero
                var dragAnchor = Offset.Zero
                var startRoi: RectF? = null
                val touchRadius = handleTouchRadiusPx * bitmap.width / size.width

                detectDragGestures(
                    onDragStart = { start ->
                        val point = start.toFramePoint(size.width, size.height, bitmap)
                        startPoint = point
                        dragAnchor = point
                        val previous = currentRoi?.let(::RectF)
                        startRoi = previous
                        val hit = RoiGeometry.hitTest(
                            roi = previous,
                            x = point.x,
                            y = point.y,
                            bitmapWidth = bitmap.width,
                            bitmapHeight = bitmap.height,
                            touchRadiusPx = touchRadius,
                        )
                        target = hit
                        activeHandle = hit.takeIf { it.isCorner }
                        if (hit == RoiHitTarget.OUTSIDE) {
                            val empty = RectF(point.x, point.y, point.x, point.y)
                            startRoi = empty
                            currentOnRoiChanged(RectF(empty))
                        }
                    },
                    onDragEnd = {
                        target = null
                        activeHandle = null
                    },
                    onDragCancel = {
                        target = null
                        activeHandle = null
                    },
                    onDrag = { change, _ ->
                        val mode = target ?: return@detectDragGestures
                        val base = startRoi ?: return@detectDragGestures
                        val point = change.position.toFramePoint(size.width, size.height, bitmap)
                        val updated = when (mode) {
                            RoiHitTarget.OUTSIDE -> RoiGeometry.clamp(
                                RectF(
                                    min(dragAnchor.x, point.x),
                                    min(dragAnchor.y, point.y),
                                    max(dragAnchor.x, point.x),
                                    max(dragAnchor.y, point.y),
                                ),
                                bitmap.width,
                                bitmap.height,
                            )
                            RoiHitTarget.INSIDE -> RoiGeometry.move(
                                roi = base,
                                dx = point.x - startPoint.x,
                                dy = point.y - startPoint.y,
                                bitmapWidth = bitmap.width,
                                bitmapHeight = bitmap.height,
                            )
                            else -> RoiGeometry.resize(
                                roi = base,
                                target = mode,
                                x = point.x,
                                y = point.y,
                                bitmapWidth = bitmap.width,
                                bitmapHeight = bitmap.height,
                                minSizePx = minSizePx,
                            )
                        }
                        currentOnRoiChanged(updated)
                    },
                )
            },
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.import_first_frame_preview),
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(Modifier.fillMaxSize()) {
            roi?.takeIf { !it.isEmpty }?.let { selected ->
                val scaleX = size.width / bitmap.width
                val scaleY = size.height / bitmap.height
                val left = selected.left * scaleX
                val top = selected.top * scaleY
                val right = selected.right * scaleX
                val bottom = selected.bottom * scaleY
                // Dim outside the box so the selected region reads at a glance.
                drawRect(color = dimColor, topLeft = Offset.Zero, size = Size(size.width, top))
                drawRect(color = dimColor, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
                drawRect(color = dimColor, topLeft = Offset(0f, top), size = Size(left, bottom - top))
                drawRect(color = dimColor, topLeft = Offset(right, top), size = Size(size.width - right, bottom - top))
                drawRect(
                    color = roiColor,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = 3.dp.toPx()),
                )

                // Centre crosshair: visible "move" affordance for the drag-inside gesture.
                val centre = Offset((left + right) / 2f, (top + bottom) / 2f)
                val arm = 10.dp.toPx()
                drawCircle(color = handleStrokeColor, radius = 12.dp.toPx(), center = centre, style = Stroke(width = 1.5.dp.toPx()))
                drawLine(handleStrokeColor, Offset(centre.x - arm, centre.y), Offset(centre.x + arm, centre.y), strokeWidth = 1.5.dp.toPx())
                drawLine(handleStrokeColor, Offset(centre.x, centre.y - arm), Offset(centre.x, centre.y + arm), strokeWidth = 1.5.dp.toPx())

                val handleRadius = 12.dp.toPx()
                val corners = listOf(
                    RoiHitTarget.TOP_LEFT to Offset(left, top),
                    RoiHitTarget.TOP_RIGHT to Offset(right, top),
                    RoiHitTarget.BOTTOM_LEFT to Offset(left, bottom),
                    RoiHitTarget.BOTTOM_RIGHT to Offset(right, bottom),
                )
                corners.forEach { (corner, center) ->
                    drawCircle(
                        color = if (activeHandle == corner) activeHandleColor else handleColor,
                        radius = handleRadius,
                        center = center,
                    )
                    drawCircle(
                        color = handleStrokeColor,
                        radius = handleRadius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
    }
}

private val HANDLE_TOUCH_RADIUS = 24.dp

private fun Offset.toFramePoint(width: Int, height: Int, bitmap: Bitmap): Offset = Offset(
    x = (x * bitmap.width / width).coerceIn(0f, bitmap.width.toFloat()),
    y = (y * bitmap.height / height).coerceIn(0f, bitmap.height.toFloat()),
)
