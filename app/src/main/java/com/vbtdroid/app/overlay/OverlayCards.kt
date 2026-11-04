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
 * Portrait frames get a compact left-side column; landscape frames get a bottom strip, so the
 * cards never become a tall pillar over the lifter. Every value is looked up through the same
 * [RepTimeline] helpers as the report, which keeps the burned-in rep and the bar trail in sync.
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
    private val chartRect = RectF()
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
    private var lastLayoutScale = 0f
    private var lastUiScale = 0f
    private var layout: List<Pair<CardKind, RectF>> = emptyList()

    fun draw(canvas: Canvas, width: Int, height: Int, tSec: Double, hudBottom: Float) {
        if (width <= 0 || height <= 0) return
        // Width drives the card scale, but capping with height keeps landscape strips sane on
        // 16:9 and 20:9 phones where width alone would make the text enormous.
        val uiScale = (min(width.toFloat(), height * 1.25f) / 1080f).coerceAtLeast(0.55f)
        ensureSizes(uiScale)
        if (width != lastWidth || height != lastHeight || lastHudBottom != hudBottom) {
            layout = buildLayout(width.toFloat(), height.toFloat(), uiScale, hudBottom)
            lastWidth = width
            lastHeight = height
            lastHudBottom = hudBottom
        }

        val active = RepTimeline.currentRepAt(result.repSlices, tSec)
            ?: RepTimeline.trailRepAt(result.repSlices, tSec)
        // In the gap after a rep the previous rep's cursor sits at its end; this mirrors the
        // trail rule and keeps the angle/velocity cards stable until the next rep starts.
        val cueSec = active?.let { tSec.coerceIn(it.startSec, it.endSec) } ?: tSec

        for ((kind, rect) in layout) {
            when (kind) {
                CardKind.MAIN -> drawMainCard(canvas, rect, active, uiScale)
                CardKind.VELOCITY -> drawVelocityCard(canvas, rect, active, cueSec, uiScale)
                CardKind.ANGLES -> drawAngleCard(canvas, rect, cueSec, uiScale)
                CardKind.BAR_PATH -> drawBarPathCard(canvas, rect, active, cueSec, uiScale)
            }
        }
    }

    private fun ensureSizes(uiScale: Float) {
        if (uiScale == lastUiScale) return
        lastUiScale = uiScale
        titlePaint.textSize = 18f * uiScale
        valuePaint.textSize = 46f * uiScale
        unitPaint.textSize = 16f * uiScale
        labelPaint.textSize = 12f * uiScale
        miniValuePaint.textSize = 20f * uiScale
        angleValuePaint.textSize = 30f * uiScale
        emptyPaint.textSize = 14f * uiScale
        sparkPaint.strokeWidth = max(1f, 2.2f * uiScale)
        axisPaint.strokeWidth = max(0.8f, 1.0f * uiScale)
        cursorPaint.strokeWidth = max(1f, 1.5f * uiScale)
        borderPaint.strokeWidth = max(0.8f, 1.0f * uiScale)
        barPathPaints.values.forEach { it.strokeWidth = max(1.2f, 2.5f * uiScale) }
    }

    private fun buildLayout(
        width: Float,
        height: Float,
        uiScale: Float,
        hudBottom: Float,
    ): List<Pair<CardKind, RectF>> {
        val includeAngles = result.analyzePose && result.frameAngles.isNotEmpty()
        val kinds = buildList {
            add(CardKind.MAIN)
            add(CardKind.VELOCITY)
            if (includeAngles) add(CardKind.ANGLES)
            add(CardKind.BAR_PATH)
        }
        val margin = 14f * uiScale
        if (width > height) {
            // Landscape: one bottom strip. The video subject usually lives in the upper 70%.
            val gap = 10f * uiScale
            val cardHeight = min(height * 0.30f, 260f * uiScale)
            val cardWidth = (width - margin * 2f - gap * (kinds.size - 1)) / kinds.size
            val top = height - margin - cardHeight
            return kinds.mapIndexed { index, kind ->
                val left = margin + index * (cardWidth + gap)
                kind to RectF(left, top, left + cardWidth, top + cardHeight)
            }
        }

        // Portrait: a narrow left column below the HUD.
        val gap = 8f * uiScale
        val columnWidth = min(width * 0.36f, 360f * uiScale)
        val heights = mapOf(
            CardKind.MAIN to 190f * uiScale,
            CardKind.VELOCITY to 110f * uiScale,
            CardKind.ANGLES to 105f * uiScale,
            CardKind.BAR_PATH to 160f * uiScale,
        )
        var top = hudBottom + gap
        return kinds.map { kind ->
            val cardHeight = heights.getValue(kind)
            val rect = RectF(margin, top, margin + columnWidth, top + cardHeight)
            top += cardHeight + gap
            kind to rect
        }
    }

    private fun drawMainCard(canvas: Canvas, rect: RectF, active: RepSlice?, uiScale: Float) {
        drawCardBackground(canvas, rect, uiScale)
        val pad = 10f * uiScale
        val titleBaseline = rect.top + pad + titlePaint.textSize
        canvas.drawText("MCV", rect.left + pad, titleBaseline, titlePaint)
        val repPosition = active?.let { slice ->
            result.repSlices.indexOfFirst { it.index == slice.index } + 1
        } ?: 0
        val repText = if (active == null || repPosition <= 0) {
            "NO REP"
        } else {
            "REP $repPosition/${result.repSlices.size}"
        }
        drawTextRight(canvas, repText, rect.right - pad, titleBaseline, titlePaint)

        val metric = active?.let { metricsByRep[it.index] }
        val valueBaseline = titleBaseline + 6f * uiScale + valuePaint.textSize
        val mcv = metric?.mcvMps?.takeIf { it > 0.0 && it.isFinite() }
        val mcvText = mcv?.let { format(it, 2) } ?: "\u2014"
        canvas.drawText(mcvText, rect.left + pad, valueBaseline, valuePaint)
        val valueWidth = valuePaint.measureText(mcvText)
        canvas.drawText("m/s", rect.left + pad + valueWidth + 5f * uiScale, valueBaseline, unitPaint)

        val barHeight = 22f * uiScale
        val barsTop = rect.bottom - pad - barHeight
        val miniLabelBaseline = valueBaseline + 8f * uiScale + labelPaint.textSize
        val miniValueBaseline = miniLabelBaseline + 3f * uiScale + miniValuePaint.textSize
        val cellWidth = (rect.width() - pad * 2f) / 3f
        val summary = result.summary
        drawMiniCell(
            canvas,
            "AVG",
            summary.mcvAvgMps?.takeIf { it.isFinite() }?.let { format(it, 2) } ?: "\u2014",
            rect.left + pad,
            miniLabelBaseline,
            miniValueBaseline,
        )
        drawMiniCell(
            canvas,
            "BEST",
            summary.bestMcvMps?.takeIf { it.isFinite() }?.let { format(it, 2) } ?: "\u2014",
            rect.left + pad + cellWidth,
            miniLabelBaseline,
            miniValueBaseline,
        )
        drawMiniCell(
            canvas,
            "VL",
            summary.velocityLossPct?.takeIf { it.isFinite() }?.let { format(it, 1) + "%" } ?: "\u2014",
            rect.left + pad + cellWidth * 2f,
            miniLabelBaseline,
            miniValueBaseline,
        )

        drawRepBars(canvas, rect, pad, barsTop, barHeight, active)
    }

    private fun drawMiniCell(
        canvas: Canvas,
        label: String,
        value: String,
        left: Float,
        labelBaseline: Float,
        valueBaseline: Float,
    ) {
        canvas.drawText(label, left, labelBaseline, labelPaint)
        canvas.drawText(value, left, valueBaseline, miniValuePaint)
    }

    /** One slim bar per rep, current rep mint, best rep amber, the rest muted. */
    private fun drawRepBars(
        canvas: Canvas,
        rect: RectF,
        pad: Float,
        top: Float,
        barHeight: Float,
        active: RepSlice?,
    ) {
        val reps = result.reps
        if (reps.isEmpty()) return
        val maxMcv = reps.maxOfOrNull { it.mcvMps }?.takeIf { it > 0.0 } ?: return
        val bestIndex = reps.firstOrNull { it.mcvMps == maxMcv }?.repIndex
        val gap = 2f * lastUiScale
        val available = rect.width() - pad * 2f
        val barWidth = ((available - gap * (reps.size - 1)) / reps.size).coerceAtLeast(1f)
        reps.forEachIndexed { index, rep ->
            val ratio = (rep.mcvMps / maxMcv).coerceIn(0.0, 1.0).toFloat()
            val height = (barHeight * ratio).coerceAtLeast(2f * lastUiScale)
            val left = rect.left + pad + index * (barWidth + gap)
            barRect.set(left, top + barHeight - height, left + barWidth, top + barHeight)
            barPaint.color = when {
                rep.repIndex == active?.index -> OverlayStyle.TRAIL.toInt()
                rep.repIndex == bestIndex -> OverlayStyle.SPEED.toInt()
                else -> (OverlayStyle.PAUSE.toInt() and 0x00FFFFFF) or (110 shl 24)
            }
            canvas.drawRoundRect(barRect, 2f * lastUiScale, 2f * lastUiScale, barPaint)
        }
    }

    private fun drawVelocityCard(
        canvas: Canvas,
        rect: RectF,
        active: RepSlice?,
        cueSec: Double,
        uiScale: Float,
    ) {
        drawCardBackground(canvas, rect, uiScale)
        val pad = 10f * uiScale
        val titleBaseline = rect.top + pad + titlePaint.textSize
        canvas.drawText("VELOCITY", rect.left + pad, titleBaseline, titlePaint)
        val rep = active
        rep?.let {
            drawTextRight(canvas, format(metricsByRep[it.index]?.mcvMps ?: 0.0, 2), rect.right - pad, titleBaseline, titlePaint)
        }

        val chartTop = titleBaseline + 6f * uiScale
        val chartLeft = rect.left + pad
        val chartWidth = rect.width() - pad * 2f
        val chartHeight = rect.bottom - pad - chartTop
        val samples = rep?.let { velocityByRep[it.index] }.orEmpty()
        if (rep == null || samples.size < 2 || chartHeight <= 1f) {
            drawEmpty(canvas, rect)
            return
        }

        var minVelocity = min(0.0, samples.minOf { it.vyMps })
        var maxVelocity = max(0.0, samples.maxOf { it.vyMps })
        if (absSpan(minVelocity, maxVelocity) < 1e-9) {
            minVelocity -= 0.5
            maxVelocity += 0.5
        }
        val span = maxVelocity - minVelocity
        fun yFor(velocity: Double): Float =
            chartTop + chartHeight * (1f - ((velocity - minVelocity) / span).toFloat())

        if (minVelocity <= 0.0 && maxVelocity >= 0.0) {
            val zeroY = yFor(0.0)
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
            val y = yFor(sample.vyMps)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, sparkPaint)

        val cursorX = overlayTimeToX(cueSec, rep.startSec, rep.endSec, chartLeft, chartWidth)
        if (cursorX != null) {
            canvas.drawLine(cursorX, chartTop, cursorX, chartTop + chartHeight, cursorPaint)
        }
    }

    private fun drawAngleCard(canvas: Canvas, rect: RectF, cueSec: Double, uiScale: Float) {
        drawCardBackground(canvas, rect, uiScale)
        val pad = 10f * uiScale
        val titleBaseline = rect.top + pad + titlePaint.textSize
        canvas.drawText("ANGLES", rect.left + pad, titleBaseline, titlePaint)

        val angles = OverlayTimeline.nearestFrameAngles(result.frameAngles, cueSec)
        val entries = angleEntries(angles)
        if (entries.isEmpty()) {
            drawEmpty(canvas, rect)
            return
        }
        val cellWidth = (rect.width() - pad * 2f) / entries.size
        val labelBaseline = titleBaseline + 6f * uiScale + labelPaint.textSize
        val valueBaseline = labelBaseline + 4f * uiScale + angleValuePaint.textSize
        val lowConfidence = (angles?.confidence ?: 0.0) < 0.6
        angleValuePaint.color = if (lowConfidence) {
            OverlayStyle.SPEED.toInt()
        } else {
            OverlayStyle.CARD_TEXT.toInt()
        }
        entries.forEachIndexed { index, (label, value) ->
            val centerX = rect.left + pad + cellWidth * (index + 0.5f)
            canvas.drawText(label, centerX, labelBaseline, labelPaint.apply { textAlign = Paint.Align.CENTER })
            val text = value?.takeIf { it.isFinite() }?.let { format(it, 0) + "\u00B0" } ?: "\u2014"
            canvas.drawText(text, centerX, valueBaseline, angleValuePaint)
        }
        labelPaint.textAlign = Paint.Align.LEFT
    }

    private fun angleEntries(angles: FrameAngles?): List<Pair<String, Double?>> = when (result.lift) {
        Lift.SQUAT, Lift.DEADLIFT -> listOf(
            "HIP" to angles?.hipDeg,
            "KNEE" to angles?.kneeDeg,
            "ANKLE" to angles?.ankleDeg,
        )

        Lift.BENCH -> listOf(
            "SHOULDER" to angles?.shoulderDeg,
            "ELBOW" to angles?.elbowDeg,
        )
    }

    private fun drawBarPathCard(
        canvas: Canvas,
        rect: RectF,
        active: RepSlice?,
        cueSec: Double,
        uiScale: Float,
    ) {
        drawCardBackground(canvas, rect, uiScale)
        val pad = 10f * uiScale
        val titleBaseline = rect.top + pad + titlePaint.textSize
        canvas.drawText("BAR PATH", rect.left + pad, titleBaseline, titlePaint)
        val bounds = active?.let { barBoundsByRep[it.index] }
        if (active == null || bounds == null) {
            drawEmpty(canvas, rect)
            return
        }

        val chartLeft = rect.left + pad
        val chartTop = titleBaseline + 6f * uiScale
        val chartWidth = rect.width() - pad * 2f
        val chartHeight = rect.bottom - pad - chartTop
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
            chartLeft + fit.x(sample.xM),
            chartTop + fit.y(sample.yM),
        )

        // Same rule as the report: in the gap after a rep the previous rep's full trail stays.
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

    private fun drawEmpty(canvas: Canvas, rect: RectF) {
        canvas.drawText(
            "\u2014",
            rect.centerX(),
            rect.centerY() - (emptyPaint.ascent() + emptyPaint.descent()) / 2f,
            emptyPaint,
        )
    }

    private fun drawTextRight(canvas: Canvas, text: String, right: Float, baseline: Float, paint: Paint) {
        val previous = paint.textAlign
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(text, right, baseline, paint)
        paint.textAlign = previous
    }

    private fun format(value: Double, decimals: Int): String =
        String.format(Locale.US, "%.${decimals}f", value)

    private fun absSpan(min: Double, max: Double): Double = max - min

    private companion object {
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
