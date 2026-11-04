package com.vbtdroid.app.ui.report

import kotlin.math.min

/**
 * Equal-unit mapping from physical bar coordinates (metres) to chart pixels.
 *
 * x and y share one [pxPerM] so a horizontal metre is never drawn wider than a vertical metre.
 * The plot is centred inside the card and letterboxed on the unused axis.
 */
internal data class BarPathTransform(
    val pxPerM: Float,
    val minXM: Double,
    val minYM: Double,
    val leftPx: Float,
    val topPx: Float,
) {
    fun x(xM: Double): Float = leftPx + ((xM - minXM) * pxPerM).toFloat()

    fun y(yM: Double, heightPx: Float): Float =
        heightPx - (topPx + ((yM - minYM) * pxPerM).toFloat())
}

/**
 * Fits [minX]..[maxX] × [minY]..[maxY] (metres) into a [widthPx] × [heightPx] card with
 * [paddingPx] on every side. Scale is the limiting axis, so the path keeps its physical aspect.
 */
internal fun fitBarPath(
    minX: Double,
    maxX: Double,
    minY: Double,
    maxY: Double,
    widthPx: Float,
    heightPx: Float,
    paddingPx: Float,
): BarPathTransform {
    val usableW = (widthPx - 2f * paddingPx).coerceAtLeast(1f)
    val usableH = (heightPx - 2f * paddingPx).coerceAtLeast(1f)
    val xSpan = (maxX - minX).takeIf { it > 1e-9 } ?: 1e-9
    val ySpan = (maxY - minY).takeIf { it > 1e-9 } ?: 1e-9
    val pxPerM = min(usableW / xSpan.toFloat(), usableH / ySpan.toFloat())
    val plotW = (xSpan * pxPerM).toFloat()
    val plotH = (ySpan * pxPerM).toFloat()
    return BarPathTransform(
        pxPerM = pxPerM,
        minXM = minX,
        minYM = minY,
        leftPx = paddingPx + (usableW - plotW) / 2f,
        topPx = paddingPx + (usableH - plotH) / 2f,
    )
}
