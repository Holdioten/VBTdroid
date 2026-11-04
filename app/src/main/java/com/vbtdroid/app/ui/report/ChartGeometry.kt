package com.vbtdroid.app.ui.report

/**
 * Maps a time to an x position across a chart of [widthPx], with [paddingPx] on both sides.
 * Returns null when the span is empty or [tSec] is outside `[startSec, endSec]`, so a cursor is
 * only drawn while the playhead is inside the plotted range. Pure, so charts and tests share it.
 */
fun timeToX(
    tSec: Double,
    startSec: Double,
    endSec: Double,
    widthPx: Float,
    paddingPx: Float = 12f,
): Float? {
    if (!tSec.isFinite() || !startSec.isFinite() || !endSec.isFinite()) return null
    val span = endSec - startSec
    if (span <= 0.0 || tSec < startSec || tSec > endSec) return null
    val usable = widthPx - 2f * paddingPx
    if (usable <= 0f) return null
    return paddingPx + ((tSec - startSec) / span).toFloat() * usable
}
