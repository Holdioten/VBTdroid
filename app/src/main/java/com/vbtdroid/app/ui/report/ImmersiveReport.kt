package com.vbtdroid.app.ui.report

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vbtdroid.app.R
import com.vbtdroid.app.analysis.FrameAngles
import com.vbtdroid.app.ui.components.ChipTone
import com.vbtdroid.app.ui.components.MessageBanner
import com.vbtdroid.app.ui.components.SectionCard
import com.vbtdroid.app.ui.components.SectionLabel
import com.vbtdroid.app.ui.components.StatusChip
import com.vbtdroid.app.ui.theme.Bg
import com.vbtdroid.app.ui.theme.MinTouchTarget
import com.vbtdroid.app.ui.theme.ShapePill
import com.vbtdroid.app.ui.theme.VbtSpacing
import com.vbtdroid.app.ui.theme.VbtTheme
import com.vbtdroid.core.metrics.MotionSample
import com.vbtdroid.core.metrics.RepMetrics
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.model.MetricId
import com.vbtdroid.core.phases.RepSlice
import com.vbtdroid.core.quality.QualityFlags
import com.vbtdroid.core.metrics.LvpEstimate
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Scrim colour for floating cards: #0A0E11 at 72% (spec §1.7). */
private val GlassColor = Bg.copy(alpha = 0.72f)

/**
 * Full-bleed overlay video. Scales to cover the parent (cropping) so portrait and landscape both
 * fill the hero area without distortion; the view is measured to the video's own aspect ratio.
 */
@Composable
internal fun CoverVideo(file: File, playback: OverlayPlayback, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            CoverFrame(context).also { frame ->
                val view = VideoView(context)
                frame.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                view.setOnPreparedListener { player ->
                    frame.setVideoSize(player.videoWidth, player.videoHeight)
                    player.isLooping = true
                    if (playback.targetMs > 0) view.seekTo(playback.targetMs)
                    view.start()
                    playback.playing = true
                }
                view.setVideoURI(Uri.fromFile(file))
                playback.view = view
            }
        },
    )
    DisposableEffect(file) {
        onDispose { playback.release() }
    }
}

/** Layout that cover-scales its single child to the video's aspect ratio. */
private class CoverFrame(context: Context) : FrameLayout(context) {
    private var videoWidth = 0
    private var videoHeight = 0

