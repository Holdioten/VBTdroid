package com.vbtdroid.app.ui.report

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.SnackbarHost
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import com.vbtdroid.app.ui.components.ChipTone
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
    val subtitle = buildSubtitle(current.loadKg, current.lift, current.variant)
    val playback = remember(result) { OverlayPlayback() }
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    var stageSheetOpen by rememberSaveable(result) { mutableStateOf(false) }
    var detailSheetOpen by rememberSaveable(result) { mutableStateOf(false) }

    // Overlay video is generated in the background as soon as the report opens; the first-frame
    // fallback shows until it is ready. Cache is removed when the report leaves composition.
    LaunchedEffect(result) {
        if (current.observations.isNotEmpty() && current.videoUri.isNotBlank()) {
            overlayPreview.start(context, android.net.Uri.parse(current.videoUri), current)
        }
    }
    // Selecting a rep seeks the overlay video to that rep's start.
    LaunchedEffect(selectedSlice) {
        selectedSlice?.let { playback.seekTo((it.startSec * 1000).toInt()) }
    }

    val overlayFile = overlayPreview.file
    val identity = subtitle
    val repLabel = if (reps.isEmpty()) {
        stringResource(R.string.report_value_missing)
    } else {
        stringResource(R.string.report_rep_index, position + 1, reps.size)
    }
    val header: @Composable (Modifier) -> Unit = { modifier ->
        ReportHeader(
            identity = identity,
            repLabel = repLabel,
            canPrev = position > 0,
            canNext = position < reps.size - 1,
            onPrev = { selectedPosition = position - 1 },
            onNext = { selectedPosition = position + 1 },
            onBack = onBack,
            onHistory = onShowHistory,
            status = {
                when {
                    persistFailed -> StatusChip(text = stringResource(R.string.report_save_failed), tone = ChipTone.ERROR)
                    overlayPreview.busy -> StatusChip(
                        text = stringResource(R.string.report_export_overlay_progress, (overlayPreview.progress * 100f).toInt()),
                    )
                    else -> Unit
                }
            },
        )
    }
    val cardsContent: @Composable (Modifier) -> Unit = { cardModifier ->
        if (reps.isEmpty()) {
            GlassCard(modifier = cardModifier) {
                Text(stringResource(R.string.report_no_data), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            MainMcvCard(
                modifier = cardModifier,
                mcv = current.summary.mcvAvgMps,
                bestMcv = current.summary.bestMcvMps,
                velocityLossPct = current.summary.velocityLossPct,
                repCount = current.summary.repCount,
            )
            selectedSlice?.let { slice ->
                VelocityCard(cardModifier, current.motionSamples, slice, selectedRep?.mcvMps)
                if (current.analyzePose) {
                    selectedRep?.let { rep ->
                        AngleCard(
                            modifier = cardModifier,
                            rep = rep,
                            slice = slice,
                            samples = current.motionSamples,
                            angles = current.frameAnglesByRep[rep.repIndex].orEmpty(),
                            lift = current.lift,
                        )
                    }
                }
                BarPathCard(
                    modifier = cardModifier,
                    samples = current.motionSamples,
                    slice = slice,
                    bestSlice = bestSlice,
                    compare = compareWithBest,
                    onCompare = { compareWithBest = it },
                )
            }
            RepBarsCard(
                modifier = cardModifier,
                reps = reps,
                position = position,
                onSelect = { selectedPosition = it },
            )
        }
    }
    val actions: @Composable (Modifier) -> Unit = { modifier ->
        ReportActions(
            onStages = { stageSheetOpen = true },
            onDetails = { detailSheetOpen = true },
            onExport = { exportSheetOpen = true },
            modifier = modifier,
        )
    }
    val backgroundLayer: @Composable (Modifier) -> Unit = { modifier ->
        if (overlayFile != null) {
            CoverVideo(file = overlayFile, playback = playback, modifier = modifier)
        } else {
            ReportPreview(
                videoUri = current.videoUri,
                selected = selectedSlice,
                best = bestSlice.takeIf { compareWithBest },
                samples = current.motionSamples,
                modifier = modifier,
                fill = true,
            )
        }
    }

    Box(modifier.fillMaxSize().background(Bg)) {
        if (!landscape) {
            // Portrait: video is the full-bleed background; cards float on the left.
            backgroundLayer(Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                header(Modifier)
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(start = VbtSpacing.m, end = VbtSpacing.m, top = VbtSpacing.s),
                ) {
                    Column(
                        Modifier.weight(0.46f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(VbtSpacing.s),
                    ) {
                        cardsContent(Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.weight(0.54f))
                }
                if (overlayFile != null) {
                    PlaybackBar(
                        playback = playback,
                        modifier = Modifier.padding(horizontal = VbtSpacing.m),
                    )
                }
                actions(Modifier.padding(VbtSpacing.m))
            }
        } else {
            // Landscape: full-width video on top, cards in a horizontal strip below. No overlap.
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                header(Modifier)
                Box(Modifier.weight(0.5f).fillMaxWidth()) {
                    backgroundLayer(Modifier.fillMaxSize())
                }
                if (overlayFile != null) {
                    PlaybackBar(playback = playback, modifier = Modifier.padding(horizontal = VbtSpacing.m))
                }
                Row(
                    Modifier
                        .weight(0.5f)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(VbtSpacing.m),
                    horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s),
                ) {
                    cardsContent(Modifier.width(240.dp))
                }
                actions(Modifier.padding(horizontal = VbtSpacing.m, vertical = VbtSpacing.xs))
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (stageSheetOpen) {
        StageSheet(
            onDismiss = { stageSheetOpen = false },
            slice = selectedSlice,
            lift = current.lift,
            editing = editingPhases,
            onToggleEditing = { editingPhases = !editingPhases },
            onRepChange = ::updateSlice,
            manualRejected = manualEditRejected,
        )
    }
    if (detailSheetOpen) {
        DetailSheet(
            onDismiss = { detailSheetOpen = false },
            lvp = current.lvp,
            analyzePose = current.analyzePose,
            quality = current.quality,
            fps = current.fps,
            reps = reps,
        )
    }
    if (exportSheetOpen) {
        ExportSheet(
            onDismiss = { exportSheetOpen = false },
            onExportJson = { jsonLauncher.launch("vbtdroid-set.json") },
            onExportCsv = { csvLauncher.launch("vbtdroid-set.csv") },
            onExportOverlay = { overlayLauncher.launch("vbtdroid-overlay.mp4") },
            onSaveOverlay = { previewSaveLauncher.launch("vbtdroid-overlay.mp4") },
            overlayReady = overlayFile != null,
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
