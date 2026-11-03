package com.vbtdroid.app.ui.report

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.app.analysis.AnalysisResult
import com.vbtdroid.app.export.CsvExporter
import com.vbtdroid.app.export.ExportMapper
import com.vbtdroid.app.overlay.VideoOverlayExporter
import com.vbtdroid.app.ui.common.liftDisplayName
import com.vbtdroid.app.ui.common.variantDisplayName
import com.vbtdroid.app.ui.components.BannerTone
import com.vbtdroid.app.ui.components.MessageBanner
import com.vbtdroid.app.ui.components.PrimaryActionBar
import com.vbtdroid.app.ui.components.SectionCard
import com.vbtdroid.app.ui.components.SectionLabel
import com.vbtdroid.app.ui.components.StatusChip
import com.vbtdroid.app.ui.components.ChipTone
import com.vbtdroid.app.ui.components.reveal
import com.vbtdroid.app.ui.theme.Bg
import com.vbtdroid.app.ui.theme.MinTouchTarget
import com.vbtdroid.app.ui.theme.VbtSpacing
import com.vbtdroid.app.ui.theme.VbtTheme
import com.vbtdroid.core.io.JsonExport
import com.vbtdroid.core.phases.RepSlice
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    result: AnalysisResult,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onShowHistory: () -> Unit = {},
    onPersist: (suspend (AnalysisResult) -> Boolean)? = null,
) {
    var current by remember(result) { mutableStateOf(result) }
    var manualEditRejected by remember(result) { mutableStateOf(false) }
    var editingPhases by remember(result) { mutableStateOf(false) }
    var persistPending by remember(result) { mutableStateOf(false) }
    var persistFailed by remember(result) { mutableStateOf(false) }
    val latestPersist by rememberUpdatedState(onPersist)
    val context = LocalContext.current
    var exportSucceeded by remember(result) { mutableStateOf<Boolean?>(null) }
    var overlayExporting by remember(result) { mutableStateOf(false) }
    var overlayProgress by remember(result) { mutableStateOf(0f) }
    var overlaySucceeded by remember(result) { mutableStateOf<Boolean?>(null) }
    var overlayError by remember(result) { mutableStateOf<String?>(null) }
    val exportScope = rememberCoroutineScope()
    val overlayPreview = remember(context) {
        OverlayPreviewController(scope = exportScope, cacheRoot = context.cacheDir)
    }
    var previewSaving by remember(result) { mutableStateOf(false) }
    // Leaving the report (or the Activity) always removes the cached preview file.
    DisposableEffect(overlayPreview) {
        onDispose { overlayPreview.close() }
    }

    // UI-only state (spec §4.1).
    var selectedPosition by rememberSaveable(result) { mutableStateOf(0) }
    var compareWithBest by rememberSaveable(result) { mutableStateOf(false) }
    var exportSheetOpen by rememberSaveable(result) { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(current, persistPending) {
        if (persistPending) {
            delay(400)
            persistFailed = latestPersist?.invoke(current) == false
            persistPending = false
        }
    }

    fun flash(message: String) {
        exportScope.launch {
            coroutineScope {
                launch {
                    delay(2_500)
                    snackbar.currentSnackbarData?.dismiss()
                }
                snackbar.showSnackbar(message)
            }
        }
    }

    val previewSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4"),
    ) { uri ->
        val source = overlayPreview.file
        if (uri != null && source != null) {
            previewSaving = true
            exportScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                            source.inputStream().use { input -> input.copyTo(out) }
                        } ?: error("Could not open export destination")
                    }.isSuccess
                }
                previewSaving = false
                flash(
                    context.getString(
                        if (ok) R.string.report_export_succeeded else R.string.report_export_failed,
                    ),
                )
            }
        }
    }

    fun writeExport(uri: android.net.Uri?, content: () -> String) {
        if (uri == null) return
        val succeeded = runCatching {
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: error("Could not open export destination")
            stream.bufferedWriter(Charsets.UTF_8).use { it.write(content()) }
        }.isSuccess
        exportSucceeded = succeeded
        flash(
            context.getString(
                if (succeeded) R.string.report_export_succeeded else R.string.report_export_failed,
            ),
        )
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
    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4"),
    ) { uri ->
        if (uri != null) {
            overlayExporting = true
            overlayProgress = 0f
            overlaySucceeded = null
            overlayError = null
            val source = android.net.Uri.parse(current.videoUri)
            exportScope.launch {
                val failure = runCatching {
                    VideoOverlayExporter().export(
                        context = context,
                        sourceUri = source,
                        outputUri = uri,
                        result = current,
                        onProgress = { value -> overlayProgress = value },
                    )
                }.exceptionOrNull()
                overlayExporting = false
                overlaySucceeded = failure == null
                overlayError = failure?.message
                flash(
                    context.getString(
                        when {
                            failure == null -> R.string.report_export_overlay_succeeded
                            failure.message == VideoOverlayExporter.ERROR_NO_OBSERVATIONS ->
                                R.string.report_export_overlay_no_data
                            else -> R.string.report_export_overlay_failed
                        },
                        failure?.message ?: "",
                    ),
                )
            }
        }
    }

    fun updateSlice(updated: RepSlice): Boolean {
        val recomputed = recomputeManualRep(current, updated)
        return if (recomputed == null) {
            manualEditRejected = true
            false
        } else {
            current = recomputed
            manualEditRejected = false
            persistPending = true
            true
        }
    }

    val reps = current.reps
    val position = selectedPosition.coerceIn(0, (reps.size - 1).coerceAtLeast(0))
    val selectedRep = reps.getOrNull(position)
    val selectedSlice = selectedRep?.let { rep ->
        current.repSlices.firstOrNull { it.index == rep.repIndex } ?: current.repSlices.getOrNull(position)
    }
    val bestRep = reps.filter { it.mcvMps != null }.maxByOrNull { it.mcvMps ?: Double.NEGATIVE_INFINITY }
    val bestSlice = bestRep?.let { best -> current.repSlices.firstOrNull { it.index == best.repIndex } }
    val lift = current.lift
    val variant = current.variant
    val subtitle = buildSubtitle(current.loadKg, lift, variant)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Bg,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.report_back))
                    }
                },
                title = {
                    Column {
                        Text(
                            stringResource(R.string.report_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = VbtTheme.colors.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onShowHistory, modifier = Modifier.heightIn(min = MinTouchTarget)) {
                        Text(stringResource(R.string.history_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            PrimaryActionBar {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
                    OutlinedButton(
                        onClick = onShowHistory,
                        modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
                    ) {
                        Text(stringResource(R.string.history_title), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { exportSheetOpen = true },
                        modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
                    ) {
                        Text(stringResource(R.string.report_export_title), maxLines = 1)
                    }
                    // Primary action: preview first, save from the preview dialog.
                    Button(
                        onClick = { overlayPreview.start(context, android.net.Uri.parse(current.videoUri), current) },
                        modifier = Modifier.weight(1.6f).heightIn(min = MinTouchTarget),
                    ) {
                        Text(stringResource(R.string.report_overlay_preview_button), maxLines = 1)
                    }
                }
            }
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(horizontal = VbtSpacing.l, vertical = VbtSpacing.m),
            verticalArrangement = Arrangement.spacedBy(VbtSpacing.l),
        ) {
            item(key = "preview") {
                ReportPreview(
                    videoUri = current.videoUri,
                    selected = selectedSlice,
                    best = bestSlice.takeIf { compareWithBest },
                    samples = current.motionSamples,
                    modifier = Modifier.reveal(0),
                )
            }
            item(key = "hero") {
                MetricHero(
                    mcv = current.summary.mcvAvgMps,
                    bestMcv = current.summary.bestMcvMps,
                    velocityLossPct = current.summary.velocityLossPct,
                    repCount = current.summary.repCount,
                    loadKg = current.loadKg,
                    modifier = Modifier.reveal(1),
                )
            }
            item(key = "save") {
                if (persistFailed) {
                    MessageBanner(stringResource(R.string.report_save_failed), tone = BannerTone.ERROR)
                } else {
                    StatusChip(text = stringResource(R.string.report_saved_automatically), tone = ChipTone.PRIMARY)
                }
            }
            item(key = "grid") {
                SetMetricGrid(
                    repCount = current.summary.repCount,
                    volumeKg = current.summary.totalVolumeKg,
                    workJ = current.summary.totalWorkJ,
                    bestMcv = current.summary.bestMcvMps,
                    modifier = Modifier.reveal(2),
                )
            }
            if (reps.isEmpty()) {
                item(key = "empty") {
                    SectionCard {
                        SectionLabel(stringResource(R.string.report_repetitions))
                        Text(stringResource(R.string.report_no_data), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                item(key = "bars") {
                    RepMcvBars(
                        reps = reps,
                        selectedPosition = position,
                        onSelect = { selectedPosition = it },
                    )
                }
                item(key = "rep-selector") {
                    RepSelectorRow(
                        count = reps.size,
                        selectedPosition = position,
                        onSelect = { selectedPosition = it },
                        repIndexOf = { reps[it].repIndex },
                    )
                }
                if (selectedRep != null) {
                    item(key = "rep-detail-$position") {
                        RepDetailCard(
                            rep = selectedRep,
                            slice = selectedSlice,
                            samples = current.motionSamples,
                            bestSlice = bestSlice,
                            compareWithBest = compareWithBest,
                            onCompareChange = { compareWithBest = it },
                            editing = editingPhases,
                            onToggleEditing = { editingPhases = !editingPhases },
                            onRepChange = ::updateSlice,
                            manualEditRejected = manualEditRejected,
                            analyzePose = current.analyzePose,
                            frameAngles = current.frameAnglesByRep[selectedRep.repIndex].orEmpty(),
                            lift = lift,
                        )
                    }
                }
            }
            current.lvp?.let { estimate ->
                item(key = "lvp") { LvpCard(estimate) }
            }
            if (!current.analyzePose) {
                item(key = "angles-off") { JointAnglesDisabledCard() }
            }
            item(key = "quality") {
                QualityCard(
                    quality = current.quality,
                    fps = current.fps,
                    filterConfig = current.filterConfig,
                )
            }
            if (reps.isNotEmpty()) {
                item(key = "table") {
                    SectionCard(padding = VbtSpacing.s) {
                        SectionLabel(stringResource(R.string.report_repetitions))
                        RepTable(reps)
                    }
                }
            }
        }
    }

    OverlayPreviewDialog(
        controller = overlayPreview,
        saving = previewSaving,
        onSave = { previewSaveLauncher.launch("vbtdroid-overlay.mp4") },
    )

    if (exportSheetOpen) {
        ExportSheet(
            onDismiss = { exportSheetOpen = false },
            onExportJson = { jsonLauncher.launch("vbtdroid-set.json") },
            onExportCsv = { csvLauncher.launch("vbtdroid-set.csv") },
            onExportOverlay = { overlayLauncher.launch("vbtdroid-overlay.mp4") },
            overlayExporting = overlayExporting,
            overlayProgress = overlayProgress,
            exportSucceeded = exportSucceeded,
            overlaySucceeded = overlaySucceeded,
            overlayError = overlayError,
        )
    }
}

@Composable
private fun buildSubtitle(loadKg: Double?, lift: com.vbtdroid.core.model.Lift, variant: com.vbtdroid.core.model.LiftVariant): String {
    val identity = stringResource(
        R.string.history_set_identity,
        liftDisplayName(lift),
        variantDisplayName(variant),
    )
    val load = formatKgValue(loadKg) ?: return identity
    return "$identity · $load ${stringResource(R.string.unit_kg)}"
}
