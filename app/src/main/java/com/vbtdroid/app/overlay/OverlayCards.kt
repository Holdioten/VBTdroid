package com.vbtdroid.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.vbtdroid.app.R
import com.vbtdroid.app.analysis.AnalysisResult
import com.vbtdroid.app.analysis.FrameAngles
import com.vbtdroid.core.metrics.MotionSample
import com.vbtdroid.core.metrics.RepMetrics
import com.vbtdroid.core.model.Lift
import com.vbtdroid.app.ui.report.phaseLabelRes
import com.vbtdroid.core.phases.Phase
import com.vbtdroid.core.phases.PhaseType
import com.vbtdroid.core.phases.RepSlice
import com.vbtdroid.core.phases.RepTimeline
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.min

/**
 * Burns the same cards the Compose report preview draws into the overlay video.
 *
 * Layout is measured from the report's dp/sp values: portrait gets a 36%-wide left column below
 * the HUD, landscape an equal-gap bottom strip. When the column is too tall the whole card block
 * scales down; rectangles never cross. Every card is clipped to its own rounded rect.
 *
 * Colours/labels come from the shared theme and string resources, so the burned video matches the
 * report style and has no duplicate English labels.
 */
internal class OverlayCards(private val context: Context, private val result: AnalysisResult) {
    private enum class CardKind { MAIN, VELOCITY, POSE, BAR_PATH, REP_BARS }

    private data class BarBounds(
        val minX: Double,
        val maxX: Double,
        val minY: Double,
        val maxY: Double,
    )

    private data class LayoutResult(
        val slots: List<Pair<CardKind, RectF>>,
        val contentScale: Float,
    )

    private data class JointSpec(
        val key: String,
        val label: String,
        val value: (FrameAngles) -> Double?,
    )

    private val mainTitle = context.getString(R.string.report_card_rep_mcv)
    private val setMeanLabel = context.getString(R.string.report_mini_set_mean)
    private val bestLabel = context.getString(R.string.report_mini_best_mcv)
    private val velocityLossLabel = context.getString(R.string.report_mini_vl)
    private val repCountLabel = context.getString(R.string.report_mini_reps)
    private val velocityTitle = context.getString(R.string.report_velocity_chart)
    private val poseTitle = context.getString(R.string.report_card_pose)
    private val poseOffText = context.getString(R.string.report_pose_off)
    private val poseUnavailableText = context.getString(R.string.report_pose_unavailable)
    private val barPathTitle = context.getString(R.string.report_bar_path_chart)
    private val repBarsTitle = context.getString(R.string.report_per_rep_mcv)
    private val unitMps = context.getString(R.string.unit_mps)
    private val missing = context.getString(R.string.report_value_missing)
    private val peakLabelFormat = context.getString(R.string.report_peak_velocity_label)
    private val angleFormat = context.getString(R.string.report_angle_value)

