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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.vbtdroid.app.R
import kotlin.math.max
import kotlin.math.min

/** First-frame ROI editor. A drag always creates a new user-owned rectangle in frame pixels. */
@Composable
fun RoiSelector(
    bitmap: Bitmap,
    roi: RectF?,
    onRoiChanged: (RectF) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxWidth()
            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
            .pointerInput(bitmap) {
                var dragStart: Offset? = null
                detectDragGestures(
                    onDragStart = { start ->
                        dragStart = start
                        val point = start.toFramePoint(size.width, size.height, bitmap)
                        onRoiChanged(RectF(point.x, point.y, point.x, point.y))
                    },
                    onDragEnd = { dragStart = null },
                    onDragCancel = { dragStart = null },
                    onDrag = { change, _ ->
                        val start = dragStart ?: return@detectDragGestures
                        val a = start.toFramePoint(size.width, size.height, bitmap)
                        val b = change.position.toFramePoint(size.width, size.height, bitmap)
                        onRoiChanged(
                            RectF(
                                min(a.x, b.x).coerceIn(0f, bitmap.width.toFloat()),
                                min(a.y, b.y).coerceIn(0f, bitmap.height.toFloat()),
                                max(a.x, b.x).coerceIn(0f, bitmap.width.toFloat()),
                                max(a.y, b.y).coerceIn(0f, bitmap.height.toFloat()),
                            ),
                        )
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
                drawRect(
                    color = Color.Green,
                    topLeft = Offset(selected.left * scaleX, selected.top * scaleY),
                    size = Size(selected.width() * scaleX, selected.height() * scaleY),
                    style = Stroke(width = 3f),
                )
            }
        }
    }
}

private fun Offset.toFramePoint(width: Int, height: Int, bitmap: Bitmap): Offset = Offset(
    x = (x * bitmap.width / width).coerceIn(0f, bitmap.width.toFloat()),
    y = (y * bitmap.height / height).coerceIn(0f, bitmap.height.toFloat()),
)
