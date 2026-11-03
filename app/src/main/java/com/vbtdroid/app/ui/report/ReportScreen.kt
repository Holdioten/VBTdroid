package com.vbtdroid.app.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.app.analysis.AnalysisResult
import com.vbtdroid.app.analysis.summarize
import com.vbtdroid.core.metrics.RepMetricsCalculator
import com.vbtdroid.core.model.MetricId
import com.vbtdroid.core.phases.RepSlice

@Composable
fun ReportScreen(
    result: AnalysisResult,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var current by remember(result) { mutableStateOf(result) }
    var manualEditRejected by remember(result) { mutableStateOf(false) }

    fun updateSlice(updated: RepSlice) {
        try {
            val bestOther = current.reps.filter { it.repIndex != updated.index }
                .maxOfOrNull { it.mcvMps }
            val recomputed = RepMetricsCalculator.compute(
                rep = updated,
                samples = current.motionSamples,
                loadKg = current.loadKg,
                bestMcvMps = bestOther,
            )
            val reps = current.reps.map { if (it.repIndex == updated.index) recomputed else it }
            current = current.copy(
                reps = reps,
                summary = summarize(reps, current.loadKg),
                repSlices = current.repSlices.map { if (it.index == updated.index) updated else it },
            )
            manualEditRejected = false
        } catch (_: IllegalArgumentException) {
            manualEditRejected = true
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.report_title), style = MaterialTheme.typography.headlineSmall)
                Button(onClick = onBack) { Text(stringResource(R.string.report_back)) }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.report_set_summary), style = MaterialTheme.typography.titleMedium)
                SummaryCard(
                    label = stringResource(R.string.report_total_reps),
                    value = current.summary.repCount.toString(),
                )
                SummaryCard(
                    label = stringResource(
                        R.string.report_average_of,
                        metricDisplayName(MetricId.MEAN_CONCENTRIC_VELOCITY),
                    ),
                    value = formatMetricValue(
                        current.summary.mcvAvgMps,
                        MetricId.MEAN_CONCENTRIC_VELOCITY,
                    ),
                )
                SummaryCard(
                    label = stringResource(
                        R.string.report_best_of,
                        metricDisplayName(MetricId.MEAN_CONCENTRIC_VELOCITY),
                    ),
                    value = formatMetricValue(
                        current.summary.bestMcvMps,
                        MetricId.MEAN_CONCENTRIC_VELOCITY,
                    ),
                )
                SummaryCard(
                    label = metricDisplayName(MetricId.SET_VELOCITY_LOSS),
                    value = formatMetricValue(
                        current.summary.velocityLossPct,
                        MetricId.SET_VELOCITY_LOSS,
                    ),
                )
                SummaryCard(
                    label = metricDisplayName(MetricId.VOLUME_LOAD),
                    value = formatMetricValue(current.summary.totalVolumeKg, MetricId.VOLUME_LOAD),
                )
                SummaryCard(
                    label = metricDisplayName(MetricId.WORK),
                    value = formatMetricValue(current.summary.totalWorkJ, MetricId.WORK),
                )
                if (current.loadKg != null) {
                    Text(
                        stringResource(R.string.report_external_load_assumption),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.report_repetitions), style = MaterialTheme.typography.titleMedium)
                if (current.reps.isEmpty()) {
                    Text(stringResource(R.string.report_no_data))
                } else {
                    RepTable(current.reps)
                }
            }
        }
        itemsIndexed(current.reps) { index, repMetrics ->
            val slice = current.repSlices.getOrNull(index)
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.report_rep_number, repMetrics.repIndex),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(stringResource(R.string.report_velocity_chart))
                    VelocityChart(current.motionSamples, slice)
                    Text(stringResource(R.string.report_bar_path_chart))
                    BarPathChart(current.motionSamples, slice)
                    if (slice != null) {
                        Text(stringResource(R.string.report_phase_timeline))
                        PhaseTimeline(slice, ::updateSlice)
                        if (slice.phases.any { it.manual }) {
                            Text(stringResource(R.string.report_manual_phases))
                        }
                    }
                    if (manualEditRejected) {
                        Text(
                            stringResource(R.string.report_manual_invalid),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(stringResource(R.string.report_quality), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.report_fps, current.fps))
                    Text(
                        stringResource(
                            R.string.report_quality_values,
                            current.quality.trackConfidence,
                            current.quality.occlusionPct,
                            current.quality.repConfidence,
                            current.quality.calibrationConfidence,
                        ),
                    )
                    if (current.quality.warnings.isEmpty()) {
                        Text(stringResource(R.string.report_no_warnings))
                    } else {
                        current.quality.warnings.forEach { warning ->
                            Text(stringResource(R.string.report_warning_item, qualityWarningText(warning)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun qualityWarningText(warning: String): String = stringResource(
    when {
        warning == "LOW_TRACK_CONFIDENCE" -> R.string.quality_low_track_confidence
        warning == "HIGH_OCCLUSION" -> R.string.quality_high_occlusion
        warning == "CAMERA_NOT_LEVEL" -> R.string.quality_camera_not_level
        warning == "LOW_CALIBRATION" -> R.string.quality_low_calibration
        warning == "LOW_REP_CONFIDENCE" -> R.string.quality_low_rep_confidence
        warning == "POSE_UNAVAILABLE" -> R.string.quality_pose_unavailable
        warning == "POSE_PARTIAL" -> R.string.quality_pose_partial
        warning == "NO_VALID_REPS" -> R.string.quality_no_valid_reps
        warning.startsWith("REP_SKIPPED_") -> R.string.quality_rep_skipped
        else -> R.string.quality_unknown
    },
)
