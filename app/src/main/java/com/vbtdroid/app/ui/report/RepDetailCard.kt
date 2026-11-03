package com.vbtdroid.app.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.app.analysis.FrameAngles
import com.vbtdroid.app.ui.components.ChipTone
import com.vbtdroid.app.ui.components.MessageBanner
import com.vbtdroid.app.ui.components.SectionCard
import com.vbtdroid.app.ui.components.SectionLabel
import com.vbtdroid.app.ui.components.SegmentedPicker
import com.vbtdroid.app.ui.components.StatusChip
import com.vbtdroid.app.ui.components.BannerTone
import com.vbtdroid.app.ui.components.confidenceColor
import com.vbtdroid.app.ui.theme.MinTouchTarget
import com.vbtdroid.app.ui.theme.VbtSpacing
import com.vbtdroid.app.ui.theme.VbtTheme
import com.vbtdroid.core.metrics.MotionSample
import com.vbtdroid.core.metrics.RepMetrics
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.model.MetricId
import com.vbtdroid.core.phases.PhaseType
import com.vbtdroid.core.phases.RepSlice

/**
 * Shared detail area for the selected rep (spec §4.4). One card serves every rep; the rep chips and
 * bars above it switch the content.
 */
@Composable
fun RepDetailCard(
    rep: RepMetrics,
    slice: RepSlice?,
    samples: List<MotionSample>,
    bestSlice: RepSlice?,
    compareWithBest: Boolean,
    onCompareChange: (Boolean) -> Unit,
    editing: Boolean,
    onToggleEditing: () -> Unit,
    onRepChange: (RepSlice) -> Boolean,
    manualEditRejected: Boolean,
    analyzePose: Boolean,
    frameAngles: List<FrameAngles>,
    lift: Lift,
    modifier: Modifier = Modifier,
) {
    val comparisonSlice = bestSlice?.takeIf { it.index != rep.repIndex }
    val compareNoneLabel = stringResource(R.string.report_compare_none)
    val compareBestLabel = stringResource(R.string.report_compare_best)
    SectionCard(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.report_rep_number, rep.repIndex))
            Spacer(Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
            StatusChip(
                text = stringResource(
                    R.string.history_rep_rom,
                    formatMetricValue(rep.romConcentricM, MetricId.ROM_TOTAL),
                ),
            )
            slice?.phases?.firstOrNull { it.type == PhaseType.CONCENTRIC }?.let { phase ->
                StatusChip(
                    text = stringResource(
                        R.string.report_concentric_duration,
                        phase.endSec - phase.startSec,
                    ),
                )
            }
        }

        Text(
            stringResource(R.string.report_velocity_chart),
            style = MaterialTheme.typography.labelSmall,
            color = VbtTheme.colors.onSurfaceVariant,
        )
        VelocityChart(samples, slice, rep.mcvMps)

        Text(
            stringResource(R.string.report_bar_path_chart),
            style = MaterialTheme.typography.labelSmall,
            color = VbtTheme.colors.onSurfaceVariant,
        )
        BarPathChart(samples, slice, comparisonRep = if (compareWithBest) comparisonSlice else null)
        SegmentedPicker(
            options = listOf(false, true),
            selected = compareWithBest && comparisonSlice != null,
            label = { if (it) compareBestLabel else compareNoneLabel },
            onSelect = onCompareChange,
            enabled = comparisonSlice != null,
        )

        if (slice != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(R.string.report_phase_timeline))
                Spacer(Modifier.weight(1f))
                if (slice.phases.any { it.manual }) {
                    StatusChip(text = stringResource(R.string.report_manual_phases), tone = ChipTone.WARNING)
                    Spacer(Modifier.width(VbtSpacing.s))
                }
                OutlinedButton(
                    onClick = onToggleEditing,
                    modifier = Modifier.heightIn(min = MinTouchTarget),
                ) {
                    Text(stringResource(if (editing) R.string.report_phase_done else R.string.report_phase_edit))
                }
            }
            Text(
                stringResource(R.string.report_phase_help),
                style = MaterialTheme.typography.bodySmall,
                color = VbtTheme.colors.onSurfaceVariant,
            )
            PhaseTimeline(slice, onRepChange, editable = editing)
            PhaseLegend()
            if (editing) {
                Text(
                    stringResource(R.string.report_phase_edit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = VbtTheme.colors.onSurfaceVariant,
                )
            }
            if (manualEditRejected) {
                MessageBanner(stringResource(R.string.report_manual_invalid), tone = BannerTone.ERROR)
            }

            if (analyzePose) {
                Text(
                    stringResource(R.string.report_key_frame_angles),
                    style = MaterialTheme.typography.titleSmall,
                )
                val repAngles = frameAngles
                if (repAngles.isNotEmpty()) {
                    KeyFrameGrid(
                        frames = selectKeyFrameAngles(rep, slice, samples, repAngles),
                        lift = lift,
                    )
                } else {
                    Text(
                        stringResource(R.string.report_joint_angles_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = VbtTheme.colors.onSurfaceVariant,
                    )
                }
            }
        } else if (manualEditRejected) {
            MessageBanner(stringResource(R.string.report_manual_invalid), tone = BannerTone.ERROR)
        }
    }
}

