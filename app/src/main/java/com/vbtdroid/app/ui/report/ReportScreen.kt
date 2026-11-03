package com.vbtdroid.app.ui.report

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.app.analysis.AnalysisResult
import com.vbtdroid.app.export.CsvExporter
import com.vbtdroid.app.export.ExportMapper
import com.vbtdroid.core.io.JsonExport
import com.vbtdroid.core.model.MetricId
import com.vbtdroid.core.phases.RepSlice

@Composable
fun ReportScreen(
    result: AnalysisResult,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onShowHistory: () -> Unit = {},
) {
    var current by remember(result) { mutableStateOf(result) }
    var manualEditRejected by remember(result) { mutableStateOf(false) }
    val context = LocalContext.current
    var exportSucceeded by remember(result) { mutableStateOf<Boolean?>(null) }

    fun writeExport(uri: android.net.Uri?, content: () -> String) {
        if (uri == null) return
        exportSucceeded = runCatching {
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: error("Could not open export destination")
            stream.bufferedWriter(Charsets.UTF_8).use { it.write(content()) }
        }.isSuccess
    }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        writeExport(uri) { JsonExport.encode(ExportMapper.toSetExport(current)) }
    }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        writeExport(uri) { CsvExporter.export(current) }
    }

    fun updateSlice(updated: RepSlice): Boolean {
        val recomputed = recomputeManualRep(current, updated)
        return if (recomputed == null) {
            manualEditRejected = true
            false
        } else {
            current = recomputed
            manualEditRejected = false
            true
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onShowHistory) { Text(stringResource(R.string.history_title)) }
                    Button(onClick = onBack) { Text(stringResource(R.string.report_back)) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { jsonLauncher.launch("vbtdroid-set.json") }) {
                    Text(stringResource(R.string.report_export_json))
                }
                Button(onClick = { csvLauncher.launch("vbtdroid-set.csv") }) {
                    Text(stringResource(R.string.report_export_csv))
                }
            }
            Text(stringResource(R.string.report_saved_automatically))
            exportSucceeded?.let { succeeded ->
                Text(
                    stringResource(if (succeeded) R.string.report_export_succeeded else R.string.report_export_failed),
                    color = if (succeeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
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
            current.lvp?.let { estimate ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.report_lvp_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.report_lvp_points, estimate.pointCount))
                        Text(stringResource(R.string.report_v1rm_source, estimate.v1rmMps, estimate.v1rmSource))
                        estimate.model?.let { model ->
                            Text(stringResource(R.string.report_estimated_1rm, model.estimated1RmKg))
                            Text(stringResource(R.string.report_lvp_fit, model.slope, model.intercept, model.r2))
                        } ?: Text(stringResource(R.string.report_lvp_unavailable, estimate.unavailableReason.orEmpty()))
                    }
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
