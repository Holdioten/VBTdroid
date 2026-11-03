package com.vbtdroid.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.app.data.HistoryDetail
import com.vbtdroid.app.data.SetEntity
import com.vbtdroid.app.ui.components.EmptyState
import com.vbtdroid.app.ui.components.MessageBanner
import com.vbtdroid.app.ui.components.BannerTone
import com.vbtdroid.app.ui.components.SectionCard
import com.vbtdroid.app.ui.components.SectionLabel
import com.vbtdroid.app.ui.components.StatusChip
import com.vbtdroid.app.ui.theme.Bg
import com.vbtdroid.app.ui.theme.MinTouchTarget
import com.vbtdroid.app.ui.theme.VbtSpacing
import com.vbtdroid.app.ui.theme.VbtTheme
import com.vbtdroid.app.ui.common.liftDisplayName
import com.vbtdroid.app.ui.common.variantDisplayName
import com.vbtdroid.app.ui.report.formatMcv
import com.vbtdroid.app.ui.report.formatMetricValue
import com.vbtdroid.app.ui.report.qualityWarningText
import com.vbtdroid.core.io.JsonExport
import com.vbtdroid.core.io.SetExport
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.model.LiftVariant
import com.vbtdroid.core.phases.PhaseType
import com.vbtdroid.app.ui.report.phaseLabelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: List<SetEntity>,
    detail: HistoryDetail?,
    onSelect: (String) -> Unit,
    onCloseDetail: () -> Unit,
    onBack: () -> Unit,
) {
    // Back inside a history detail returns to the list, not out of history.
    BackHandler(enabled = detail != null, onBack = onCloseDetail)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Bg,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = if (detail == null) onBack else onCloseDetail) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.report_back))
                    }
                },
                title = {
                    Text(
                        stringResource(if (detail == null) R.string.history_title else R.string.history_detail_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                actions = {
                    if (detail != null) {
                        TextButton(onClick = onCloseDetail, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                            Text(stringResource(R.string.history_close_detail))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
            )
        },
    ) { inner ->
        if (detail == null) {
            HistoryList(history = history, onSelect = onSelect, onBack = onBack, modifier = Modifier.padding(inner))
        } else {
            HistoryDetailContent(detail = detail, modifier = Modifier.padding(inner))
        }
    }
}

@Composable
private fun HistoryList(
    history: List<SetEntity>,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (history.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.history_empty),
            body = stringResource(R.string.history_empty_hint),
            actionLabel = stringResource(R.string.history_empty_cta),
            onAction = onBack,
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = VbtSpacing.l, vertical = VbtSpacing.m),
        verticalArrangement = Arrangement.spacedBy(VbtSpacing.m),
    ) {
        items(history, key = { it.setId }) { set ->
            SetCard(set = set, onClick = { onSelect(set.setId) })
        }
    }
}

@Composable
internal fun liftLabel(name: String): String =
    runCatching { liftDisplayName(Lift.valueOf(name)) }.getOrDefault(name)

@Composable
internal fun variantLabel(name: String): String =
    runCatching { variantDisplayName(LiftVariant.valueOf(name)) }.getOrDefault(name)

@Composable
private fun HistoryDetailContent(detail: HistoryDetail, modifier: Modifier = Modifier) {
    val export = remember(detail.set.setId) { runCatching { JsonExport.decode(detail.set.exportJson) }.getOrNull() }
    if (export == null) {
        Column(modifier.fillMaxSize().padding(VbtSpacing.l)) {
            MessageBanner(stringResource(R.string.history_export_unavailable), tone = BannerTone.WARNING)
        }
        return
    }
    val missing = stringResource(R.string.report_value_missing)
    val maxMcv = export.reps.mapNotNull { it.metrics["mcv_mps"] }.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = VbtSpacing.l, vertical = VbtSpacing.m),
        verticalArrangement = Arrangement.spacedBy(VbtSpacing.m),
    ) {
        item(key = "header") {
            HistoryHeader(export = export, detail = detail, missing = missing)
        }
        items(export.reps, key = { it.index }) { rep ->
            RepHistoryRow(rep = rep, lift = liftFromName(export.lift), maxMcv = maxMcv, missing = missing)
        }
    }
}

@Composable
private fun HistoryHeader(export: SetExport, detail: HistoryDetail, missing: String) {
    SectionCard(padding = VbtSpacing.l) {
        SectionLabel(stringResource(R.string.history_set_identity, liftLabel(export.lift), variantLabel(export.variant)))
        Row(horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
            StatusChip(
                stringResource(
                    R.string.history_camera_geometry,
                    export.plateDiameterMm,
                    export.cameraAngleDeg,
                ),
            )
        }
        Text(
            stringResource(R.string.history_video_uri, export.videoUri.ifBlank { missing }),
            style = MaterialTheme.typography.labelSmall,
            color = VbtTheme.colors.onSurfaceVariant,
            maxLines = 1,
        )
        export.lvp?.let { lvp ->
            Text(
                stringResource(R.string.report_lvp_points, lvp.pointCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            val oneRm = lvp.estimated1RmKg
            if (oneRm != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        String.format("%.1f", oneRm),
                        style = VbtTheme.numbers.displayMetricSm,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(VbtSpacing.s))
                    Text(stringResource(R.string.unit_kg), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(VbtSpacing.s))
                    StatusChip(stringResource(R.string.report_estimated_badge))
                }
            } else {
                Text(
                    stringResource(R.string.report_lvp_unavailable, lvp.unavailableReason.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = VbtTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RepHistoryRow(rep: com.vbtdroid.core.io.RepExport, lift: Lift, maxMcv: Double, missing: String) {
    val mcv = rep.metrics["mcv_mps"]
    val rom = rep.metrics["rom_concentric_m"]
    SectionCard(padding = VbtSpacing.m) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.report_rep_number, rep.index),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatMcv(mcv) ?: missing,
                style = VbtTheme.numbers.headlineMetric,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 6.dp)
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(3.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(((mcv ?: 0.0) / maxMcv).toFloat().coerceIn(0f, 1f))
                        .height(6.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
                )
            }
            Text(
                stringResource(R.string.history_rep_rom, rom?.let { String.format("%.3f", it) } ?: missing),
                style = MaterialTheme.typography.labelSmall,
                color = VbtTheme.colors.onSurfaceVariant,
            )
        }
        rep.phases.forEach { phase ->
            StatusChip(
                stringResource(
                    R.string.history_phase,
                    phaseLabel(lift, phase.type),
                    phase.startSec,
                    phase.endSec,
                    phase.manual,
                ),
                maxWidth = null,
            )
        }
        rep.warnings.forEach { warning ->
            Text(
                stringResource(R.string.report_warning_item, qualityWarningText(warning)),
                style = MaterialTheme.typography.bodySmall,
                color = VbtTheme.colors.warning,
            )
        }
    }
}

private fun liftFromName(name: String): Lift = runCatching { Lift.valueOf(name) }.getOrDefault(Lift.SQUAT)

/** Stored phase type is the enum name; unknown names fall back to the missing-value dash. */
@Composable
private fun phaseLabel(lift: Lift, type: String): String {
    val phaseType = runCatching { PhaseType.valueOf(type) }.getOrNull()
    return if (phaseType == null) stringResource(R.string.report_value_missing)
    else stringResource(phaseLabelRes(lift, phaseType))
}
