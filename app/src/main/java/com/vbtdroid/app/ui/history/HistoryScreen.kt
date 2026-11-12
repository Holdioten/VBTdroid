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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
import com.vbtdroid.app.ui.components.ChipTone
import com.vbtdroid.app.ui.components.StatusChip
import com.vbtdroid.app.ui.theme.MinTouchTarget
import com.vbtdroid.app.ui.theme.VbtSpacing
import com.vbtdroid.app.ui.theme.VbtTheme
import com.vbtdroid.app.ui.common.liftDisplayName
import com.vbtdroid.app.ui.common.variantDisplayName
import com.vbtdroid.app.ui.report.FitVideo
import com.vbtdroid.app.ui.report.OverlayPlayback
import com.vbtdroid.app.ui.report.formatMcv
import com.vbtdroid.app.ui.report.formatMetricValue
import com.vbtdroid.app.ui.report.qualityWarningText
import com.vbtdroid.core.io.JsonExport
import com.vbtdroid.core.io.SetExport
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.model.LiftVariant
import com.vbtdroid.core.phases.PhaseType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.vbtdroid.app.ui.report.phaseLabelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: List<SetEntity>,
    estimates: List<com.vbtdroid.app.data.OneRmEstimateEntity>,
    detail: HistoryDetail?,
    onSelect: (String) -> Unit,
    onCloseDetail: () -> Unit,
    onBack: () -> Unit,
    onEstimate1Rm: () -> Unit,
) {
    // Back inside a history detail returns to the list, not out of history.
    BackHandler(enabled = detail != null, onBack = onCloseDetail)

    // Hoisted above the detail/list switch: a state created inside the list would be discarded
    // when the detail opens, and the list would jump back to the top on return.
    val listState = rememberLazyListState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
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
                    } else {
                        TextButton(onClick = onEstimate1Rm, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                            Text(stringResource(R.string.lvp_title))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { inner ->
        if (detail == null) {
            HistoryList(
                history = history,
                estimates = estimates,
                onSelect = onSelect,
                onBack = onBack,
                listState = listState,
                modifier = Modifier.padding(inner),
            )
        } else {
            HistoryDetailContent(detail = detail, modifier = Modifier.padding(inner))
        }
    }
}

@Composable
private fun HistoryList(
    history: List<SetEntity>,
    estimates: List<com.vbtdroid.app.data.OneRmEstimateEntity>,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    if (history.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.history_empty),
            actionLabel = stringResource(R.string.history_empty_cta),
            onAction = onBack,
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = VbtSpacing.l, vertical = VbtSpacing.m),
        verticalArrangement = Arrangement.spacedBy(VbtSpacing.m),
    ) {
        val latestEstimates = estimates.distinctBy { it.lift to it.variant }
        if (latestEstimates.isNotEmpty()) {
            item(key = "one_rm_estimates") {
                SectionCard(padding = VbtSpacing.m) {
                    SectionLabel(stringResource(R.string.lvp_recent_title))
                    val datePattern = stringResource(R.string.history_date_format)
                    latestEstimates.forEach { estimate ->
                        val date = remember(estimate.createdAtEpochMs, datePattern) {
                            SimpleDateFormat(datePattern, Locale.getDefault())
                                .format(Date(estimate.createdAtEpochMs))
                        }
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${liftLabel(estimate.lift)} / ${variantLabel(estimate.variant)}",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    String.format("%.1f", estimate.estimateKg),
                                    style = VbtTheme.numbers.displayMetricSm,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.unit_kg),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VbtTheme.colors.onSurfaceVariant,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    date,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VbtTheme.colors.onSurfaceVariant,
                                )
                                if (estimateIsStale(estimate, history)) {
                                    Spacer(Modifier.width(VbtSpacing.s))
                                    Text(
                                        stringResource(R.string.lvp_based_on_old_data),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        items(history, key = { it.setId }) { set ->
            SetCard(set = set, onClick = { onSelect(set.setId) })
        }
    }
}

/**
 * True when a set this estimate was fitted on has since been re-analysed from the same video
 * with the same inputs, i.e. the numbers behind the estimate are no longer what the app would
 * produce today.
 */
private fun estimateIsStale(
    estimate: com.vbtdroid.app.data.OneRmEstimateEntity,
    history: List<SetEntity>,
): Boolean {
    val used = estimate.setIds.split(',').filter { it.isNotBlank() }.toSet()
    if (used.isEmpty()) return false
    return history.filter { it.setId in used }.any { set ->
        set.videoUri.isNotBlank() && history.any { other ->
            other.videoUri == set.videoUri &&
                other.lift == set.lift &&
                other.variant == set.variant &&
                other.loadKg == set.loadKg &&
                other.createdAtEpochMs > set.createdAtEpochMs
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
        item(key = "overlay-video") { HistoryVideo(detail.set.createdAtEpochMs) }
        items(export.reps, key = { it.index }) { rep ->
            RepHistoryRow(rep = rep, lift = liftFromName(export.lift), maxMcv = maxMcv, missing = missing)
        }
    }
}

/**
 * The overlay MP4 is a cache artefact keyed by the analysis timestamp, so an old record may point
 * at a file the cache has already evicted. Show the video when it is still there, and say so
 * plainly when it is not.
 */
@Composable
private fun HistoryVideo(createdAtEpochMs: Long) {
    val context = LocalContext.current
    val file = remember(createdAtEpochMs) {
        java.io.File(context.cacheDir, "overlay-$createdAtEpochMs.mp4")
            .takeIf { it.isFile && it.length() > 0L }
    }
    if (file == null) {
        SectionCard(padding = VbtSpacing.m) {
            Text(
                stringResource(R.string.history_video_cleared),
                style = MaterialTheme.typography.bodySmall,
                color = VbtTheme.colors.onSurfaceVariant,
            )
        }
        return
    }
    val playback = remember(file) { OverlayPlayback() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        FitVideo(
            uri = android.net.Uri.fromFile(file),
            playback = playback,
            modifier = Modifier.fillMaxSize(),
        )
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
            Row(horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(
                    stringResource(
                        R.string.history_phase,
                        phaseLabel(lift, phase.type),
                        phase.startSec,
                        phase.endSec,
                    ),
                )
                if (phase.manual) {
                    StatusChip(stringResource(R.string.report_manual_phases), tone = ChipTone.WARNING)
                }
            }
        }
        rep.warnings.forEach { warning ->
            Text(
                qualityWarningText(warning),
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