    private val poseJoints: List<JointSpec> = when (result.lift) {
        Lift.SQUAT -> listOf(
            JointSpec("trunk_angle_deg", context.getString(R.string.metric_trunk_angle)) { it.trunkDeg },
            JointSpec("hip_angle_deg", context.getString(R.string.metric_hip_angle)) { it.hipDeg },
            JointSpec("knee_angle_deg", context.getString(R.string.metric_knee_angle)) { it.kneeDeg },
            JointSpec("ankle_angle_deg", context.getString(R.string.metric_ankle_angle)) { it.ankleDeg },
        )

        Lift.DEADLIFT -> listOf(
            JointSpec("trunk_angle_deg", context.getString(R.string.metric_trunk_angle)) { it.trunkDeg },
            JointSpec("hip_angle_deg", context.getString(R.string.metric_hip_angle)) { it.hipDeg },
            JointSpec("knee_angle_deg", context.getString(R.string.metric_knee_angle)) { it.kneeDeg },
        )

        Lift.BENCH -> listOf(
            JointSpec("shoulder_angle_deg", context.getString(R.string.metric_shoulder_angle)) { it.shoulderDeg },
            JointSpec("elbow_angle_deg", context.getString(R.string.metric_elbow_angle)) { it.elbowDeg },
        )
    }

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = OverlayStyle.CARD_BACKGROUND.toInt()
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = OverlayStyle.CARD_BORDER.toInt()
    }
    private val titlePaint = textPaint(OverlayStyle.CARD_MUTED, Paint.Align.LEFT)
    private val heroPaint = textPaint(OverlayStyle.TRAIL, Paint.Align.LEFT).apply {
        typeface = ResourcesCompat.getFont(context, R.font.barlow_condensed_semibold)
            ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val unitPaint = textPaint(OverlayStyle.CARD_MUTED, Paint.Align.LEFT)
    private val labelPaint = textPaint(OverlayStyle.CARD_MUTED, Paint.Align.LEFT)
    private val valuePaint = textPaint(OverlayStyle.CARD_TEXT, Paint.Align.LEFT)
    private val emptyPaint = textPaint(OverlayStyle.CARD_MUTED, Paint.Align.CENTER)
    private val linePaint = chartPaint(OverlayStyle.TRAIL)
    private val axisPaint = chartPaint(OverlayStyle.CARD_BORDER).apply {
        strokeWidth = 1f
    }
    private val meanPaint = chartPaint(OverlayStyle.TRAIL).apply {
        alpha = 102
    }
    private val cursorPaint = chartPaint(OverlayStyle.CARD_TEXT).apply {
        alpha = 230
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = OverlayStyle.TRAIL.toInt()
    }
    private val dotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = OverlayStyle.TRAIL.toInt()
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = OverlayStyle.SKELETON.toInt()
    }
    private val bandPaints: Map<PhaseType, Paint> = mapOf(
        PhaseType.ECCENTRIC to bandPaint(OverlayStyle.ECCENTRIC),
        PhaseType.PAUSE to bandPaint(OverlayStyle.PAUSE),
        PhaseType.CONCENTRIC to bandPaint(OverlayStyle.TRAIL),
        PhaseType.LOCKOUT to bandPaint(OverlayStyle.LOCKOUT),
    )
    private val barPathPaints: Map<PhaseType?, Paint> = mapOf(
        PhaseType.ECCENTRIC to chartPaint(OverlayStyle.ECCENTRIC),
        PhaseType.CONCENTRIC to chartPaint(OverlayStyle.TRAIL),
        PhaseType.PAUSE to chartPaint(OverlayStyle.PAUSE).apply { alpha = 179 },
        PhaseType.LOCKOUT to chartPaint(OverlayStyle.LOCKOUT).apply { alpha = 179 },
        null to chartPaint(OverlayStyle.PAUSE).apply { alpha = 128 },
    )
    private val path = Path()
    private val clipPath = Path()
    private val chartRect = RectF()
    private val barRect = RectF()

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
    private var layout = LayoutResult(emptyList(), 0f)

    fun draw(canvas: Canvas, width: Int, height: Int, tSec: Double, hudBottom: Float) {
        if (width <= 0 || height <= 0) return
        val baseScale = (min(width.toFloat(), height * 1.25f) / 1080f).coerceAtLeast(0.55f)
        if (width != lastWidth || height != lastHeight || lastHudBottom != hudBottom) {
            layout = buildLayout(width.toFloat(), height.toFloat(), baseScale, hudBottom)
            lastWidth = width
            lastHeight = height
            lastHudBottom = hudBottom
        }
        val uiScale = layout.contentScale.takeIf { it > 0f } ?: (baseScale * CONTENT_MULTIPLIER)
        ensureSizes(uiScale)

        val active = RepTimeline.currentRepAt(result.repSlices, tSec)
            ?: RepTimeline.trailRepAt(result.repSlices, tSec)
        val cueSec = active?.let { tSec.coerceIn(it.startSec, it.endSec) } ?: tSec

        for ((kind, rect) in layout.slots) {
            drawCardBackground(canvas, rect, uiScale)
            clippedToCard(canvas, rect, 12f * uiScale) {
                when (kind) {
                    CardKind.MAIN -> drawMainCard(canvas, rect, active, uiScale)
                    CardKind.VELOCITY -> drawVelocityCard(canvas, rect, active, cueSec, uiScale)
                    CardKind.POSE -> drawPoseCard(canvas, rect, cueSec, uiScale)
                    CardKind.BAR_PATH -> drawBarPathCard(canvas, rect, active, cueSec, uiScale)
                    CardKind.REP_BARS -> drawRepBarsCard(canvas, rect, active, uiScale)
                }
            }
        }
    }

    /** Median bar x in output pixels; null when calibration is missing. */
    private fun barMedianXpx(): Double? {
        val calibration = result.calibration ?: return null
        val samples = result.motionSamples
        if (samples.isEmpty()) return null
        val sinAngle = sin(Math.toRadians(calibration.cameraAngleDeg))
        if (!sinAngle.isFinite() || sinAngle <= 1e-6) return null
        val xs = samples.map { it.xM * calibration.pxPerM * sinAngle }.sorted()
        return xs[xs.size / 2]
    }

    private fun ensureSizes(uiScale: Float) {
        if (uiScale == lastUiScale) return
        lastUiScale = uiScale
        titlePaint.textSize = TITLE * uiScale
        heroPaint.textSize = HERO * uiScale
        unitPaint.textSize = TITLE * uiScale
        labelPaint.textSize = LABEL * uiScale
        valuePaint.textSize = BODY * uiScale
        emptyPaint.textSize = BODY * uiScale
        linePaint.strokeWidth = 3f * uiScale
        axisPaint.strokeWidth = max(0.8f, 1f * uiScale)
        meanPaint.strokeWidth = max(0.8f, 1f * uiScale)
        cursorPaint.strokeWidth = max(1f, 1.5f * uiScale)
        borderPaint.strokeWidth = max(0.8f, 1f * uiScale)
        dotRingPaint.strokeWidth = max(1.2f, 2f * uiScale)
        selectionPaint.strokeWidth = max(1.2f, 1.5f * uiScale)
        val dash = DashPathEffect(floatArrayOf(6f * uiScale, 6f * uiScale), 0f)
        axisPaint.pathEffect = dash
        meanPaint.pathEffect = dash
        barPathPaints.values.forEach { it.strokeWidth = 3f * uiScale }
    }

    /**
     * Portrait: fixed left column, measured card heights, explicit gaps. Landscape: five equal
     * cards in a bottom strip. If the column is too tall, text/spacing shrink together so the last
     * card always ends above the bottom safe margin.
     */
    private fun buildLayout(
        width: Float,
        height: Float,
        baseScale: Float,
        hudBottom: Float,
    ): LayoutResult {
        val kinds = CardKind.values().toList()
        if (width > height) {
            val stripHeight = min(height * 0.32f, 320f * baseScale).coerceAtLeast(1f)
            val gap = 8f * baseScale
            val margin = 12f * baseScale
            val cardWidth = (
                (width - margin * 2f - gap * (kinds.size - 1)) / kinds.size
                ).coerceAtLeast(1f)
            val top = (height - margin - stripHeight).coerceAtLeast(0f)
            val contentScale = min(baseScale * CONTENT_MULTIPLIER, stripHeight / cardHeight(CardKind.MAIN, 1f))
                .coerceAtLeast(0.4f)
            val slots = kinds.mapIndexed { index, kind ->
                val left = margin + index * (cardWidth + gap)
                kind to RectF(left, top, left + cardWidth, top + stripHeight)
            }
            return LayoutResult(slots, contentScale)
        }

        val desiredScale = baseScale * CONTENT_MULTIPLIER
        val margin = 12f * desiredScale
        val columnWidth = (width * 0.36f).coerceAtLeast(1f)
        // Cards sit opposite the bar path so they never cover it. The whole set uses one side
        // (median x) so the column does not jump between reps.
        val cardsOnRight = barMedianXpx()?.let { it < width / 2.0 } ?: false
        val columnLeft = if (cardsOnRight) {
            (width - margin - columnWidth).coerceAtLeast(margin)
        } else {
            margin
        }
        val top = if (cardsOnRight) {
            margin
        } else {
            (hudBottom + 8f * desiredScale).coerceAtLeast(margin)
        }
        val available = (height - top - margin).coerceAtLeast(1f)

        var contentScale = desiredScale
        var gap = 8f * contentScale
        var heights = kinds.map { cardHeight(it, contentScale) }
        var total = heights.sum() + gap * (kinds.size - 1)
        if (total > available) {
            contentScale = (desiredScale * (available / total)).coerceIn(0.3f, desiredScale)
            gap = 8f * contentScale
            heights = kinds.map { cardHeight(it, contentScale) }
            total = heights.sum() + gap * (kinds.size - 1)
        }
        if (total > available) {
            val squeeze = (available / total).coerceAtLeast(0.05f)
            heights = heights.map { (it * squeeze).coerceAtLeast(0.5f) }
            gap *= squeeze
            total = heights.sum() + gap * (kinds.size - 1)
        }
        if (total > available) {
            // Extremely short frames: drop inter-card gaps before clipping anything.
            gap = 0f
            val heightBudget = available.coerceAtLeast(1f)
            val sum = heights.sum()
            if (sum > heightBudget) {
                val squeeze = heightBudget / sum
                heights = heights.map { (it * squeeze).coerceAtLeast(0.5f) }
            }
        }
        val slots = ArrayList<Pair<CardKind, RectF>>(kinds.size)
        var cursor = top
        kinds.forEachIndexed { index, kind ->
            val cardHeight = heights[index].coerceAtLeast(0.5f)
            slots += kind to RectF(
                columnLeft,
                cursor,
                columnLeft + columnWidth,
                cursor + cardHeight,
            )
            cursor += cardHeight + gap
        }
        return LayoutResult(slots, contentScale)
    }

    /** Report-equivalent measured height in sp/dp units. */
    private fun cardHeight(kind: CardKind, scale: Float): Float {
        val pad = 10f * scale
        val title = TITLE * scale
        val gap = 6f * scale
        return when (kind) {
            CardKind.MAIN -> {
                val miniRow = LABEL * scale + 3f * scale + BODY * scale
                pad + title + gap + HERO * scale + gap + miniRow + gap + miniRow + pad
            }

            CardKind.VELOCITY -> pad + title + gap + 56f * scale + pad
            CardKind.POSE -> {
                val rows = if (result.analyzePose) (poseJoints.size + 1) / 2 else 1
                val row = if (result.analyzePose) {
                    LABEL * scale + 3f * scale + BODY * scale
                } else {
                    BODY * scale
                }
                pad + title + gap + rows * row + (rows - 1) * gap + pad
            }

            CardKind.BAR_PATH -> pad + title + gap + 96f * scale + pad
            CardKind.REP_BARS -> pad + title + gap + 56f * scale + pad
        }
    }

    private fun drawMainCard(canvas: Canvas, rect: RectF, active: RepSlice?, uiScale: Float) {
        val pad = 10f * uiScale
        val contentWidth = rect.width() - pad * 2f
        val titleBaseline = rect.top + pad + titlePaint.textSize
        drawTextFitted(canvas, mainTitle, rect.left + pad, titleBaseline, titlePaint, contentWidth)

        val metric = active?.let { metricsByRep[it.index] }
        val heroBaseline = titleBaseline + 6f * uiScale + heroPaint.textSize
        val heroText = formatMcv(metric?.mcvMps)
        drawTextFitted(canvas, heroText, rect.left + pad, heroBaseline, heroPaint, contentWidth)
        val heroWidth = min(heroPaint.measureText(heroText), contentWidth)
        drawTextFitted(
            canvas,
            unitMps,
            rect.left + pad + heroWidth + 4f * uiScale,
            heroBaseline,
            unitPaint,
            (contentWidth - heroWidth - 4f * uiScale).coerceAtLeast(1f),
        )

        val labelBaseline = heroBaseline + 6f * uiScale + labelPaint.textSize
        val valueBaseline = labelBaseline + 3f * uiScale + valuePaint.textSize
        val secondLabelBaseline = valueBaseline + 6f * uiScale + labelPaint.textSize
        val secondValueBaseline = secondLabelBaseline + 3f * uiScale + valuePaint.textSize
        val cellWidth = contentWidth / 2f
        val summary = result.summary
        drawMiniCell(
            canvas, setMeanLabel, formatMcv(summary.mcvAvgMps),
            rect.left + pad, labelBaseline, valueBaseline, cellWidth,
        )
        drawMiniCell(
            canvas, bestLabel, formatMcv(summary.bestMcvMps),
            rect.left + pad + cellWidth, labelBaseline, valueBaseline, cellWidth,
        )
        drawMiniCell(
            canvas, velocityLossLabel, formatOne(summary.velocityLossPct, "%"),
            rect.left + pad, secondLabelBaseline, secondValueBaseline, cellWidth,
        )
        drawMiniCell(
            canvas, repCountLabel, summary.repCount.toString(),
            rect.left + pad + cellWidth, secondLabelBaseline, secondValueBaseline, cellWidth,
        )
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
        drawTextFitted(canvas, value, left, valueBaseline, valuePaint, maxWidth)
    }

    /**
     * One phase at a time: eccentric playback draws the eccentric curve, concentric draws the
     * concentric curve, with both phase durations in the free bottom row.
     */
    private fun drawVelocityCard(
        canvas: Canvas,
        rect: RectF,
        active: RepSlice?,
        cueSec: Double,
        uiScale: Float,
    ) {
        val pad = 10f * uiScale
        val contentWidth = rect.width() - pad * 2f
        val titleBaseline = rect.top + pad + titlePaint.textSize
        drawTextFitted(canvas, velocityTitle, rect.left + pad, titleBaseline, titlePaint, contentWidth)

        val samples = active?.let { velocityByRep[it.index] }.orEmpty()
        if (active == null) {
            drawEmpty(canvas, rect)
            return
        }
        val phase = displayVelocityPhase(active, cueSec)
        val phaseSamples = if (phase != null) {
            samples.filter { it.tSec >= phase.startSec && it.tSec <= phase.endSec }
        } else {
            samples
        }
        if (phaseSamples.size < 2) {
            drawEmpty(canvas, rect)
            return
        }

        val labelRowHeight = labelPaint.textSize + 5f * uiScale
        val chartTop = titleBaseline + 6f * uiScale
        val chartBottom = rect.bottom - pad - labelRowHeight
        val chartHeight = chartBottom - chartTop
        if (chartHeight <= 1f) return

        val startSec = phase?.startSec ?: active.startSec
        val endSec = phase?.endSec ?: active.endSec
        val inset = 10f * uiScale
        val mapLeft = rect.left + pad + inset
        val mapWidth = (contentWidth - inset * 2f).coerceAtLeast(1f)
        var minVelocity = min(0.0, phaseSamples.minOf { it.vyMps })
        var maxVelocity = max(0.0, phaseSamples.maxOf { it.vyMps })
        if (maxVelocity - minVelocity < 1e-9) {
            minVelocity -= 0.5
            maxVelocity += 0.5
        }
        val velocitySpan = maxVelocity - minVelocity
        val usableHeight = (chartHeight - inset * 2f).coerceAtLeast(1f)
        fun xFor(t: Double): Float? = overlayTimeToX(t, startSec, endSec, mapLeft, mapWidth)
        fun yFor(velocity: Double): Float =
            chartTop + inset + usableHeight * (1f - ((velocity - minVelocity) / velocitySpan).toFloat())

        val zeroY = yFor(0.0).coerceIn(chartTop, chartTop + chartHeight)
        canvas.drawLine(rect.left + pad, zeroY, rect.left + pad + contentWidth, zeroY, axisPaint)

        // MCV is a concentric mean; showing it during the eccentric phase is misleading.
        if (phase?.type == PhaseType.CONCENTRIC) {
            metricsByRep[active.index]?.mcvMps?.takeIf { it.isFinite() }?.let { mean ->
                val meanY = yFor(mean).coerceIn(chartTop, chartTop + chartHeight)
                canvas.drawLine(
                    rect.left + pad, meanY, rect.left + pad + contentWidth, meanY, meanPaint,
                )
            }
        }

        linePaint.color = if (phase?.type == PhaseType.ECCENTRIC) {
            OverlayStyle.ECCENTRIC.toInt()
        } else {
            OverlayStyle.TRAIL.toInt()
        }
        path.reset()
        phaseSamples.forEachIndexed { index, sample ->
            val x = xFor(sample.tSec) ?: return@forEachIndexed
            val y = yFor(sample.vyMps).coerceIn(chartTop, chartTop + chartHeight)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)

        val cursorX = xFor(cueSec.coerceIn(startSec, endSec))
        if (cursorX != null) {
            canvas.drawLine(cursorX, chartTop, cursorX, chartTop + chartHeight, cursorPaint)
        }

        // Bottom row: eccentric / concentric label + duration, in phase colours.
        val labelBaseline = rect.bottom - pad
        val eccentric = active.phases.firstOrNull { it.type == PhaseType.ECCENTRIC }
        val concentric = active.phases.firstOrNull { it.type == PhaseType.CONCENTRIC }
        drawPhaseDuration(
            canvas, eccentric, rect.left + pad, labelBaseline, labelPaint, uiScale,
            OverlayStyle.ECCENTRIC.toInt(), contentWidth / 2f,
        )
        drawPhaseDuration(
            canvas, concentric, rect.left + pad + contentWidth / 2f, labelBaseline, labelPaint,
            uiScale, OverlayStyle.TRAIL.toInt(), contentWidth / 2f,
        )
    }

    private fun displayVelocityPhase(rep: RepSlice, tSec: Double): Phase? {
        val current = RepTimeline.phaseAt(rep, tSec.coerceIn(rep.startSec, rep.endSec))
        if (current?.type == PhaseType.ECCENTRIC || current?.type == PhaseType.CONCENTRIC) {
            return current
        }
        return rep.phases.lastOrNull {
            it.endSec <= tSec && (it.type == PhaseType.ECCENTRIC || it.type == PhaseType.CONCENTRIC)
        } ?: rep.phases.firstOrNull {
            it.type == PhaseType.ECCENTRIC || it.type == PhaseType.CONCENTRIC
        }
    }

    private fun drawPhaseDuration(
        canvas: Canvas,
        phase: Phase?,
        left: Float,
        baseline: Float,
        paint: Paint,
        uiScale: Float,
        color: Int,
        maxWidth: Float,
    ) {
        if (phase == null) return
        val name = context.getString(phaseLabelRes(result.lift, phase.type))
        val seconds = String.format(Locale.US, "%.1fs", (phase.endSec - phase.startSec).coerceAtLeast(0.0))
        val previous = paint.color
        paint.color = color
        try {
            drawTextFitted(
                canvas, "$name $seconds", left, baseline, paint, (maxWidth - 4f * uiScale).coerceAtLeast(1f),
            )
        } finally {
            paint.color = previous
        }
    }

    private fun drawPoseCard(canvas: Canvas, rect: RectF, cueSec: Double, uiScale: Float) {
        val pad = 10f * uiScale
        val contentWidth = rect.width() - pad * 2f
        val titleBaseline = rect.top + pad + titlePaint.textSize
        drawTextFitted(canvas, poseTitle, rect.left + pad, titleBaseline, titlePaint, contentWidth)
        val firstBaseline = titleBaseline + 6f * uiScale + valuePaint.textSize
        if (!result.analyzePose) {
            drawTextFitted(
                canvas, poseOffText, rect.left + pad, firstBaseline, valuePaint, contentWidth,
            )
            return
        }

        val nearest = OverlayTimeline.nearestFrameAngles(result.frameAngles, cueSec)
        val current = nearest?.takeIf { abs(it.tSec - cueSec) <= MAX_FRAME_GAP_SEC }
        if (current == null) {
            drawTextFitted(
                canvas, poseUnavailableText, rect.left + pad, firstBaseline, valuePaint, contentWidth,
            )
            return
        }
        val rows = poseJoints.chunked(2)
        val cellWidth = contentWidth / 2f
        rows.forEachIndexed { row, pair ->
            val labelBaseline = titleBaseline + 6f * uiScale +
                row * (LABEL * uiScale + 3f * uiScale + BODY * uiScale + 6f * uiScale) +
                labelPaint.textSize
            val valueBaseline = labelBaseline + 3f * uiScale + valuePaint.textSize
            pair.forEachIndexed { index, joint ->
                val left = rect.left + pad + cellWidth * index
                val value = angleValue(current, joint)
                drawTextFitted(
                    canvas, joint.label, left, labelBaseline, labelPaint,
                    (cellWidth - 4f * uiScale).coerceAtLeast(1f),
                )
                drawTextFitted(
                    canvas,
                    value?.let { formatAngle(it) } ?: missing,
                    left,
                    valueBaseline,
                    if (value == null) labelPaint else valuePaint,
                    (cellWidth - 4f * uiScale).coerceAtLeast(1f),
                )
            }
        }
    }

    /**
     * The burned angle labels on the skeleton are the source of truth: the card mirrors them and
     * only hides a value when the joint is genuinely missing. A low overall confidence no longer
     * blanks every cell while the same number is visible on the lifter.
     */
    private fun angleValue(frame: FrameAngles?, joint: JointSpec): Double? =
        frame?.let { joint.value(it) }?.takeIf { it.isFinite() }

    /**
     * Up to three rep paths. The window follows the current rep; the current one is drawn live
     * up to the playhead, its neighbours are complete trajectories. All three share one
     * equal-scale fit so their shapes can be compared directly.
     */
    private fun drawBarPathCard(
        canvas: Canvas,
        rect: RectF,
        active: RepSlice?,
        cueSec: Double,
        uiScale: Float,
    ) {
        val pad = 10f * uiScale
        val contentWidth = rect.width() - pad * 2f
        val titleBaseline = rect.top + pad + titlePaint.textSize
        drawTextFitted(canvas, barPathTitle, rect.left + pad, titleBaseline, titlePaint, contentWidth)
        val reps = result.repSlices
        if (reps.isEmpty()) {
            drawEmpty(canvas, rect)
            return
        }
        val activePosition = reps.indexOfFirst { it.index == active?.index }.takeIf { it >= 0 } ?: 0
        val windowSize = min(3, reps.size)
        val start = (activePosition - 1).coerceIn(0, (reps.size - windowSize).coerceAtLeast(0))
        val visible = reps.subList(start, start + windowSize)
        val bounds = visible.mapNotNull { barBoundsByRep[it.index] }
        if (bounds.isEmpty()) {
            drawEmpty(canvas, rect)
            return
        }
        val union = bounds.reduce { acc, next ->
            BarBounds(
                minX = min(acc.minX, next.minX),
                maxX = max(acc.maxX, next.maxX),
                minY = min(acc.minY, next.minY),
                maxY = max(acc.maxY, next.maxY),
            )
        }

        val labelHeight = labelPaint.textSize + 4f * uiScale
        val chartTop = titleBaseline + 6f * uiScale
        val chartBottom = rect.bottom - pad - labelHeight
        val chartHeight = chartBottom - chartTop
        if (chartHeight <= 1f) return
        val gap = 8f * uiScale
        val columnWidth = ((contentWidth - gap * (visible.size - 1)) / visible.size)
            .coerceAtLeast(1f)

        visible.forEachIndexed { index, rep ->
            val left = rect.left + pad + index * (columnWidth + gap)
            val fit = fitOverlayBarPath(
                minX = union.minX,
                maxX = union.maxX,
                minY = union.minY,
                maxY = union.maxY,
                widthPx = columnWidth,
                heightPx = chartHeight,
                paddingPx = 7f * uiScale,
            )
            fun point(sample: MotionSample): PointF = PointF(
                (left + fit.x(sample.xM)).coerceIn(left, left + columnWidth),
                (chartTop + fit.y(sample.yM)).coerceIn(chartTop, chartTop + chartHeight),
            )
            // Faint baseline only; three sets of axes would be noise.
            val axisY = (chartTop + fit.bottomPx).coerceIn(chartTop, chartTop + chartHeight)
            canvas.drawLine(left, axisY, left + columnWidth, axisY, axisPaint)

            val isCurrent = rep.index == active?.index
            val upTo = if (isCurrent) cueSec else rep.endSec
            val runs = RepTimeline.trailRuns(result.motionSamples, rep, upTo)
            var firstPoint: PointF? = null
            var lastPoint: PointF? = null
            for (run in runs) {
                if (run.samples.size < 2) continue
                path.reset()
                run.samples.forEachIndexed { sampleIndex, sample ->
                    val p = point(sample)
                    if (sampleIndex == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                canvas.drawPath(path, barPathPaints.getValue(run.type))
                if (firstPoint == null) firstPoint = point(run.samples.first())
                lastPoint = point(run.samples.last())
            }
            firstPoint?.let { canvas.drawCircle(it.x, it.y, 3.5f * uiScale, dotRingPaint) }
            lastPoint?.let { canvas.drawCircle(it.x, it.y, 3.5f * uiScale, dotPaint) }

            drawTextCenteredFitted(
                canvas,
                rep.index.toString(),
                left + columnWidth / 2f,
                rect.bottom - pad,
                if (isCurrent) valuePaint else labelPaint,
                columnWidth,
            )
        }
    }

    private fun drawRepBarsCard(
        canvas: Canvas,
        rect: RectF,
        active: RepSlice?,
        uiScale: Float,
    ) {
        val pad = 10f * uiScale
        val contentWidth = rect.width() - pad * 2f
        val titleBaseline = rect.top + pad + titlePaint.textSize
        drawTextFitted(canvas, repBarsTitle, rect.left + pad, titleBaseline, titlePaint, contentWidth)
        val reps = result.reps
        if (reps.isEmpty()) {
            drawEmpty(canvas, rect)
            return
        }

        // At most three bars: the current rep stays centered and the window slides as playback
        // advances. All bars are drawn on the same scale, with the rep number inside the block.
        val activePosition = reps.indexOfFirst { it.repIndex == active?.index }.takeIf { it >= 0 } ?: 0
        val windowSize = min(3, reps.size)
        val start = (activePosition - 1).coerceIn(0, (reps.size - windowSize).coerceAtLeast(0))
        val visible = reps.subList(start, start + windowSize)

        val gap = 10f * uiScale
        val columnWidth = ((contentWidth - gap * (visible.size - 1)) / visible.size).coerceAtLeast(1f)
        val chartBottom = rect.bottom - pad
        val chartTop = titleBaseline + 6f * uiScale
        val maxValue = reps.maxOfOrNull { it.mcvMps }?.takeIf { it > 0.0 } ?: return
        val barMaxHeight = (chartBottom - chartTop).coerceAtLeast(3f)

        visible.forEachIndexed { index, rep ->
            val selected = rep.repIndex == active?.index
            val fraction = (rep.mcvMps / maxValue).coerceIn(0.0, 1.0).toFloat()
            val height = max(3f * uiScale, (barMaxHeight - 4f * uiScale) * fraction)
            val left = rect.left + pad + index * (columnWidth + gap) + 4f * uiScale
            val right = left + (columnWidth - 8f * uiScale).coerceAtLeast(1f)
            val top = chartBottom - height
            barRect.set(left, top, right, chartBottom)
            // The current rep is the highlighted block, wherever the sliding window puts it.
            barPaint.color = if (selected) {
                OverlayStyle.TRAIL.toInt()
            } else {
                (OverlayStyle.TRAIL.toInt() and 0x00FFFFFF) or (115 shl 24)
            }
            canvas.drawRoundRect(barRect, 6f * uiScale, 6f * uiScale, barPaint)
            if (selected) {
                canvas.drawRoundRect(barRect, 6f * uiScale, 6f * uiScale, selectionPaint)
            }
            val centerX = (left + right) / 2f
            // The block itself carries the value; the rep index below was redundant.
            val valueText = formatMcv(rep.mcvMps) ?: missing
            val numberPaint = if (selected) valuePaint else labelPaint
            val numberBaseline = top + numberPaint.textSize + 3f * uiScale
            if (height >= numberPaint.textSize + 3f * uiScale) {
                drawTextCenteredFitted(
                    canvas, valueText, centerX, numberBaseline, numberPaint, columnWidth,
                )
            } else {
                drawTextCenteredFitted(
                    canvas,
                    valueText,
                    centerX,
                    (top - 3f * uiScale).coerceAtLeast(chartTop + numberPaint.textSize),
                    numberPaint,
                    columnWidth,
                )
            }
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
        canvas.drawText(missing, rect.centerX(), centerY, emptyPaint)
    }

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
        val previous = paint.textScaleX
        paint.textScaleX = (maxWidth / textWidth).coerceAtLeast(0.1f)
        try {
            canvas.drawText(text, x, baseline, paint)
        } finally {
            paint.textScaleX = previous
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

    private fun drawTextCenteredFitted(
        canvas: Canvas,
        text: String,
        centerX: Float,
        baseline: Float,
        paint: Paint,
        maxWidth: Float,
    ) {
        val previous = paint.textAlign
        paint.textAlign = Paint.Align.CENTER
        try {
            drawTextFitted(canvas, text, centerX, baseline, paint, maxWidth)
        } finally {
            paint.textAlign = previous
        }
    }

    private fun formatMcv(value: Double?): String =
        value?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.2f", it) } ?: missing

    private fun formatOne(value: Double?, suffix: String = ""): String =
        value?.takeIf { it.isFinite() }?.let {
            String.format(Locale.US, "%.1f", it) + suffix
        } ?: missing

    private fun formatAngle(value: Double): String =
        String.format(Locale.US, angleFormat, value)

    private companion object {
        const val TITLE = 12f
        const val HERO = 40f
        const val LABEL = 11f
        const val BODY = 16f
        const val CONTENT_MULTIPLIER = 2.75f
        const val MAX_FRAME_GAP_SEC = 0.3

        fun textPaint(color: Long, align: Paint.Align): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toInt()
            textAlign = align
        }

        fun chartPaint(color: Long): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color.toInt()
        }

        fun bandPaint(color: Long): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color.toInt()
            alpha = 31
        }
    }
}
