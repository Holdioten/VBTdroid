package com.vbtdroid.app.ui.report

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vbtdroid.app.R
import com.vbtdroid.app.ui.components.MessageBanner
import com.vbtdroid.app.ui.components.SectionCard
import com.vbtdroid.app.ui.components.SectionLabel
import com.vbtdroid.app.ui.theme.MinTouchTarget
import com.vbtdroid.app.ui.theme.ShapePill
import com.vbtdroid.app.ui.theme.VbtSpacing
import com.vbtdroid.app.ui.theme.VbtTheme
import com.vbtdroid.core.metrics.RepMetrics
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.phases.RepSlice
import com.vbtdroid.core.quality.QualityFlags
import com.vbtdroid.core.metrics.LvpEstimate
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt

/** Card scrim: #0A0E11 at 82%, so burned-in video text does not show through the cards. */
internal val GlassColor: Color
    @Composable get() = MaterialTheme.colorScheme.background.copy(alpha = 0.94f)

/**
 * Full-bleed video layer that letterboxes the frame instead of cropping it. The overlay burns its
 * card column into the source frame, so cover-scaling would cut those cards off; fit keeps the
 * whole frame visible and lets the theme background fill the bars.
 */
@Composable
internal fun FitVideo(uri: Uri, playback: OverlayPlayback, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FitFrame(context).also { frame ->
                val view = VideoView(context)
                frame.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                view.setOnErrorListener { _, _, _ ->
                    playback.failed = true
                    true
                }
                view.setOnPreparedListener { player ->
                    frame.setVideoSize(player.videoWidth, player.videoHeight)
                    player.isLooping = true
                    if (playback.targetMs > 0) view.seekTo(playback.targetMs)
                    view.start()
                    playback.playing = true
                }
                view.setVideoURI(uri)
                playback.view = view
            }
        },
    )
    DisposableEffect(uri) {
        onDispose { playback.release() }
    }
}

/** Layout that fits its single child inside the parent, preserving the video's aspect ratio. */
private class FitFrame(context: Context) : FrameLayout(context) {
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
        val (cw, ch) = fitSize(w, h)
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

    private fun fitSize(w: Int, h: Int): Pair<Int, Int> {
        if (videoWidth <= 0 || videoHeight <= 0 || w <= 0 || h <= 0) return w to h
        val scale = min(w.toFloat() / videoWidth, h.toFloat() / videoHeight)
        return (videoWidth * scale).roundToInt() to (videoHeight * scale).roundToInt()
    }
}

/** Play/pause and seek for the background video. State is driven by icons, never by text. */
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
    // Label describes the action the control performs, so it matches the glyph shown.
    val actionLabel = stringResource(
        if (playback.playing) R.string.report_overlay_pause else R.string.report_overlay_play,
    )
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s),
    ) {
        Surface(
            shape = CircleShape,
            color = GlassColor,
            modifier = Modifier.size(MinTouchTarget).clickable { playback.togglePlay() },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (playback.playing) {
                    PauseGlyph(Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                } else {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = actionLabel,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
        Slider(
            value = if (duration > 0f) playback.positionMs / duration else 0f,
            onValueChange = { fraction -> playback.seekTo((fraction * duration).toInt()) },
            enabled = duration > 0f,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Pause glyph drawn as two rounded bars: the material core icon set ships no pause vector. */
@Composable
private fun PauseGlyph(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Canvas(modifier) {
        val barWidth = size.width * 0.13f
        val barHeight = size.height * 0.52f
        val top = (size.height - barHeight) / 2f
        val gap = size.width * 0.1f
        val center = size.width / 2f
        val radius = CornerRadius(barWidth / 2f)
        drawRoundRect(
            color = tint,
            topLeft = Offset(center - gap / 2f - barWidth, top),
            size = Size(barWidth, barHeight),
            cornerRadius = radius,
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(center + gap / 2f, top),
            size = Size(barWidth, barHeight),
            cornerRadius = radius,
        )
    }
}

/**
 * Top strip over the video: back, identity (one line), history. The status chip drops to a second
 * row only when there is something to report.
 */
@Composable
internal fun ReportHeader(
    identity: String,
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
        if (status != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                status()
            }
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

/** Bottom action strip: stage correction, details, save the cached overlay video. */
@Composable
internal fun ReportActions(
    onStages: () -> Unit,
    onDetails: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
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
            OutlinedButton(
                onClick = onSave,
                enabled = saveEnabled,
                modifier = Modifier.weight(1.3f).heightIn(min = MinTouchTarget),
            ) {
                Text(stringResource(R.string.report_save_overlay), maxLines = 1)
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

