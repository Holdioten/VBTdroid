package com.vbtdroid.app.ui.report

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vbtdroid.app.R
import com.vbtdroid.app.analysis.AnalysisResult
import com.vbtdroid.app.ui.common.liftDisplayName
import com.vbtdroid.app.ui.common.variantDisplayName
import com.vbtdroid.app.ui.components.ChipTone
import com.vbtdroid.app.ui.components.StatusChip
import com.vbtdroid.app.ui.theme.Bg
import com.vbtdroid.app.ui.theme.VbtSpacing
import com.vbtdroid.core.phases.RepSlice
import com.vbtdroid.core.phases.RepTimeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Video-first report. The burned-in overlay MP4 is the only content: Compose draws no data cards
 * over it, so the cards appear exactly once, in the style the overlay burns.
 */
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
    val exportScope = rememberCoroutineScope()
    val playback = remember(result) { OverlayPlayback() }
    val overlayFile = remember(current.overlayVideoPath) { overlayVideoFile(current.overlayVideoPath) }
    // null while the overlay is missing or unreadable: the report falls back to the first frame.
    val overlayVideo = overlayFile?.takeUnless { playback.failed }
    val overlayUri = remember(overlayVideo) { overlayVideo?.let { Uri.fromFile(it) } }
    var saving by remember(result) { mutableStateOf(false) }

    // UI-only state (spec §4.1).
    var stageSheetOpen by rememberSaveable(result) { mutableStateOf(false) }
    var detailSheetOpen by rememberSaveable(result) { mutableStateOf(false) }
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

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4"),
    ) { uri ->
        val source = overlayFile
        if (uri != null && source != null) {
            saving = true
            exportScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                            source.inputStream().use { input -> input.copyTo(out) }
                        } ?: error("Could not open export destination")
                    }.isSuccess
                }
                saving = false
                flash(
                    context.getString(
                        if (ok) R.string.report_export_succeeded else R.string.report_export_failed,
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
    val playbackSec: Double? = if (overlayVideo != null && playback.durationMs > 0f) {
        playback.positionMs / 1000.0
    } else {
        null
    }
    // Stage sheet edits the rep under the playhead; with no card selection it falls back to the first.
    val editedSlice = playbackSec
        ?.let { RepTimeline.currentRepAt(current.repSlices, it) }
        ?: current.repSlices.firstOrNull()

    val identity = buildSubtitle(current.loadKg, current.lift, current.variant)
    val header: @Composable (Modifier) -> Unit = {
        ReportHeader(
            identity = identity,
            onBack = onBack,
            onHistory = onShowHistory,
            status = {
                when {
                    persistFailed -> StatusChip(text = stringResource(R.string.report_save_failed), tone = ChipTone.ERROR)
                    overlayVideo == null -> StatusChip(text = stringResource(R.string.report_overlay_unavailable))
                    else -> Unit
                }
            },
        )
    }
    val actions: @Composable (Modifier) -> Unit = { actionModifier ->
        ReportActions(
            onStages = { stageSheetOpen = true },
            onDetails = { detailSheetOpen = true },
            onSave = { saveLauncher.launch("vbtdroid-overlay.mp4") },
            saveEnabled = overlayFile != null && !saving,
            modifier = actionModifier,
        )
    }
    val backgroundLayer: @Composable (Modifier) -> Unit = { videoModifier ->
        val uri = overlayUri
        if (uri != null) {
            FitVideo(uri = uri, playback = playback, modifier = videoModifier)
        } else {
            ReportPreview(
                videoUri = current.videoUri,
                modifier = videoModifier,
                fill = true,
            )
        }
    }

    Box(modifier.fillMaxSize().background(Bg)) {
        backgroundLayer(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            header(Modifier)
            Spacer(Modifier.weight(1f))
            if (overlayUri != null) {
                PlaybackBar(
                    playback = playback,
                    modifier = Modifier.padding(horizontal = VbtSpacing.m),
                )
            }
            actions(Modifier.padding(VbtSpacing.m))
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (stageSheetOpen) {
        StageSheet(
            onDismiss = { stageSheetOpen = false },
            slice = editedSlice,
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