private data class JointRow(val labelRes: Int, val key: String, val value: Double?)

@Composable
private fun KeyFrameGrid(frames: List<KeyFrameAngles>, lift: Lift) {
    val headlineKey = if (lift == Lift.BENCH) "elbow_angle_deg" else "knee_angle_deg"
    Column(verticalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
        frames.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
                pair.forEach { frame ->
                    KeyFrameCell(frame, headlineKey, Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun KeyFrameCell(frame: KeyFrameAngles, headlineKey: String, modifier: Modifier) {
    val angles = frame.angles
    val joints = listOf(
        JointRow(R.string.metric_hip_angle, "hip_angle_deg", angles.hipDeg),
        JointRow(R.string.metric_knee_angle, "knee_angle_deg", angles.kneeDeg),
        JointRow(R.string.metric_ankle_angle, "ankle_angle_deg", angles.ankleDeg),
        JointRow(R.string.metric_trunk_angle, "trunk_angle_deg", angles.trunkDeg),
        JointRow(R.string.metric_shoulder_angle, "shoulder_angle_deg", angles.shoulderDeg),
        JointRow(R.string.metric_elbow_angle, "elbow_angle_deg", angles.elbowDeg),
        JointRow(R.string.metric_wrist_angle, "wrist_angle_deg", angles.wristDeg),
    )
    val headline = joints.firstOrNull { it.key == headlineKey && it.value != null }
        ?: joints.firstOrNull { it.value != null }
    val others = joints.filter { it.value != null && it.key != headline?.key }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(VbtSpacing.m), verticalArrangement = Arrangement.spacedBy(VbtSpacing.xs)) {
            Text(
                stringResource(keyFrameLabel(frame.kind)),
                style = MaterialTheme.typography.labelSmall,
                color = VbtTheme.colors.onSurfaceVariant,
            )
            if (headline != null) {
                Text(
                    stringResource(R.string.report_angle_value, headline.value ?: 0.0),
                    style = VbtTheme.numbers.headlineMetric,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                ConfidenceBadge(angles.confidenceByJoint[headline.key] ?: angles.confidence)
            }
            others.forEach { joint ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(joint.labelRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = VbtTheme.colors.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.report_angle_value, joint.value ?: 0.0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (headline == null) {
                Text(
                    stringResource(R.string.report_joint_angles_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = VbtTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConfidenceBadge(value: Double) {
    val color = confidenceColor(value)
    Surface(shape = RoundedCornerShape(999.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            stringResource(R.string.report_confidence_chip, value),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
