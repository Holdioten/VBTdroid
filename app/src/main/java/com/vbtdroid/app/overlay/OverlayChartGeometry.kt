package com.vbtdroid.app.overlay

import kotlin.math.max
import kotlin.math.min

/**
 * Equal-scale bar-path fit used by the burned-in card.
 *
 * Both axes share one pixels-per-metre factor, so the path keeps its real shape instead of
 * stretching to fill the card. The shorter physical span is letterboxed. Pure geometry (no
 * Android types) so it can be unit tested and reused by any future overlay renderer.
 */
data class BarPathFit(
    val minX: Double,
    val minY: Double,
    val pxPerMetre: Float,
    val leftPx: Float,
    val bottomPx: Float,
) {
    fun x(xM: Double): Float = leftPx + ((xM - minX) * pxPerMetre).toFloat()

    fun y(yM: Double): Float = bottomPx - ((yM - minY) * pxPerMetre).toFloat()
}

/** Fits `[minX, maxX] x [minY, maxY]` into a card chart area while keeping one scale for both axes. */
fun fitOverlayBarPath(
    minX: Double,
    maxX: Double,
    minY: Double,
    maxY: Double,
    widthPx: Float,
    heightPx: Float,
    paddingPx: Float = 0f,
): BarPathFit {
    val usableWidth = max(1f, widthPx - paddingPx * 2f)
    val usableHeight = max(1f, heightPx - paddingPx * 2f)
    val xSpan = max(0.01, maxX - minX)
    val ySpan = max(0.01, maxY - minY)
    val pxPerMetre = min(usableWidth / xSpan.toFloat(), usableHeight / ySpan.toFloat())
        .takeIf { it.isFinite() && it > 0f }
        ?: 1f
    val drawnWidth = max(0.0, maxX - minX).toFloat() * pxPerMetre
    val drawnHeight = max(0.0, maxY - minY).toFloat() * pxPerMetre
    return BarPathFit(
        minX = minX,
        minY = minY,
        pxPerMetre = pxPerMetre,
        leftPx = paddingPx + (usableWidth - drawnWidth) / 2f,
        bottomPx = paddingPx + (usableHeight + drawnHeight) / 2f,
    )
}

/**
 * Maps a time to an x position inside `[startSec, endSec]`. Returns null outside the rep so a cursor
 * cannot jump into a gap. Mirrors the report chart mapping so both previews agree.
 */
fun overlayTimeToX(
    tSec: Double,
    startSec: Double,
    endSec: Double,
    leftPx: Float,
    widthPx: Float,
): Float? {
    if (!tSec.isFinite() || !startSec.isFinite() || !endSec.isFinite()) return null
    val span = endSec - startSec
    if (span <= 0.0 || tSec < startSec || tSec > endSec) return null
    return leftPx + ((tSec - startSec) / span).toFloat() * widthPx
}
