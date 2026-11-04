package com.vbtdroid.app.overlay

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import com.vbtdroid.app.analysis.AnalysisResult
import com.vbtdroid.app.analysis.FrameAngles
import com.vbtdroid.core.metrics.MotionSample
import com.vbtdroid.core.metrics.RepMetrics
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.phases.PhaseType
import com.vbtdroid.core.phases.RepSlice
import com.vbtdroid.core.phases.RepTimeline
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Arcus-style data cards burned into the overlay video.
 *
 * Portrait frames get one measured left column below the HUD; landscape frames get an equal-gap
 * bottom strip. Heights come from the same text metrics the card draws with, and when the column
 * does not fit the secondary metrics compact and the whole column scales down. Every card is
 * clipped to its own rounded rect, so text and charts can never spill into a neighbour.
 *
 * The renderer is allocated once per export. Layout rectangles and per-rep sample slices are
 * cached, so the hot path only builds one [Path] per chart per frame.
 */
internal class OverlayCards(private val result: AnalysisResult) {
    private enum class CardKind { MAIN, VELOCITY, ANGLES, BAR_PATH }

    private data class BarBounds(
        val minX: Double,
        val maxX: Double,
        val minY: Double,
        val maxY: Double,
    )

    private data class LayoutResult(
        val slots: List<Pair<CardKind, RectF>>,
        val contentScale: Float,
        val compact: Boolean,
    )

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = OverlayStyle.CARD_BACKGROUND.toInt()
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = OverlayStyle.CARD_BORDER.toInt()
    }
    private val titlePaint = textPaint(OverlayStyle.CARD_MUTED, Paint.Align.LEFT)
    private val valuePaint = textPaint(OverlayStyle.TRAIL, Paint.Align.LEFT).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val unitPaint = textPaint(OverlayStyle.CARD_MUTED, Paint.Align.LEFT)
    private val labelPaint = textPaint(OverlayStyle.CARD_MUTED, Paint.Align.LEFT)
    private val miniValuePaint = textPaint(OverlayStyle.CARD_TEXT, Paint.Align.LEFT)
    private val angleLabelPaint = textPaint(OverlayStyle.CARD_MUTED, Paint.Align.CENTER)
    private val angleValuePaint = textPaint(OverlayStyle.CARD_TEXT, Paint.Align.CENTER)
    private val emptyPaint = textPaint(OverlayStyle.CARD_MUTED, Paint.Align.CENTER)
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = OverlayStyle.TRAIL.toInt()
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = OverlayStyle.PAUSE.toInt()
        alpha = 90
    }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = OverlayStyle.SPEED.toInt()
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = OverlayStyle.TRAIL.toInt()
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val path = Path()
    private val clipPath = Path()
    private val barRect = RectF()

    private val barPathPaints: Map<PhaseType?, Paint> = mapOf(
        PhaseType.ECCENTRIC to chartPaint(OverlayStyle.ECCENTRIC),
        PhaseType.CONCENTRIC to chartPaint(OverlayStyle.TRAIL),
        PhaseType.PAUSE to chartPaint(OverlayStyle.PAUSE, alpha = 180),
        PhaseType.LOCKOUT to chartPaint(OverlayStyle.LOCKOUT, alpha = 180),
        null to chartPaint(OverlayStyle.PAUSE, alpha = 120),
    )

    private val metricsByRep: Map<Int, RepMetrics> = result.reps.associateBy { it.repIndex }
    private val velocityByRep: Map<Int, List<MotionSample>> = result.repSlices.associate { rep ->
        rep.index to result.motionSamples.filter { it.tSec >= rep.startSec && it.tSec <= rep.endSec }
    }
    private val barBoundsByRep: Map<Int, BarBounds?> = velocityByRep.mapValues { (_, samples) ->
        if (samples.size < 2) {
            null
        } else {
            BarBounds(
                minX = samples.minOf { it.xM },
                maxX = samples.maxOf { it.xM },
                minY = samples.minOf { it.yM },
                maxY = samples.maxOf { it.yM },
            )
        }
    }

    private var lastWidth = 0
    private var lastHeight = 0
    private var lastHudBottom = 0f
    private var lastUiScale = 0f
    private var layout = LayoutResult(emptyList(), 0f, false)

    fun draw(canvas: Canvas, width: Int, height: Int, tSec: Double, hudBottom: Float) {
        if (width <= 0 || height <= 0) return
        // Width drives the card scale, but capping with height keeps landscape strips sane on
        // 16:9 and 20:9 phones where width alone would make the text enormous.
        val baseScale = (min(width.toFloat(), height * 1.25f) / 1080f).coerceAtLeast(0.55f)
        if (width != lastWidth || height != lastHeight || lastHudBottom != hudBottom) {
            layout = buildLayout(width.toFloat(), height.toFloat(), baseScale, hudBottom)
            lastWidth = width
            lastHeight = height
            lastHudBottom = hudBottom
        }
        val uiScale = layout.contentScale.takeIf { it > 0f } ?: baseScale
        ensureSizes(uiScale)

        val active = RepTimeline.currentRepAt(result.repSlices, tSec)
            ?: RepTimeline.trailRepAt(result.repSlices, tSec)
        // In the gap after a rep the previous rep's cursor sits at its end; this mirrors the
        // trail rule and keeps the angle/velocity cards stable until the next rep starts.
        val cueSec = active?.let { tSec.coerceIn(it.startSec, it.endSec) } ?: tSec

        for ((kind, rect) in layout.slots) {
            drawCardBackground(canvas, rect, uiScale)
            clippedToCard(canvas, rect, 12f * uiScale) {
                when (kind) {
                    CardKind.MAIN -> drawMainCard(canvas, rect, active, uiScale, layout.compact)
                    CardKind.VELOCITY -> drawVelocityCard(canvas, rect, active, cueSec, uiScale)
                    CardKind.ANGLES -> drawAngleCard(canvas, rect, cueSec, uiScale)
                    CardKind.BAR_PATH -> drawBarPathCard(canvas, rect, active, cueSec, uiScale)
                }
            }
        }
    }

    private fun ensureSizes(uiScale: Float) {
        if (uiScale == lastUiScale) return
        lastUiScale = uiScale
        titlePaint.textSize = TITLE * uiScale
        valuePaint.textSize = VALUE * uiScale
        unitPaint.textSize = 16f * uiScale
        labelPaint.textSize = LABEL * uiScale
        miniValuePaint.textSize = MINI_VALUE * uiScale
        angleLabelPaint.textSize = LABEL * uiScale
        angleValuePaint.textSize = ANGLE_VALUE * uiScale
        emptyPaint.textSize = 14f * uiScale
        sparkPaint.strokeWidth = max(1f, 2.2f * uiScale)
        axisPaint.strokeWidth = max(0.8f, 1.0f * uiScale)
        cursorPaint.strokeWidth = max(1f, 1.5f * uiScale)
        borderPaint.strokeWidth = max(0.8f, 1.0f * uiScale)
        barPathPaints.values.forEach { it.strokeWidth = max(1.2f, 2.5f * uiScale) }
    }

    /**
     * Explicit non-overlapping layout. Heights are derived from the paint sizes and only change
     * when the column has to compact or shrink, never by letting rectangles drift over each other.
     */
    private fun buildLayout(
        width: Float,
        height: Float,
        baseScale: Float,
        hudBottom: Float,
    ): LayoutResult {
        val includeAngles = result.analyzePose && result.frameAngles.isNotEmpty()
        val kinds = buildList {
            add(CardKind.MAIN)
            add(CardKind.VELOCITY)
            if (includeAngles) add(CardKind.ANGLES)
            add(CardKind.BAR_PATH)
        }
        val margin = 14f * baseScale
        if (width > height) {
            // Landscape: one bottom strip, equal gaps, equal card widths.
            val gap = 10f * baseScale
            val cardHeight = min(height * 0.30f, 260f * baseScale).coerceAtLeast(1f)
            val cardWidth = (
                (width - margin * 2f - gap * (kinds.size - 1)) / kinds.size
                ).coerceAtLeast(1f)
            val top = (height - margin - cardHeight).coerceAtLeast(0f)
            val compact = cardHeight < cardHeight(CardKind.MAIN, baseScale, compact = false)
            val slots = kinds.mapIndexed { index, kind ->
                val left = (margin + index * (cardWidth + gap)).coerceAtLeast(0f)
                kind to RectF(left, top, left + cardWidth, top + cardHeight)
            }
            return LayoutResult(slots, baseScale, compact)
        }

        // Portrait: a single left column. Reserve the HUD area and a bottom safe margin; if the
        // measured column is too tall, compact secondary metrics first, then scale the whole card
        // block so the last card always ends above the safe margin.
        val gap = 8f * baseScale
        val top = (hudBottom + gap).coerceAtLeast(margin)
        val columnWidth = min(width * 0.36f, 360f * baseScale).coerceAtLeast(1f)
        val available = (height - top - margin).coerceAtLeast(1f)
        var compact = false
        var heights = kinds.map { cardHeight(it, baseScale, compact = false) }
        var total = heights.sum() + gap * (kinds.size - 1)
        if (total > available) {
            compact = true
            heights = kinds.map { cardHeight(it, baseScale, compact = true) }
            total = heights.sum() + gap * (kinds.size - 1)
        }
        var contentScale = baseScale
        if (total > available) {
            val ratio = (available / total).coerceIn(0.25f, 1f)
            contentScale = (baseScale * ratio).coerceAtLeast(0.25f)
            heights = kinds.map { cardHeight(it, contentScale, compact) }
            total = heights.sum() + gap * (kinds.size - 1)
        }
        var layoutGap = gap
        if (layoutGap * (kinds.size - 1) >= available) layoutGap = 0f
        val heightBudget = (available - layoutGap * (kinds.size - 1)).coerceAtLeast(1f)
        var heightSum = heights.sum()
        if (heightSum > heightBudget) {
            val squeeze = (heightBudget / heightSum).coerceAtLeast(0.01f)
            heights = heights.map { (it * squeeze).coerceAtLeast(0.5f) }
            heightSum = heights.sum()
        }
        if (heightSum > heightBudget) {
            val squeeze = (heightBudget / heightSum).coerceAtLeast(0.01f)
            heights = heights.map { (it * squeeze).coerceAtLeast(0.5f) }
        }

        val slots = ArrayList<Pair<CardKind, RectF>>(kinds.size)
        var cursor = top
        kinds.forEachIndexed { index, kind ->
            val cardHeight = heights[index].coerceAtLeast(0.5f)
            slots += kind to RectF(
                margin.coerceAtLeast(0f),
                cursor.coerceAtLeast(0f),
                (margin + columnWidth).coerceAtLeast(1f),
                (cursor + cardHeight).coerceAtLeast(1f),
            )
            cursor += cardHeight + layoutGap
        }
        return LayoutResult(slots, contentScale, compact)
    }

    /** Measured card height, in the same units as [ensureSizes]. */
    private fun cardHeight(kind: CardKind, uiScale: Float, compact: Boolean): Float {
        val pad = 10f * uiScale
        val title = TITLE * uiScale
        val gap = 6f * uiScale
        return when (kind) {
            CardKind.MAIN -> {
                val mini = if (compact) {
                    MINI_VALUE * uiScale
                } else {
                    LABEL * uiScale + 3f * uiScale + MINI_VALUE * uiScale
                }
                val bars = (if (compact) 16f else 22f) * uiScale
                pad + title + gap + VALUE * uiScale + gap + mini + gap + bars + pad
            }

            CardKind.VELOCITY -> pad + title + gap + 40f * uiScale + pad
            CardKind.ANGLES -> pad + title + gap + LABEL * uiScale + 4f * uiScale +
                ANGLE_VALUE * uiScale + pad

            CardKind.BAR_PATH -> pad + title + gap + 80f * uiScale + pad
        }
    }

    private fun drawMainCard(
        canvas: Canvas,
        rect: RectF,
        active: RepSlice?,
        uiScale: Float,
        compact: Boolean,
    ) {
        val pad = 10f * uiScale
        val titleBaseline = rect.top + pad + titlePaint.textSize
        val contentWidth = (rect.width() - pad * 2f).coerceAtLeast(1f)
        drawTextFitted(canvas, "MCV", rect.left + pad, titleBaseline, titlePaint, contentWidth)
        val repPosition = active?.let { slice ->
            result.repSlices.indexOfFirst { it.index == slice.index } + 1
        } ?: 0
        val repText = if (active == null || repPosition <= 0) {
            "NO REP"
        } else {
            "REP $repPosition/${result.repSlices.size}"
        }
        val repWidth = (contentWidth - titlePaint.measureText("MCV") - 8f * uiScale)
            .coerceAtLeast(1f)
        drawTextRightFitted(canvas, repText, rect.right - pad, titleBaseline, titlePaint, repWidth)

        val metric = active?.let { metricsByRep[it.index] }
        val valueBaseline = titleBaseline + 6f * uiScale + valuePaint.textSize
        val mcv = metric?.mcvMps?.takeIf { it > 0.0 && it.isFinite() }
        val mcvText = mcv?.let { format(it, 2) } ?: "\u2014"
        val unitWidth = unitPaint.measureText("m/s") + 8f * uiScale
        val valueWidth = (contentWidth - unitWidth).coerceAtLeast(1f)
        drawTextFitted(canvas, mcvText, rect.left + pad, valueBaseline, valuePaint, valueWidth)
        val drawnWidth = min(valuePaint.measureText(mcvText), valueWidth)
        drawTextFitted(
            canvas,
            "m/s",
            rect.left + pad + drawnWidth + 5f * uiScale,
            valueBaseline,
            unitPaint,
            (rect.right - pad - (rect.left + pad + drawnWidth + 5f * uiScale))
                .coerceAtLeast(1f),
        )

        val barHeight = (if (compact) 16f else 22f) * uiScale
        val barsTop = rect.bottom - pad - barHeight
        val summary = result.summary
        val avgText = summary.mcvAvgMps?.takeIf { it.isFinite() }?.let { format(it, 2) } ?: "\u2014"
        val bestText = summary.bestMcvMps?.takeIf { it.isFinite() }?.let { format(it, 2) } ?: "\u2014"
        val vlText = summary.velocityLossPct?.takeIf { it.isFinite() }
            ?.let { format(it, 1) + "%" } ?: "\u2014"
        if (compact) {
            val summaryText = "AVG $avgText   BEST $bestText   VL $vlText"
            val baseline = valueBaseline + 6f * uiScale + miniValuePaint.textSize
            drawTextFitted(
                canvas,
                summaryText,
                rect.left + pad,
                baseline,
                miniValuePaint,
                contentWidth,
            )
        } else {
            val miniLabelBaseline = valueBaseline + 6f * uiScale + labelPaint.textSize
            val miniValueBaseline = miniLabelBaseline + 3f * uiScale + miniValuePaint.textSize
            val cellWidth = contentWidth / 3f
            drawMiniCell(
                canvas,
                "AVG",
                avgText,
                rect.left + pad,
                miniLabelBaseline,
                miniValueBaseline,
                cellWidth,
            )
            drawMiniCell(
                canvas,
                "BEST",
                bestText,
                rect.left + pad + cellWidth,
                miniLabelBaseline,
                miniValueBaseline,
                cellWidth,
            )
            drawMiniCell(
                canvas,
                "VL",
                vlText,
                rect.left + pad + cellWidth * 2f,
                miniLabelBaseline,
                miniValueBaseline,
                cellWidth,
            )
        }

        drawRepBars(canvas, rect, pad, barsTop, barHeight, active, uiScale)
    }

    private fun drawMiniCell(
        canvas: Canvas,
        label: String,
        value: String,
        left: Float,
        labelBaseline: Float,
        valueBaseline: Float,
        cellWidth: Float,
    ) {
        val maxWidth = (cellWidth - 4f * lastUiScale).coerceAtLeast(1f)
        drawTextFitted(canvas, label, left, labelBaseline, labelPaint, maxWidth)
        drawTextFitted(canvas, value, left, valueBaseline, miniValuePaint, maxWidth)
    }

    /** One slim bar per rep, current rep mint, best rep amber, the rest muted. */
    private fun drawRepBars(
        canvas: Canvas,
        rect: RectF,
        pad: Float,
        top: Float,
        barHeight: Float,
        active: RepSlice?,
        uiScale: Float,
    ) {
        val reps = result.reps
        if (reps.isEmpty()) return
        val maxMcv = reps.maxOfOrNull { it.mcvMps }?.takeIf { it > 0.0 } ?: return
        val bestIndex = reps.firstOrNull { it.mcvMps == maxMcv }?.repIndex
        val gap = 2f * uiScale
        val innerLeft = (rect.left + pad).coerceAtMost(rect.right).coerceAtLeast(rect.left)
        val innerRight = (rect.right - pad).coerceAtLeast(innerLeft).coerceAtMost(rect.right)
        val innerWidth = (innerRight - innerLeft).coerceAtLeast(1f)
        val barWidth = ((innerWidth - gap * (reps.size - 1)) / reps.size).coerceAtLeast(1f)
        reps.forEachIndexed { index, rep ->
            val ratio = (rep.mcvMps / maxMcv).coerceIn(0.0, 1.0).toFloat()
            val height = (barHeight * ratio).coerceAtLeast(2f * uiScale)
            val left = (innerLeft + index * (barWidth + gap)).coerceIn(innerLeft, innerRight)
            barRect.set(
                left,
                (top + barHeight - height).coerceAtLeast(rect.top),
                (left + barWidth).coerceAtMost(innerRight),
                (top + barHeight).coerceAtMost(rect.bottom),
            )
            if (barRect.width() <= 0f || barRect.height() <= 0f) return@forEachIndexed
            barPaint.color = when {
                rep.repIndex == active?.index -> OverlayStyle.TRAIL.toInt()
                rep.repIndex == bestIndex -> OverlayStyle.SPEED.toInt()
                else -> (OverlayStyle.PAUSE.toInt() and 0x00FFFFFF) or (110 shl 24)
            }
            canvas.drawRoundRect(barRect, 2f * uiScale, 2f * uiScale, barPaint)
        }
    }

    private fun drawVelocityCard(
        canvas: Canvas,
        rect: RectF,
        active: RepSlice?,
        cueSec: Double,
        uiScale: Float,
    ) {
        val pad = 10f * uiScale
        val titleBaseline = rect.top + pad + titlePaint.textSize
        val contentWidth = (rect.width() - pad * 2f).coerceAtLeast(1f)
        drawTextFitted(canvas, "VELOCITY", rect.left + pad, titleBaseline, titlePaint, contentWidth)
        val rep = active
        val repValue = rep?.let { metricsByRep[it.index]?.mcvMps }?.takeIf { it > 0.0 }
        val titleWidth = titlePaint.measureText("VELOCITY")
        if (repValue != null) {
            drawTextRightFitted(
                canvas,
                format(repValue, 2),
                rect.right - pad,
                titleBaseline,
                titlePaint,
                (contentWidth - titleWidth - 8f * uiScale).coerceAtLeast(1f),
            )
        }

        val chartTop = titleBaseline + 6f * uiScale
        val chartLeft = rect.left + pad
        val chartWidth = contentWidth
        val chartHeight = (rect.bottom - pad - chartTop).coerceAtLeast(0f)
        val samples = rep?.let { velocityByRep[it.index] }.orEmpty()
        if (rep == null || samples.size < 2 || chartHeight <= 1f) {
            drawEmpty(canvas, rect)
            return
        }

        var minVelocity = min(0.0, samples.minOf { it.vyMps })
        var maxVelocity = max(0.0, samples.maxOf { it.vyMps })
        if (maxVelocity - minVelocity < 1e-9) {
            minVelocity -= 0.5
            maxVelocity += 0.5
        }
        val span = maxVelocity - minVelocity
        fun yFor(velocity: Double): Float =
            chartTop + chartHeight * (1f - ((velocity - minVelocity) / span).toFloat())

        if (minVelocity <= 0.0 && maxVelocity >= 0.0) {
            val zeroY = yFor(0.0).coerceIn(chartTop, chartTop + chartHeight)
            canvas.drawLine(chartLeft, zeroY, chartLeft + chartWidth, zeroY, axisPaint)
        }

        path.reset()
        samples.forEachIndexed { index, sample ->
            val x = overlayTimeToX(
                sample.tSec,
                rep.startSec,
                rep.endSec,
                chartLeft,
                chartWidth,
            ) ?: return@forEachIndexed
            val y = yFor(sample.vyMps).coerceIn(chartTop, chartTop + chartHeight)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, sparkPaint)

        // Only the velocity sparkline keeps a playback cursor.
        val cursorX = overlayTimeToX(cueSec, rep.startSec, rep.endSec, chartLeft, chartWidth)
        if (cursorX != null) {
            canvas.drawLine(
                cursorX.coerceIn(chartLeft, chartLeft + chartWidth),
                chartTop,
                cursorX.coerceIn(chartLeft, chartLeft + chartWidth),
                chartTop + chartHeight,
                cursorPaint,
            )
        }
    }

    private fun drawAngleCard(canvas: Canvas, rect: RectF, cueSec: Double, uiScale: Float) {
        val pad = 10f * uiScale
        val titleBaseline = rect.top + pad + titlePaint.textSize
        val contentWidth = (rect.width() - pad * 2f).coerceAtLeast(1f)
        // Keep the user-facing legend/title.
        drawTextFitted(canvas, "姿态分析", rect.left + pad, titleBaseline, titlePaint, contentWidth)

        val angles = OverlayTimeline.nearestFrameAngles(result.frameAngles, cueSec)
        val entries = angleEntries(angles)
        if (entries.isEmpty()) {
            drawEmpty(canvas, rect)
            return
        }
        val cellWidth = contentWidth / entries.size
        val labelBaseline = titleBaseline + 6f * uiScale + angleLabelPaint.textSize
        val valueBaseline = labelBaseline + 4f * uiScale + angleValuePaint.textSize
        val lowConfidence = (angles?.confidence ?: 0.0) < 0.6
        angleValuePaint.color = if (lowConfidence) {
            OverlayStyle.SPEED.toInt()
        } else {
            OverlayStyle.CARD_TEXT.toInt()
        }
        val maxTextWidth = (cellWidth - 4f * uiScale).coerceAtLeast(1f)
        entries.forEachIndexed { index, (label, value) ->
            val centerX = rect.left + pad + cellWidth * (index + 0.5f)
            drawTextFitted(canvas, label, centerX, labelBaseline, angleLabelPaint, maxTextWidth)
            val text = value?.takeIf { it.isFinite() }?.let { format(it, 0) + "\u00B0" } ?: "\u2014"
            drawTextFitted(canvas, text, centerX, valueBaseline, angleValuePaint, maxTextWidth)
        }
    }

    private fun angleEntries(angles: FrameAngles?): List<Pair<String, Double?>> = when (result.lift) {
        Lift.SQUAT -> listOf(
            "背角" to angles?.trunkDeg,
            "髋角" to angles?.hipDeg,
            "膝角" to angles?.kneeDeg,
            "踝角" to angles?.ankleDeg,
        )

        Lift.DEADLIFT -> listOf(
            "背角" to angles?.trunkDeg,
            "髋角" to angles?.hipDeg,
            "膝角" to angles?.kneeDeg,
        )

        Lift.BENCH -> listOf(
            "肩角" to angles?.shoulderDeg,
            "肘角" to angles?.elbowDeg,
        )
    }

    private fun drawBarPathCard(
        canvas: Canvas,
        rect: RectF,
        active: RepSlice?,
        cueSec: Double,
        uiScale: Float,
    ) {
        val pad = 10f * uiScale
        val titleBaseline = rect.top + pad + titlePaint.textSize
        val contentWidth = (rect.width() - pad * 2f).coerceAtLeast(1f)
        drawTextFitted(canvas, "BAR PATH", rect.left + pad, titleBaseline, titlePaint, contentWidth)
        val bounds = active?.let { barBoundsByRep[it.index] }
        if (active == null || bounds == null) {
            drawEmpty(canvas, rect)
            return
        }

        val chartLeft = rect.left + pad
        val chartTop = titleBaseline + 6f * uiScale
        val chartWidth = contentWidth
        val chartHeight = (rect.bottom - pad - chartTop).coerceAtLeast(0f)
        if (chartWidth <= 1f || chartHeight <= 1f) return
        val fit = fitOverlayBarPath(
            minX = bounds.minX,
            maxX = bounds.maxX,
            minY = bounds.minY,
            maxY = bounds.maxY,
            widthPx = chartWidth,
            heightPx = chartHeight,
        )
        // Equal scale keeps the path honest; the bar path card letterboxes instead of stretching.
        fun point(sample: MotionSample): PointF = PointF(
            (chartLeft + fit.x(sample.xM)).coerceIn(chartLeft, chartLeft + chartWidth),
            (chartTop + fit.y(sample.yM)).coerceIn(chartTop, chartTop + chartHeight),
        )

        // Same rule as the report: in the gap after a rep the previous rep's full trail stays.
        // The trail advances with the frame; this card deliberately has no playback cursor.
        val runs = RepTimeline.trailRuns(result.motionSamples, active, cueSec)
        var lastPoint: PointF? = null
        for (run in runs) {
            if (run.samples.size < 2) continue
            path.reset()
            run.samples.forEachIndexed { index, sample ->
                val p = point(sample)
                if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            canvas.drawPath(path, barPathPaints.getValue(run.type))
            lastPoint = point(run.samples.last())
        }
        lastPoint?.let {
            canvas.drawCircle(it.x, it.y, 3.2f * uiScale, dotPaint)
        }
    }

    private fun drawCardBackground(canvas: Canvas, rect: RectF, uiScale: Float) {
        val radius = 12f * uiScale
        canvas.drawRoundRect(rect, radius, radius, cardPaint)
        canvas.drawRoundRect(rect, radius, radius, borderPaint)
    }

    private inline fun clippedToCard(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        block: () -> Unit,
    ) {
        val safeRadius = min(radius, min(rect.width(), rect.height()) / 2f).coerceAtLeast(0f)
        canvas.save()
        clipPath.reset()
        clipPath.addRoundRect(rect, safeRadius, safeRadius, Path.Direction.CW)
        canvas.clipPath(clipPath)
        try {
            block()
        } finally {
            canvas.restore()
        }
    }

    private fun drawEmpty(canvas: Canvas, rect: RectF) {
        val centerY = rect.centerY() - (emptyPaint.ascent() + emptyPaint.descent()) / 2f
        canvas.drawText(
            "\u2014",
            rect.centerX(),
            centerY.coerceIn(rect.top, rect.bottom),
            emptyPaint,
        )
    }

    /** Draws text scaled horizontally when needed so it always stays inside its card/cell. */
    private fun drawTextFitted(
        canvas: Canvas,
        text: String,
        x: Float,
        baseline: Float,
        paint: Paint,
        maxWidth: Float,
    ) {
        val textWidth = paint.measureText(text)
        if (textWidth <= maxWidth || textWidth <= 0f || maxWidth <= 0f) {
            canvas.drawText(text, x, baseline, paint)
            return
        }
        val previousScale = paint.textScaleX
        paint.textScaleX = (maxWidth / textWidth).coerceAtLeast(0.1f)
        try {
            canvas.drawText(text, x, baseline, paint)
        } finally {
            paint.textScaleX = previousScale
        }
    }

    private fun drawTextRightFitted(
        canvas: Canvas,
        text: String,
        right: Float,
        baseline: Float,
        paint: Paint,
        maxWidth: Float,
    ) {
        val previous = paint.textAlign
        paint.textAlign = Paint.Align.RIGHT
        try {
            drawTextFitted(canvas, text, right, baseline, paint, maxWidth)
        } finally {
            paint.textAlign = previous
        }
    }

    private fun format(value: Double, decimals: Int): String =
        String.format(Locale.US, "%.${decimals}f", value)

    private companion object {
        const val TITLE = 18f
        const val VALUE = 46f
        const val LABEL = 12f
        const val MINI_VALUE = 20f
        const val ANGLE_VALUE = 30f

        fun textPaint(color: Long, align: Paint.Align): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toInt()
            textAlign = align
        }

        fun chartPaint(color: Long, alpha: Int = 255): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = (color.toInt() and 0x00FFFFFF) or (alpha shl 24)
        }
    }
}
