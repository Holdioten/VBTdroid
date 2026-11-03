package com.vbtdroid.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.app.data.HistoryDetail
import com.vbtdroid.app.data.SetEntity
import com.vbtdroid.core.io.JsonExport

@Composable
fun HistoryScreen(
    history: List<SetEntity>,
    detail: HistoryDetail?,
    onSelect: (String) -> Unit,
    onCloseDetail: () -> Unit,
    onBack: () -> Unit,
) {
    val missing = stringResource(R.string.report_value_missing)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineSmall)
                Button(onClick = onBack) { Text(stringResource(R.string.report_back)) }
            }
        }
        if (detail == null) {
            if (history.isEmpty()) item { Text(stringResource(R.string.history_empty)) }
            items(history, key = { it.setId }) { set ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.history_set_identity, set.lift, set.variant))
                        Text(stringResource(R.string.history_set_load, set.loadKg?.toString() ?: missing))
                        Text(stringResource(R.string.history_set_mcv, set.mcvAvgMps?.toString() ?: missing))
                        OutlinedButton(onClick = { onSelect(set.setId) }) {
                            Text(stringResource(R.string.history_open_detail))
                        }
                    }
                }
            }
        } else {
            item {
                OutlinedButton(onClick = onCloseDetail) { Text(stringResource(R.string.history_close_detail)) }
            }
            val export = runCatching { JsonExport.decode(detail.set.exportJson) }.getOrNull()
            if (export == null) {
                item { Text(stringResource(R.string.history_export_unavailable)) }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.history_detail_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.history_set_identity, export.lift, export.variant))
                        Text(stringResource(R.string.history_video_uri, export.videoUri))
                        Text(stringResource(R.string.history_camera_geometry, export.plateDiameterMm, export.cameraAngleDeg))
                        export.lvp?.let { lvp ->
                            Text(stringResource(R.string.report_lvp_points, lvp.pointCount))
                            lvp.estimated1RmKg?.let { Text(stringResource(R.string.report_estimated_1rm, it)) }
                                ?: Text(stringResource(R.string.report_lvp_unavailable, lvp.unavailableReason.orEmpty()))
                        }
                    }
                }
                items(export.reps, key = { it.index }) { rep ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.report_rep_number, rep.index))
                            Text(stringResource(R.string.history_rep_mcv, rep.metrics["mcv_mps"]?.toString() ?: missing))
                            Text(stringResource(R.string.history_rep_rom, rep.metrics["rom_concentric_m"]?.toString() ?: missing))
                            rep.phases.forEach { phase ->
                                Text(
                                    stringResource(
                                        R.string.history_phase,
                                        phase.type,
                                        phase.startSec,
                                        phase.endSec,
                                        phase.manual,
                                    ),
                                )
                            }
                            rep.warnings.forEach { warning ->
                                Text(stringResource(R.string.report_warning_item, warning))
                            }
                        }
                    }
                }
            }
        }
    }
}