    fun setVideoSize(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val child = getChildAt(0) ?: return
        val (cw, ch) = coverSize(w, h)
        child.measure(
            MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = getChildAt(0) ?: return
        val cw = child.measuredWidth
        val ch = child.measuredHeight
        val l = (width - cw) / 2
        val t = (height - ch) / 2
        child.layout(l, t, l + cw, t + ch)
    }

    private fun coverSize(w: Int, h: Int): Pair<Int, Int> {
        if (videoWidth <= 0 || videoHeight <= 0 || w <= 0 || h <= 0) return w to h
        val scale = max(w.toFloat() / videoWidth, h.toFloat() / videoHeight)
        return (videoWidth * scale).roundToInt() to (videoHeight * scale).roundToInt()
    }
}

/** Play/pause and seek for the background overlay video. */
@Composable
internal fun PlaybackBar(playback: OverlayPlayback, modifier: Modifier = Modifier) {
    LaunchedEffect(playback) {
        while (true) {
            playback.view?.let { v ->
                if (v.duration > 0) {
                    playback.durationMs = v.duration.toFloat()
                    playback.positionMs = v.currentPosition.toFloat()
                    playback.playing = v.isPlaying
                }
            }
            delay(100)
        }
    }
    val duration = playback.durationMs
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s),
    ) {
        Surface(
            shape = ShapePill,
            color = GlassColor,
            modifier = Modifier.clickable { playback.togglePlay() },
        ) {
            Text(
                stringResource(if (playback.playing) R.string.report_overlay_pause else R.string.report_overlay_play),
                modifier = Modifier.padding(horizontal = VbtSpacing.m, vertical = VbtSpacing.s),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Slider(
            value = if (duration > 0f) playback.positionMs / duration else 0f,
            onValueChange = { fraction -> playback.seekTo((fraction * duration).toInt()) },
            enabled = duration > 0f,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Top strip over the video. Row 1: back, identity (one line), history. Row 2: rep stepper and
 * status. Two rows keep the identity from being squeezed by the rep index.
 */
@Composable
internal fun ReportHeader(
    identity: String,
    repLabel: String,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    status: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = VbtSpacing.m, vertical = VbtSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(VbtSpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
            RoundGlassButton(onClick = onBack, label = stringResource(R.string.report_back), icon = Icons.Filled.KeyboardArrowLeft)
            Surface(shape = ShapePill, color = GlassColor, modifier = Modifier.weight(1f)) {
                Text(
                    identity,
                    modifier = Modifier.padding(horizontal = VbtSpacing.m, vertical = VbtSpacing.s),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            TextButton(onClick = onHistory, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                Text(stringResource(R.string.history_title), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
            Surface(shape = ShapePill, color = GlassColor) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = VbtSpacing.s)) {
                    IconButton(onClick = onPrev, enabled = canPrev, modifier = Modifier.size(MinTouchTarget)) {
                        Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(repLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    IconButton(onClick = onNext, enabled = canNext, modifier = Modifier.size(MinTouchTarget)) {
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            status?.invoke()
        }
    }
}

@Composable
private fun RoundGlassButton(onClick: () -> Unit, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        shape = ShapePill,
        color = GlassColor,
        modifier = Modifier.size(MinTouchTarget).clickable(onClick = onClick),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Translucent floating card over the video (spec §1.7). Compact: 10dp padding, tight spacing. */
@Composable
internal fun GlassCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = GlassColor,
        border = BorderStroke(1.dp, VbtTheme.colors.outline.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (title != null || trailing != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (title != null) {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelMedium,
                            color = VbtTheme.colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

/** This rep's MCV as the hero number, with a 2×2 grid of set-level figures. */
@Composable
internal fun MainMcvCard(
    modifier: Modifier,
    repMcv: Double?,
    setMeanMcv: Double?,
    bestMcv: Double?,
    velocityLossPct: Double?,
    repCount: Int,
) {
    val missing = stringResource(R.string.report_value_missing)
    GlassCard(modifier = modifier, title = stringResource(R.string.report_card_rep_mcv)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                formatMcv(repMcv) ?: missing,
                style = VbtTheme.numbers.displayMetricSm,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                stringResource(R.string.unit_mps),
                style = MaterialTheme.typography.labelMedium,
                color = VbtTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniCell(stringResource(R.string.report_mini_set_mean), formatMcv(setMeanMcv) ?: missing, Modifier.weight(1f))
            MiniCell(stringResource(R.string.report_mini_best_mcv), formatMcv(bestMcv) ?: missing, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniCell(
                stringResource(R.string.report_mini_vl),
                formatMetricValue(velocityLossPct, MetricId.SET_VELOCITY_LOSS),
                Modifier.weight(1f),
            )
            MiniCell(stringResource(R.string.report_mini_reps), repCount.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = VbtTheme.colors.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
internal fun VelocityCard(
    modifier: Modifier,
    samples: List<MotionSample>,
    slice: RepSlice?,
    mean: Double?,
    cursorSec: Double?,
) {
    GlassCard(modifier = modifier, title = stringResource(R.string.report_velocity_chart)) {
        VelocityChart(
            samples = samples,
            rep = slice,
            meanVelocityMps = mean,
            height = 56.dp,
            chartBackground = Color.Transparent,
            cursorSec = cursorSec,
        )
    }
}

/** Joint angle over the rep (curve + playback cursor) and the key-frame values in a 2×2 grid. */
@Composable
internal fun AngleCard(
    modifier: Modifier,
    rep: RepMetrics,
    slice: RepSlice,
    samples: List<MotionSample>,
    angles: List<FrameAngles>,
    lift: Lift,
    cursorSec: Double?,
) {
    val frames = selectKeyFrameAngles(rep, slice, samples, angles)
    if (frames.isEmpty()) return
    val key = headlineKey(lift)
    GlassCard(modifier = modifier, title = stringResource(R.string.report_key_frame_angles)) {
        JointAngleChart(
            angles = angles,
            key = key,
            rep = slice,
            cursorSec = cursorSec,
            height = 40.dp,
        )
        frames.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { frame ->
                    val value = angleValue(frame.angles, key)
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(keyFrameLabel(frame.kind)),
                            style = MaterialTheme.typography.labelSmall,
                            color = VbtTheme.colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Text(
                            value?.let { stringResource(R.string.report_angle_value, it) }
                                ?: stringResource(R.string.report_value_missing),
                            style = MaterialTheme.typography.bodyLarge,
                            color = confidenceColorFor(frame.angles, key),
                            maxLines = 1,
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun confidenceColorFor(angles: FrameAngles, key: String) =
    com.vbtdroid.app.ui.components.confidenceColor(angles.confidenceByJoint[key] ?: angles.confidence)

/** Joint/key-frame angle over the rep; a vertical cursor marks the playback time. */
@Composable
internal fun JointAngleChart(
    angles: List<FrameAngles>,
    key: String,
    rep: RepSlice,
    cursorSec: Double?,
    height: Dp,
) {
    val series = angles.filter { it.tSec >= rep.startSec && it.tSec <= rep.endSec }
        .mapNotNull { frame -> angleValue(frame, key)?.takeIf { it.isFinite() }?.let { frame.tSec to it } }
    val lineColor = VbtTheme.colors.skeleton
    val cursorColor = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(height)) {
        if (series.size < 2) return@Canvas
        val minV = series.minOf { it.second } - 2.0
        val maxV = series.maxOf { it.second } + 2.0
        val span = (maxV - minV).takeIf { it > 1e-6 } ?: 1.0
        val path = Path()
        series.forEachIndexed { index, (t, v) ->
            val x = timeToX(t, rep.startSec, rep.endSec, size.width) ?: return@forEachIndexed
            val y = 4f + (size.height - 8f) * (1f - ((v - minV) / span).toFloat())
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor.copy(alpha = 0.9f), style = Stroke(width = 1.5.dp.toPx()))
        cursorSec?.let { t ->
            timeToX(t, rep.startSec, rep.endSec, size.width)?.let { x ->
                drawLine(cursorColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
            }
        }
    }
}

/** Joint shown as the headline angle: elbow for bench, knee for squat and deadlift. */
private fun headlineKey(lift: Lift): String = if (lift == Lift.BENCH) "elbow_angle_deg" else "knee_angle_deg"

private fun angleValue(angles: FrameAngles, key: String): Double? = when (key) {
    "elbow_angle_deg" -> angles.elbowDeg
    else -> angles.kneeDeg
}

@Composable
internal fun BarPathCard(
    modifier: Modifier,
    samples: List<MotionSample>,
    slice: RepSlice?,
    bestSlice: RepSlice?,
    compare: Boolean,
    onCompare: (Boolean) -> Unit,
    progressSec: Double?,
) {
    val comparison = bestSlice?.takeIf { it.index != slice?.index }
    val compareLabel = stringResource(R.string.report_compare_best)
    GlassCard(modifier = modifier, title = stringResource(R.string.report_bar_path_chart)) {
        BarPathChart(
            samples = samples,
            rep = slice,
            comparisonRep = if (compare) comparison else null,
            progressSec = progressSec,
            height = 96.dp,
            chartBackground = Color.Transparent,
        )
        if (comparison != null) {
            FilterChip(
                selected = compare,
                onClick = { onCompare(!compare) },
                label = { Text(compareLabel, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = VbtTheme.colors.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
internal fun RepBarsCard(
    modifier: Modifier,
    reps: List<RepMetrics>,
    position: Int,
    onSelect: (Int) -> Unit,
) {
    GlassCard(modifier = modifier, title = stringResource(R.string.report_per_rep_mcv)) {
        RepBarsContent(reps = reps, selectedPosition = position, onSelect = onSelect)
    }
}

/** Bottom action strip: stage correction, details, export (spec: compact action bar). */
@Composable
internal fun ReportActions(
    onStages: () -> Unit,
    onDetails: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = GlassColor,
    ) {
        Row(Modifier.padding(VbtSpacing.s), horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
            OutlinedButton(onClick = onStages, modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget)) {
                Text(stringResource(R.string.report_action_stages), maxLines = 1)
            }
            OutlinedButton(onClick = onDetails, modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget)) {
                Text(stringResource(R.string.report_action_details), maxLines = 1)
            }
            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget)) {
                Text(stringResource(R.string.report_export_title), maxLines = 1)
            }
        }
    }
}

/** Stage correction sheet (read-only by default; edit toggles drag handles). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StageSheet(
    onDismiss: () -> Unit,
    slice: RepSlice?,
    lift: Lift,
    editing: Boolean,
    onToggleEditing: () -> Unit,
    onRepChange: (RepSlice) -> Boolean,
    manualRejected: Boolean,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.padding(horizontal = VbtSpacing.l).padding(bottom = VbtSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(VbtSpacing.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(R.string.report_phase_timeline))
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onToggleEditing, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                    Text(stringResource(if (editing) R.string.report_phase_done else R.string.report_phase_edit))
                }
            }
            if (slice == null) {
                Text(stringResource(R.string.report_no_data), style = MaterialTheme.typography.bodyMedium)
            } else {
                PhaseTimeline(slice, onRepChange, editable = editing, lift = lift)
                PhaseLegend(lift = lift, present = slice.phases.map { it.type }.toSet())
                if (editing) {
                    Text(
                        stringResource(R.string.report_phase_edit_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = VbtTheme.colors.onSurfaceVariant,
                    )
                }
                if (manualRejected) {
                    MessageBanner(stringResource(R.string.report_manual_invalid))
                }
            }
        }
    }
}

/** Detail sheet: estimate, joint-angle state, quality, per-rep table. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailSheet(
    onDismiss: () -> Unit,
    lvp: LvpEstimate?,
    analyzePose: Boolean,
    quality: QualityFlags,
    fps: Double,
    reps: List<RepMetrics>,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = VbtSpacing.l).padding(bottom = VbtSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(VbtSpacing.m),
        ) {
            lvp?.let { LvpCard(it) }
            if (!analyzePose) JointAnglesDisabledCard()
            QualityCard(quality = quality, fps = fps)
            if (reps.isNotEmpty()) {
                SectionCard(padding = VbtSpacing.s) {
                    SectionLabel(stringResource(R.string.report_repetitions))
                    RepTable(reps)
                }
            }
        }
    }
}
