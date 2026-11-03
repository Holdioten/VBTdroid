package com.vbtdroid.app.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.app.ui.components.ChipTone
import com.vbtdroid.app.ui.components.MeterRow
import com.vbtdroid.app.ui.components.SectionCard
import com.vbtdroid.app.ui.components.SectionLabel
import com.vbtdroid.app.ui.components.StatusChip
import com.vbtdroid.app.ui.theme.MinTouchTarget
import com.vbtdroid.app.ui.theme.VbtSpacing
import com.vbtdroid.app.ui.theme.VbtTheme
import com.vbtdroid.core.io.FilterConfigExport
import com.vbtdroid.core.metrics.LvpEstimate
import com.vbtdroid.core.quality.QualityFlags

/** Estimated 1RM card. The value is always labelled as an estimate (spec §4.5). */
@Composable
fun LvpCard(estimate: LvpEstimate, modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier) {
        SectionLabel(stringResource(R.string.report_lvp_title))
        val model = estimate.model
        if (model != null) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
                Text(
                    text = String.format("%.1f", model.estimated1RmKg),
                    style = VbtTheme.numbers.displayMetricSm,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.unit_kg),
                    style = MaterialTheme.typography.titleMedium,
                    color = VbtTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                StatusChip(text = stringResource(R.string.report_estimated_badge))
            }
            Text(
                stringResource(R.string.report_lvp_fit, model.slope, model.intercept, model.r2),
                style = MaterialTheme.typography.labelSmall,
                color = VbtTheme.colors.onSurfaceVariant,
            )
        } else {
            Text(
                stringResource(R.string.report_lvp_unavailable, estimate.unavailableReason.orEmpty()),
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { (estimate.pointCount / 3f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            Text(
                stringResource(R.string.report_lvp_points, estimate.pointCount),
                style = MaterialTheme.typography.labelSmall,
                color = VbtTheme.colors.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.report_v1rm_source, estimate.v1rmMps, estimate.v1rmSource),
            style = MaterialTheme.typography.labelSmall,
            color = VbtTheme.colors.onSurfaceVariant,
        )
    }
}

/** Shown only when joint-angle analysis was switched off for this run. */
@Composable
fun JointAnglesDisabledCard(modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier) {
        SectionLabel(stringResource(R.string.report_joint_angles_disabled_title))
        Text(
            stringResource(R.string.report_joint_angles_disabled_hint),
            style = MaterialTheme.typography.bodySmall,
            color = VbtTheme.colors.onSurfaceVariant,
        )
    }
}

/** Collapsible quality card (spec §4.6). Collapsed by default; state survives rotation. */
@Composable
fun QualityCard(
    quality: QualityFlags,
    fps: Double,
    filterConfig: FilterConfigExport?,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard(modifier = modifier, padding = VbtSpacing.m) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s),
        ) {
            SectionLabel(stringResource(R.string.report_quality))
            Spacer(Modifier.weight(1f))
            if (quality.warnings.isEmpty()) {
                StatusChip(text = stringResource(R.string.report_no_warnings), tone = ChipTone.PRIMARY)
            } else {
                StatusChip(
                    text = stringResource(R.string.report_quality_summary_warnings, quality.warnings.size),
                    tone = ChipTone.WARNING,
                )
            }
        }
        if (expanded) {
            MeterRow(
                label = stringResource(R.string.report_quality_meter_track),
                fraction = quality.trackConfidence.toFloat(),
                valueText = String.format("%.2f", quality.trackConfidence),
                color = MaterialTheme.colorScheme.primary,
            )
            MeterRow(
                label = stringResource(R.string.report_quality_meter_occlusion),
                fraction = (quality.occlusionPct / 100.0).toFloat(),
                valueText = String.format("%.1f%%", quality.occlusionPct),
                color = VbtTheme.colors.warning,
            )
            MeterRow(
                label = stringResource(R.string.report_quality_meter_rep),
                fraction = quality.repConfidence.toFloat(),
                valueText = String.format("%.2f", quality.repConfidence),
                color = MaterialTheme.colorScheme.primary,
            )
            MeterRow(
                label = stringResource(R.string.report_quality_meter_calibration),
                fraction = quality.calibrationConfidence.toFloat(),
                valueText = String.format("%.2f", quality.calibrationConfidence),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.report_fps, fps),
                style = MaterialTheme.typography.labelSmall,
                color = VbtTheme.colors.onSurfaceVariant,
            )
            filterConfig?.let { filter ->
                Text(
                    stringResource(
                        R.string.report_filter_config,
                        filter.method,
                        filter.meanWindow,
                        filter.meanPolyOrder,
                        filter.peakWindow,
                        filter.peakPolyOrder,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = VbtTheme.colors.onSurfaceVariant,
                )
            }
            quality.warnings.forEach { warning ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(VbtTheme.colors.warning))
                    Text(
                        qualityWarningText(warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
