package com.vbtdroid.app.overlay

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.content.Context
import com.vbtdroid.app.R
import com.vbtdroid.app.analysis.AnalysisResult
import com.vbtdroid.app.ui.report.phaseLabelRes
import com.vbtdroid.app.vision.PoseFrame
import com.vbtdroid.core.metrics.Point2
import com.vbtdroid.core.metrics.RepMetrics
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.phases.Phase
import com.vbtdroid.core.phases.PhaseType
import com.vbtdroid.core.phases.RepTimeline
import com.vbtdroid.core.phases.RepSlice
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Burns pose, joint angles, bar path and a compact HUD onto a mutable frame bitmap.
 *
 * The renderer owns reusable paints/paths and sizes everything against [referenceWidth] so the same
 * output looks consistent on 720p and 1080p frames. All coordinates come from the same decoder
 * settings as the analysis pipeline.
 */
class OverlayRenderer(private val context: Context, private val result: AnalysisResult) {
    private val skeletonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = OverlayStyle.SKELETON_LINE.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = OverlayStyle.SKELETON.toInt()
    }
    private val jointRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = OverlayStyle.SKELETON.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = OverlayStyle.TRAIL.toInt()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val currentPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = OverlayStyle.TRAIL.toInt()
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = OverlayStyle.BUBBLE.toInt()
    }
    private val speedBubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = OverlayStyle.SPEED_BUBBLE.toInt()
    }
    private val angleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = OverlayStyle.SKELETON.toInt()
        textAlign = Paint.Align.LEFT
    }
    private val speedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = OverlayStyle.SPEED.toInt()
        textAlign = Paint.Align.LEFT
    }
    private val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = OverlayStyle.SKELETON.toInt()
        textAlign = Paint.Align.LEFT
    }
    private val path = Path()
    private val phaseTrailPaints: Map<PhaseType?, Paint> = mapOf(
        PhaseType.ECCENTRIC to trailPaintOf(OverlayStyle.ECCENTRIC, 255),
        PhaseType.CONCENTRIC to trailPaintOf(OverlayStyle.TRAIL, 255),
        PhaseType.PAUSE to trailPaintOf(OverlayStyle.PAUSE, 160),
        PhaseType.LOCKOUT to trailPaintOf(OverlayStyle.LOCKOUT, 160),
        null to trailPaintOf(OverlayStyle.PAUSE, 120),
    )
    private val bubbleRect = RectF()
    private val cards = OverlayCards(context, result)

    private var referenceWidth = 0

    fun draw(canvas: Canvas, width: Int, height: Int, tSec: Double) {
        if (width <= 0 || height <= 0) return
        val scale = width / 1080f
        if (width != referenceWidth) {
            referenceWidth = width
            skeletonPaint.strokeWidth = max(2.4f, 4.0f * scale)
            jointRingPaint.strokeWidth = max(1.4f, 2.2f * scale)
            trailPaint.strokeWidth = max(1.5f, 3.5f * scale)
            angleTextPaint.textSize = max(9f, 26f * scale)
            speedTextPaint.textSize = max(11f, 34f * scale)
            hudTextPaint.textSize = max(9f, 25f * scale)
        }
        val dotRadius = max(1.5f, 4.0f * scale)

        drawTrail(canvas, tSec, dotRadius)
        // Only draw a pose that belongs to this moment. A sparse/failed pose track must not
        // freeze a later frame over the whole first half of the clip.
        val pose = OverlayTimeline.nearestPoseFrame(result.poseFrames, tSec)
            ?.takeIf { abs(it.tSec - tSec) <= MAX_POSE_GAP_SEC }
        if (pose != null) {
            drawPose(canvas, pose, dotRadius, scale, height)
        }
        val hudBottom = drawHud(canvas, tSec, width, height, scale)
        cards.draw(canvas, width, height, tSec, hudBottom)
        // Speed and HUD are drawn after the cards so the readouts stay legible even if the bar
        // travels through the card strip.
        drawSpeed(canvas, tSec, width, height, scale)
    }

    /**
     * Bar path of the rep that owns the current playback time, colored by phase. In the gap after
     * a rep the previous rep's full trail stays visible; it clears only when the next rep starts.
     */
    private fun drawTrail(canvas: Canvas, tSec: Double, dotRadius: Float) {
        val rep = RepTimeline.trailRepAt(result.repSlices, tSec)
        if (rep != null) {
            for (run in RepTimeline.trailRuns(result.motionSamples, rep, tSec)) {
                val points = run.samples.mapNotNull(::toPixel)
                if (points.size < 2) continue
                path.reset()
                points.forEachIndexed { index, point ->
                    if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                canvas.drawPath(path, trailPaintFor(run.type))
            }
        }

        val current = OverlayTimeline.nearestMotionSample(result.motionSamples, tSec)?.let(::toPixel)
        if (current != null) {
            canvas.drawCircle(current.x, current.y, dotRadius * 1.6f, currentPointPaint)
            skeletonPaint.color = OverlayStyle.SKELETON.toInt()
            canvas.drawCircle(current.x, current.y, dotRadius * 2.4f, skeletonPaint)
        }
    }

    private fun trailPaintOf(color: Long, alpha: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = (color.toInt() and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun trailPaintFor(type: PhaseType?): Paint {
        val paint = phaseTrailPaints.getValue(type)
        paint.strokeWidth = trailPaint.strokeWidth
        return paint
    }

    /** Converts smoothed bar coordinates from metres back into image pixels. */
    private fun toPixel(sample: com.vbtdroid.core.metrics.MotionSample): PointF? {
        val calibration = result.calibration ?: return null
        val azimuth = Math.toRadians(calibration.cameraAngleDeg)
        return PointF(
            (sample.xM * calibration.pxPerM * kotlin.math.sin(azimuth)).toFloat(),
            (-sample.yM * calibration.pxPerM).toFloat(),
        )
    }

    private fun drawPose(canvas: Canvas, pose: PoseFrame, dotRadius: Float, scale: Float, height: Int) {
        skeletonPaint.color = OverlayStyle.SKELETON.toInt()
        BONES.forEach { (from, to) ->
            val a = visiblePoint(pose, from) ?: return@forEach
            val b = visiblePoint(pose, to) ?: return@forEach
            canvas.drawLine(a.x.toFloat(), a.y.toFloat(), b.x.toFloat(), b.y.toFloat(), skeletonPaint)
        }
        LANDMARKS.forEach { index ->
            visiblePoint(pose, index)?.let { point ->
                // Hollow ring + inner dot, Arcus-style. Rings read better over busy video than
                // solid dots and keep the joint centre visible.
                canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), dotRadius * 1.9f, jointRingPaint)
                canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), dotRadius * 0.75f, jointPaint)
            }
        }

        val angles = OverlayTimeline.nearestFrameAngles(result.frameAngles, pose.tSec) ?: return
        val left = sideVisibility(pose, LEFT_SIDE)
        val right = sideVisibility(pose, RIGHT_SIDE)
        val useLeft = left >= right
        when (result.lift) {
            Lift.BENCH -> {
                drawAngle(canvas, angles.shoulderDeg, pose, if (useLeft) 13 else 14, scale, height)
                drawAngle(canvas, angles.elbowDeg, pose, if (useLeft) 15 else 16, scale, height)
            }

            Lift.SQUAT, Lift.DEADLIFT -> {
                drawAngle(canvas, angles.hipDeg, pose, if (useLeft) 23 else 24, scale, height)
                drawAngle(canvas, angles.kneeDeg, pose, if (useLeft) 25 else 26, scale, height)
                drawAngle(canvas, angles.ankleDeg, pose, if (useLeft) 27 else 28, scale, height)
            }
        }
    }

    private fun drawAngle(
        canvas: Canvas,
        value: Double?,
        pose: PoseFrame,
        landmark: Int,
        scale: Float,
        height: Int,
    ) {
        if (value == null || !value.isFinite()) return
        val point = visiblePoint(pose, landmark) ?: return
        val text = String.format(Locale.US, "%.0f\u00B0", value)
        drawBubble(
            canvas = canvas,
            text = text,
            anchorX = point.x.toFloat() + 10f * scale,
            anchorY = point.y.toFloat() - 8f * scale,
            paint = angleTextPaint,
            maxWidth = referenceWidth.toFloat(),
            maxHeight = height.toFloat(),
            paddingX = 5f * scale,
            paddingY = 3f * scale,
            corner = 5f * scale,
        )
    }

    private fun drawSpeed(canvas: Canvas, tSec: Double, width: Int, height: Int, scale: Float) {
        val speed = OverlayTimeline.speedMps(result.motionSamples, tSec) ?: return
        val current = OverlayTimeline.nearestMotionSample(result.motionSamples, tSec)?.let(::toPixel) ?: return
        val text = String.format(Locale.US, "%.2f m/s", speed)
        drawBubble(
            canvas = canvas,
            text = text,
            anchorX = current.x + 16f * scale,
            anchorY = current.y - 24f * scale,
            paint = speedTextPaint,
            fill = speedBubblePaint,
            maxWidth = width.toFloat(),
            maxHeight = height.toFloat(),
            paddingX = 8f * scale,
            paddingY = 5f * scale,
            corner = 7f * scale,
        )
    }

    private fun drawHud(canvas: Canvas, tSec: Double, width: Int, height: Int, scale: Float): Float {
        // Keep the previous rep through the between-rep gap, matching the burned-in cards/trail.
        val slice = RepTimeline.currentRepAt(result.repSlices, tSec)
            ?: RepTimeline.trailRepAt(result.repSlices, tSec)
        val rep = slice?.let { current -> result.reps.firstOrNull { it.repIndex == current.index } }
        // Lift and load already live in the report top bar; the HUD only adds in-video state.
        val lines = buildList {
            add(repLabel(slice, tSec))
            add(velocityLabel(rep))
        }
        val lineHeight = hudTextPaint.textSize * 1.35f
        var textWidth = 0f
        lines.forEach { textWidth = max(textWidth, hudTextPaint.measureText(it)) }
        val padding = 9f * scale
        val left = padding
        val top = padding
        bubbleRect.set(
            left,
            top,
            (left + textWidth + padding * 2f).coerceAtMost(width.toFloat() - padding),
            top + lines.size * lineHeight + padding * 2f,
        )
        canvas.drawRoundRect(bubbleRect, 7f * scale, 7f * scale, bubblePaint)
        var baseline = top + padding + hudTextPaint.textSize
        lines.forEach { line ->
            canvas.drawText(line, left + padding, baseline.coerceAtMost(height.toFloat() - padding), hudTextPaint)
            baseline += lineHeight
        }
        return bubbleRect.bottom
    }

    private fun repLabel(slice: RepSlice?, tSec: Double): String {
        if (slice == null) return "\u2014"
        val phase = RepTimeline.phaseAt(slice, tSec.coerceIn(slice.startSec, slice.endSec))
        val stage = phase?.let { context.getString(phaseLabelRes(result.lift, it.type)) } ?: "\u2014"
        return context.getString(R.string.report_hud_stage, slice.index, result.repSlices.size, stage)
    }

    private fun velocityLabel(rep: RepMetrics?): String {
        rep?.mcvMps?.takeIf { it > 0.0 }?.let {
            return String.format(Locale.US, "MCV %.2f m/s", it)
        }
        rep?.velocityLossPct?.let {
            return String.format(Locale.US, "VL %.1f%%", it)
        }
        return "MCV \u2014"
    }

    private fun drawBubble(
        canvas: Canvas,
        text: String,
        anchorX: Float,
        anchorY: Float,
        paint: Paint,
        maxWidth: Float,
        maxHeight: Float,
        paddingX: Float,
        paddingY: Float,
        corner: Float,
        fill: Paint = bubblePaint,
    ) {
        val textWidth = paint.measureText(text)
        val textHeight = paint.textSize
        val left = anchorX.coerceIn(0f, max(0f, maxWidth - textWidth - paddingX * 2f))
        val top = anchorY.coerceIn(0f, max(0f, maxHeight - textHeight - paddingY * 2f))
        bubbleRect.set(left, top, left + textWidth + paddingX * 2f, top + textHeight + paddingY * 2f)
        canvas.drawRoundRect(bubbleRect, corner, corner, fill)
        canvas.drawText(text, left + paddingX, top + paddingY - paint.fontMetrics.ascent, paint)
    }

    private fun visiblePoint(pose: PoseFrame, index: Int): Point2? {
        if ((pose.visibility[index] ?: 1f) < MIN_VISIBILITY) return null
        return pose.pixelPoint(index)
    }

    private fun sideVisibility(pose: PoseFrame, indices: IntArray): Float {
        var sum = 0f
        indices.forEach { index ->
            sum += if (index in pose.landmarks) pose.visibility[index] ?: 0f else 0f
        }
        return sum / indices.size
    }

    private companion object {
        /** Pose is estimated every third frame (~0.1 s at 30 fps); allow two frames of slack. */
        const val MAX_POSE_GAP_SEC = 0.25
        const val MIN_VISIBILITY = 0.35f

        val LEFT_SIDE = intArrayOf(11, 13, 15, 19, 23, 25, 27, 31)
        val RIGHT_SIDE = intArrayOf(12, 14, 16, 20, 24, 26, 28, 32)
        val LANDMARKS = intArrayOf(11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28)
        val BONES = listOf(
            11 to 12,
            11 to 23,
            12 to 24,
            23 to 24,
            23 to 25,
            25 to 27,
            24 to 26,
            26 to 28,
            11 to 13,
            13 to 15,
            12 to 14,
            14 to 16,
        )
    }
}
