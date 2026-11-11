package com.vbtdroid.app.ui.import

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import android.graphics.RectF
import android.net.Uri
import android.provider.Settings
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.app.analysis.AnalysisRequest
import com.vbtdroid.app.ui.common.liftDisplayName
import com.vbtdroid.app.ui.common.variantDisplayName
import com.vbtdroid.app.ui.components.ChipTone
import com.vbtdroid.app.ui.components.MessageBanner
import com.vbtdroid.app.ui.components.PrimaryActionBar
import com.vbtdroid.app.ui.components.PrimaryButton
import com.vbtdroid.app.ui.components.SectionCard
import com.vbtdroid.app.ui.components.SectionLabel
import com.vbtdroid.app.ui.components.SegmentedPicker
import com.vbtdroid.app.ui.components.StatusChip
import com.vbtdroid.app.ui.components.SwitchRow
import com.vbtdroid.app.ui.components.primaryButtonColors
import com.vbtdroid.app.ui.components.secondaryOutlinedColors
import com.vbtdroid.app.ui.theme.MinTouchTarget
import com.vbtdroid.app.ui.theme.VbtPalettes
import com.vbtdroid.app.ui.theme.ShapePill
import com.vbtdroid.app.ui.theme.VbtSpacing
import com.vbtdroid.app.ui.theme.VbtTheme
import com.vbtdroid.app.ui.theme.VbtThemeId
import com.vbtdroid.app.video.VideoDecoder
import com.vbtdroid.app.video.VideoLimits
import com.vbtdroid.app.video.VideoMeta
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.model.LiftVariant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGE_VIDEO = ImportFlowPolicy.PAGE_VIDEO
private const val PAGE_ROI = ImportFlowPolicy.PAGE_ROI
private const val PAGE_SETTINGS = ImportFlowPolicy.PAGE_SETTINGS

/**
 * Pre-analysis flow in three horizontal pages: video, settings, ROI. State survives Activity
 * recreation through rememberSaveable; decoded frame and metadata are reloaded from the saved URI.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ImportScreen(
    analyzing: Boolean = false,
    analysisProgress: Float = 0f,
    analysisError: String? = null,
    onAnalyze: (AnalysisRequest) -> Unit = {},
    onReanalyze: (AnalysisRequest) -> Unit = {},
    onCancel: () -> Unit = {},
    onShowHistory: () -> Unit = {},
    suggestRois: (Bitmap) -> List<RectF> = { emptyList() },
    onLookupReuse: (suspend (AnalysisRequest) -> Boolean)? = null,
    themeId: VbtThemeId = VbtThemeId.LIGHT_RED,
    onThemeChange: (VbtThemeId) -> Unit = {},
) {
    val context = LocalContext.current
    val decoder = remember(context) { VideoDecoder(context) }
    var selectedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var fileName by rememberSaveable { mutableStateOf<String?>(null) }
    var videoMeta by remember { mutableStateOf<VideoMeta?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var roi by rememberSaveable { mutableStateOf<RectF?>(null) }
    var roiConfirmed by rememberSaveable { mutableStateOf(false) }
    var autoRoiUnavailable by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var videoError by remember { mutableStateOf<String?>(null) }
    var lift by rememberSaveable { mutableStateOf(Lift.SQUAT) }
    var variant by rememberSaveable { mutableStateOf(LiftVariant.HIGH_BAR) }
    var loadText by rememberSaveable { mutableStateOf("") }
    val defaultPlateDiameter = stringResource(R.string.import_default_plate_diameter)
    val defaultCameraAngle = stringResource(R.string.import_default_camera_angle)
    var plateDiameterText by rememberSaveable { mutableStateOf(defaultPlateDiameter) }
    var cameraAngleText by rememberSaveable { mutableStateOf(defaultCameraAngle) }
    // Bench pose tracking is unreliable; default it off and reset when the lift changes.
    var analyzePose by rememberSaveable(lift) { mutableStateOf(lift != Lift.BENCH) }
    var roiCandidates by remember { mutableStateOf<List<RectF>>(emptyList()) }
    var showSettings by remember { mutableStateOf(false) }
    var autoRoi by remember { mutableStateOf<RectF?>(null) }
    var selectedCandidate by rememberSaveable { mutableStateOf(0) }

    val currentPreview = preview
    DisposableEffect(currentPreview) {
        onDispose { currentPreview?.takeIf { !it.isRecycled }?.recycle() }
    }

    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    fun goTo(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page) }
    }

    fun clearVideo() {
        selectedUri = null
        fileName = null
        videoMeta = null
        preview = null
        roi = null
        roiConfirmed = false
        autoRoiUnavailable = false
        roiCandidates = emptyList()
        autoRoi = null
        selectedCandidate = 0
        videoError = null
        loading = false
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // A cancelled picker returns null: nothing changes, the user stays where they were.
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            selectedUri = uri
            fileName = null
            videoMeta = null
            roi = null
            roiConfirmed = false
            videoError = null
            goTo(PAGE_VIDEO)
        }
    }

    // Back with a video loaded clears it and returns to the empty import state (never exits the app).
    BackHandler(enabled = selectedUri != null && !analyzing) {
        clearVideo()
        goTo(PAGE_VIDEO)
    }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        // ROI present at start means we were restored from saved state: keep the user's region.
        val restoredRoi = roi
        loading = true
        videoError = null
        try {
            fileName = queryDisplayName(context, uri)
            val loaded = withContext(Dispatchers.IO) {
                val meta = decoder.readMeta(uri)
                VideoLimits.requireSupportedDuration(meta.durationSec)
                val firstFrame = decoder.readFirstFrame(uri)
                // The first frame often has one plate occluded/out of frame. Probe a few more
                // frames so both bar-end plates can become ROI candidates.
                var suggestions = runCatching { suggestRois(firstFrame) }.getOrDefault(emptyList())
                for (probeSec in listOf(0.5, 1.0)) {
                    if (suggestions.size >= 2) break
                    val probe = runCatching { decoder.readFrameAt(uri, probeSec) }.getOrNull() ?: continue
                    val probeSuggestions = runCatching { suggestRois(probe) }.getOrDefault(emptyList())
                    probe.recycle()
                    if (probeSuggestions.size > suggestions.size) suggestions = probeSuggestions
                }
                Triple(meta, firstFrame, suggestions)
            }
            videoMeta = loaded.first
            preview = loaded.second
            roiCandidates = loaded.third
            selectedCandidate = 0
            autoRoi = loaded.third.firstOrNull()
            if (restoredRoi == null) {
                roi = autoRoi
                roiConfirmed = false
                autoRoiUnavailable = autoRoi == null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            videoError = error.message ?: error::class.java.simpleName
            videoMeta = null
            preview = null
            roi = null
            roiConfirmed = false
        } finally {
            loading = false
        }
    }

    val loadKg = loadText.toDoubleOrNull()
    val plateDiameterM = plateDiameterMeters(plateDiameterText.toDoubleOrNull())
    val cameraAngle = cameraAngleText.toDoubleOrNull()
    val validLoad = loadText.isBlank() || (loadKg != null && loadKg.isFinite() && loadKg > 0.0)
    val validPlate = plateDiameterM != null
    val validAngle = cameraAngle != null && cameraAngle.isFinite() &&
        cameraAngle > 0.0 && cameraAngle <= 90.0
    val validSettings = validLoad && validPlate && validAngle
    val minRoiSize = preview?.let { RoiGeometry.minimumSize(it.width, it.height) }
        ?: RoiGeometry.MIN_SIZE_PX
    val validRoi = roi?.let { !it.isEmpty && it.width() >= minRoiSize && it.height() >= minRoiSize } == true
    val canAnalyze = !analyzing && validSettings && validRoi && roiConfirmed

    val snackbar = remember { SnackbarHostState() }
    val cancelledMessage = stringResource(R.string.analysis_cancelled)
    val liftNames = Lift.entries.associateWith { liftDisplayName(it) }
    val variantNames = LiftVariant.entries.associateWith { variantDisplayName(it) }

    fun currentRequest(): AnalysisRequest? {
        val uri = selectedUri ?: return null
        val angle = cameraAngle ?: return null
        val plate = plateDiameterM ?: return null
        val region = roi ?: return null
        return AnalysisRequest(
            uri = uri,
            lift = lift,
            variant = variant,
            cameraAngleDeg = angle,
            plateDiameterM = plate,
            loadKg = if (loadText.isBlank()) null else loadKg,
            analyzePose = analyzePose,
            roi = RectF(region),
            themeId = themeId,
            analysisKey = buildAnalysisKey(
                uri = uri,
                meta = videoMeta,
                lift = lift,
                variant = variant,
                loadText = loadText,
                plateText = plateDiameterText,
                angleText = cameraAngleText,
                analyzePose = analyzePose,
            ),
        )
    }

    fun startAnalysis() {
        currentRequest()?.let(onAnalyze)
    }

    fun forceReanalysis() {
        currentRequest()?.let(onReanalyze)
    }

    // Tell the user up front that this exact video and input set already produced a result.
    var alreadyAnalyzed by remember { mutableStateOf(false) }
    val lookupKey = if (canAnalyze) currentRequest()?.analysisKey else null
    LaunchedEffect(lookupKey) {
        alreadyAnalyzed = false
        if (lookupKey != null && onLookupReuse != null) {
            val request = currentRequest() ?: return@LaunchedEffect
            alreadyAnalyzed = runCatching { onLookupReuse(request) }.getOrDefault(false)
        }
    }

    // The bar only exists when there is a video or analysis is running (spec: no permanent bar).
    val barVisible = analyzing || selectedUri != null

    if (showSettings) {
        SettingsDialog(
            themeId = themeId,
            onThemeChange = onThemeChange,
            onDismiss = { showSettings = false },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { showSettings = true }, enabled = !analyzing) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.settings_title),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                title = { BrandLockup() },
                actions = {
                    TextButton(
                        onClick = onShowHistory,
                        enabled = !analyzing,
                        modifier = Modifier.heightIn(min = MinTouchTarget),
                    ) {
                        Text(stringResource(R.string.history_title))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (barVisible) {
                ImportActionBar(
                    analyzing = analyzing,
                    analysisProgress = analysisProgress,
                    page = pagerState.currentPage,
                    hasVideo = selectedUri != null,
                    loading = loading,
                    hasFrame = preview != null,
                    hasRoi = roi != null,
                    roiConfirmed = roiConfirmed,
                    validRoi = validRoi,
                    validSettings = validSettings,
                    canAnalyze = canAnalyze,
                    reused = alreadyAnalyzed,
                    onCancel = {
                        onCancel()
                        scope.launch { snackbar.showSnackbar(cancelledMessage) }
                    },
                    onNext = { goTo(pagerState.currentPage + 1) },
                    onBackToRoi = { goTo(PAGE_ROI) },
                    onReset = {
                        roi = autoRoi
                        roiConfirmed = false
                        selectedCandidate = 0
                    },
                    onConfirm = { roiConfirmed = true },
                    onAnalyze = ::startAnalysis,
                    onReanalyze = ::forceReanalysis,
                )
            }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            StepIndicator(current = pagerState.currentPage, onSelect = ::goTo)
            Column(
                modifier = Modifier.padding(horizontal = VbtSpacing.l),
                verticalArrangement = Arrangement.spacedBy(VbtSpacing.s),
            ) {
                videoError?.let {
                    val message = if (it == VideoLimits.TOO_LONG_MESSAGE) {
                        stringResource(R.string.import_error_too_long)
                    } else {
                        stringResource(R.string.import_error_reason, it)
                    }
                    MessageBanner(message)
                }
                analysisError?.let {
                    MessageBanner(stringResource(R.string.import_analysis_failed_reason, it))
                }
            }
            // Weight, not fillMaxSize: the pager takes only the height left after the bars above it.
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) { page ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = VbtSpacing.l, vertical = VbtSpacing.m),
                    verticalArrangement = Arrangement.spacedBy(VbtSpacing.l),
                ) {
                    when (page) {
                        PAGE_VIDEO -> VideoPage(
                            selectedUri = selectedUri,
                            preview = preview,
                            videoMeta = videoMeta,
                            fileName = fileName,
                            loading = loading,
                            enabled = !analyzing,
                            onChoose = { picker.launch(arrayOf("video/*")) },
                        )
                        PAGE_SETTINGS -> SettingsPage(
                            enabled = !analyzing,
                            lift = lift,
                            variant = variant,
                            liftNames = liftNames,
                            variantNames = variantNames,
                            onLift = {
                                lift = it
                                variant = defaultVariant(it)
                            },
                            onVariant = { variant = it },
                            loadText = loadText,
                            onLoad = { loadText = it },
                            validLoad = validLoad,
                            plateText = plateDiameterText,
                            onPlate = { plateDiameterText = it },
                            validPlate = validPlate,
                            angleText = cameraAngleText,
                            onAngle = { cameraAngleText = it },
                            validAngle = validAngle,
                            analyzePose = analyzePose,
                            onAnalyzePose = { analyzePose = it },
                            validSettings = validSettings,
                        )
                        PAGE_ROI -> RoiPage(
                            preview = preview,
                            roi = roi,
                            candidates = roiCandidates,
                            selectedCandidate = selectedCandidate,
                            onSelectCandidate = { index ->
                                roiCandidates.getOrNull(index)?.let { candidate ->
                                    roi = candidate
                                    roiConfirmed = false
                                    selectedCandidate = index
                                }
                            },
                            roiConfirmed = roiConfirmed,
                            autoRoiUnavailable = autoRoiUnavailable,
                            onRoiChanged = {
                                roi = it
                                roiConfirmed = false
                            },
                            editable = RoiEditPolicy.canEdit(roiConfirmed, analyzing),
                            onModify = { roiConfirmed = false },
                            onReset = {
                                roi = autoRoi
                                roiConfirmed = false
                                selectedCandidate = 0
                            },
                            onGoVideo = { goTo(PAGE_VIDEO) },
                        )
                    }
                }
            }
        }
    }
}

/** Three-step indicator. Tapping a step jumps to it (pages handle their own empty states). */
@Composable
private fun StepIndicator(current: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(R.string.import_step_video, R.string.import_step_roi, R.string.import_step_params)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = VbtSpacing.l, vertical = VbtSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s),
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == current
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = MinTouchTarget)
                    .clickable { onSelect(index) },
                shape = ShapePill,
                color = if (active) VbtTheme.colors.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MinTouchTarget)
                        .padding(horizontal = VbtSpacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "${index + 1}  ${stringResource(label)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) MaterialTheme.colorScheme.primary else VbtTheme.colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoPage(
    selectedUri: Uri?,
    preview: Bitmap?,
    videoMeta: VideoMeta?,
    fileName: String?,
    loading: Boolean,
    enabled: Boolean,
    onChoose: () -> Unit,
) {
    if (selectedUri == null) {
        EmptyDropZone(enabled = enabled, onChooseVideo = onChoose)
        return
    }
    when {
        loading -> LoadingFrame()
        preview != null -> FrameHero(preview)
        else -> Text(
            stringResource(R.string.import_cta_need_video),
            style = MaterialTheme.typography.bodyMedium,
            color = VbtTheme.colors.onSurfaceVariant,
        )
    }
    videoMeta?.let { meta ->
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s),
            verticalArrangement = Arrangement.spacedBy(VbtSpacing.s),
        ) {
            fileName?.let {
                StatusChip(text = stringResource(R.string.import_file_name, it), maxWidth = 220.dp)
            }
            StatusChip(stringResource(R.string.import_duration, meta.durationSec))
            StatusChip(stringResource(R.string.import_resolution, meta.width, meta.height))
            StatusChip(stringResource(R.string.import_effective_fps, meta.effectiveFps))
        }
    }
    OutlinedButton(
        onClick = onChoose,
        enabled = enabled,
        modifier = Modifier.heightIn(min = MinTouchTarget),
        colors = secondaryOutlinedColors(),
    ) {
        Text(stringResource(R.string.import_replace_video))
    }
}

@Composable
private fun FrameHero(frame: Bitmap) {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(BorderStroke(1.dp, VbtTheme.colors.outline.copy(alpha = 0.6f)), shape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = frame.asImageBitmap(),
            contentDescription = stringResource(R.string.import_first_frame_preview),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPage(
    enabled: Boolean,
    lift: Lift,
    variant: LiftVariant,
    liftNames: Map<Lift, String>,
    variantNames: Map<LiftVariant, String>,
    onLift: (Lift) -> Unit,
    onVariant: (LiftVariant) -> Unit,
    loadText: String,
    onLoad: (String) -> Unit,
    validLoad: Boolean,
    plateText: String,
    onPlate: (String) -> Unit,
    validPlate: Boolean,
    angleText: String,
    onAngle: (String) -> Unit,
    validAngle: Boolean,
    analyzePose: Boolean,
    onAnalyzePose: (Boolean) -> Unit,
    validSettings: Boolean,
) {
    SectionLabel(stringResource(R.string.import_lift))
    SegmentedPicker(
        options = Lift.entries,
        selected = lift,
        label = { liftNames.getValue(it) },
        onSelect = onLift,
        enabled = enabled,
    )
    if (lift != Lift.BENCH) {
        SectionLabel(stringResource(R.string.import_variant))
        SegmentedPicker(
            options = variantsFor(lift),
            selected = variant,
            label = { variantNames.getValue(it) },
            onSelect = onVariant,
            enabled = enabled,
        )
    }

    SectionLabel(stringResource(R.string.import_analysis_settings))
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
            OutlinedTextField(
                value = loadText,
                onValueChange = onLoad,
                enabled = enabled,
                label = { Text(stringResource(R.string.import_load_label)) },
                suffix = { Text(stringResource(R.string.unit_kg)) },
                isError = !validLoad,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = plateText,
                onValueChange = onPlate,
                enabled = enabled,
                label = { Text(stringResource(R.string.import_plate_label)) },
                suffix = { Text(stringResource(R.string.unit_cm)) },
                isError = !validPlate,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = angleText,
            onValueChange = onAngle,
            enabled = enabled,
            label = { Text(stringResource(R.string.import_camera_label)) },
            suffix = { Text(stringResource(R.string.unit_deg)) },
            isError = !validAngle,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
            AssistChip(
                onClick = { onAngle("90") },
                enabled = enabled,
                label = { Text(stringResource(R.string.import_camera_preset_side)) },
            )
            AssistChip(
                onClick = { onAngle("45") },
                enabled = enabled,
                label = { Text(stringResource(R.string.import_camera_preset_diagonal)) },
            )
        }
    }

    SectionCard(padding = VbtSpacing.s) {
        SwitchRow(
            title = stringResource(R.string.import_analyze_pose),
            checked = analyzePose,
            onCheckedChange = onAnalyzePose,
            enabled = enabled,
            description = stringResource(R.string.import_pose_hint),
        )
    }
}

@Composable
private fun RoiPage(
    preview: Bitmap?,
    roi: RectF?,
    candidates: List<RectF>,
    selectedCandidate: Int,
    onSelectCandidate: (Int) -> Unit,
    roiConfirmed: Boolean,
    autoRoiUnavailable: Boolean,
    onRoiChanged: (RectF) -> Unit,
    editable: Boolean,
    onModify: () -> Unit,
    onReset: () -> Unit,
    onGoVideo: () -> Unit,
) {
    if (preview == null) {
        Text(
            stringResource(R.string.import_cta_need_video),
            style = MaterialTheme.typography.bodyMedium,
            color = VbtTheme.colors.onSurfaceVariant,
        )
        OutlinedButton(onClick = onGoVideo, modifier = Modifier.heightIn(min = MinTouchTarget)) {
            Text(stringResource(R.string.import_step_video))
        }
        return
    }
    val shape = MaterialTheme.shapes.large
    val (chipText, chipTone) = when {
        roiConfirmed -> stringResource(R.string.import_status_locked) to ChipTone.PRIMARY
        autoRoiUnavailable -> stringResource(R.string.import_status_manual) to ChipTone.WARNING
        else -> stringResource(R.string.import_status_suggested) to ChipTone.PRIMARY
    }
    if (editable && candidates.size > 1) {
        Row(horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
            candidates.forEachIndexed { index, _ ->
                FilterChip(
                    selected = index == selectedCandidate,
                    onClick = { onSelectCandidate(index) },
                    label = {
                        Text(
                            stringResource(
                                if (index == 0) R.string.import_roi_candidate_far
                                else R.string.import_roi_candidate_near,
                            ),
                        )
                    },
                )
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(BorderStroke(1.dp, VbtTheme.colors.outline.copy(alpha = 0.6f)), shape),
    ) {
        RoiSelector(bitmap = preview, roi = roi, onRoiChanged = onRoiChanged, editable = editable)
        StatusChip(
            text = chipText,
            tone = chipTone,
            modifier = Modifier.align(Alignment.TopStart).padding(VbtSpacing.s),
        )
    }
    if (roiConfirmed) {
        Row(horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
            OutlinedButton(
                onClick = onModify,
                modifier = Modifier.heightIn(min = MinTouchTarget),
                colors = secondaryOutlinedColors(),
            ) {
                Text(stringResource(R.string.import_roi_modify))
            }
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.heightIn(min = MinTouchTarget),
                colors = secondaryOutlinedColors(),
            ) {
                Text(stringResource(R.string.import_roi_reset))
            }
        }
    } else {
        Text(
            stringResource(R.string.import_roi_edit_hint),
            style = MaterialTheme.typography.bodySmall,
            color = VbtTheme.colors.onSurfaceVariant,
        )
    }
}

/**
 * Bottom bar, page-aware. Nothing is shown on the empty state (handled by the caller).
 * - video page: 下一步 once a frame is ready
 * - settings page: 下一步 once settings are valid
 * - ROI page: 重置 + 确认区域 until confirmed, then 开始分析 when settings are valid
 * - analyzing: progress + cancel on any page
 */
@Composable
private fun ImportActionBar(
    analyzing: Boolean,
    analysisProgress: Float,
    page: Int,
    hasVideo: Boolean,
    loading: Boolean,
    hasFrame: Boolean,
    hasRoi: Boolean,
    roiConfirmed: Boolean,
    validRoi: Boolean,
    validSettings: Boolean,
    canAnalyze: Boolean,
    reused: Boolean,
    onReanalyze: () -> Unit,
    onCancel: () -> Unit,
    onNext: () -> Unit,
    onBackToRoi: () -> Unit,
    onReset: () -> Unit,
    onConfirm: () -> Unit,
    onAnalyze: () -> Unit,
) {
    if (analyzing) {
        PrimaryActionBar {
            Text(
                stringResource(R.string.import_analysis_progress, (analysisProgress * 100).toInt()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            LinearProgressIndicator(
                progress = { analysisProgress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.import_cancel_analysis))
            }
        }
        return
    }
    when (page) {
        PAGE_VIDEO -> PrimaryActionBar(
            hint = if (loading) stringResource(R.string.import_loading_frame) else null,
        ) {
            PrimaryButton(
                text = stringResource(R.string.import_next),
                onClick = onNext,
                enabled = ImportFlowPolicy.canLeaveVideo(hasFrame, loading),
            )
        }
        PAGE_ROI -> when {
            !hasFrame -> PrimaryActionBar(
                hint = stringResource(if (loading) R.string.import_loading_frame else R.string.import_cta_need_video),
            ) {}
            !roiConfirmed -> PrimaryActionBar {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
                    OutlinedButton(
                        onClick = onReset,
                        enabled = hasRoi,
                        modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
                        colors = secondaryOutlinedColors(),
                    ) {
                        Text(stringResource(R.string.import_roi_reset), style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = validRoi,
                        modifier = Modifier.weight(1.6f).height(52.dp),
                        shape = MaterialTheme.shapes.small,
                        colors = primaryButtonColors(),
                    ) {
                        Text(stringResource(R.string.import_action_confirm_roi), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            else -> PrimaryActionBar {
                PrimaryButton(
                    text = stringResource(R.string.import_next),
                    onClick = onNext,
                    enabled = ImportFlowPolicy.canLeaveRoi(roiConfirmed),
                )
            }
        }
        else -> when {
            ImportFlowPolicy.mustReturnToRoi(page, roiConfirmed) -> PrimaryActionBar(
                hint = stringResource(R.string.import_cta_need_roi),
            ) {
                OutlinedButton(
                    onClick = onBackToRoi,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
                    colors = secondaryOutlinedColors(),
                ) {
                    Text(stringResource(R.string.import_back_to_roi), style = MaterialTheme.typography.labelLarge)
                }
            }
            !validSettings -> PrimaryActionBar(hint = stringResource(R.string.import_invalid_settings)) {}
            reused -> PrimaryActionBar(hint = stringResource(R.string.import_reuse_hint)) {
                val enabled = ImportFlowPolicy.showStart(page, roiConfirmed, validSettings) && canAnalyze
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
                    OutlinedButton(
                        onClick = onReanalyze,
                        enabled = enabled,
                        modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
                        colors = secondaryOutlinedColors(),
                    ) {
                        Text(
                            stringResource(R.string.import_reanalyze),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Button(
                        onClick = onAnalyze,
                        enabled = enabled,
                        modifier = Modifier.weight(1.4f).height(52.dp),
                        shape = MaterialTheme.shapes.small,
                        colors = primaryButtonColors(),
                    ) {
                        Text(
                            stringResource(R.string.import_open_existing),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            else -> PrimaryActionBar {
                PrimaryButton(
                    text = stringResource(R.string.import_start_analysis),
                    onClick = onAnalyze,
                    enabled = ImportFlowPolicy.showStart(page, roiConfirmed, validSettings) && canAnalyze,
                )
            }
        }
    }
}

@Composable
private fun EmptyDropZone(enabled: Boolean, onChooseVideo: () -> Unit) {
    val outline = VbtTheme.colors.outline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(6.dp.toPx(), 6.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width - 12.dp.toPx(), size.height - 12.dp.toPx()),
                )
            }
            .padding(VbtSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(VbtSpacing.s))
        Text(
            stringResource(R.string.import_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(R.string.import_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = VbtTheme.colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(VbtSpacing.m))
        Button(onClick = onChooseVideo, enabled = enabled, modifier = Modifier.heightIn(min = MinTouchTarget)) {
            Text(stringResource(R.string.import_choose_video))
        }
    }
}

@Composable
private fun LoadingFrame() {
    val context = LocalContext.current
    val animatorOff = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val transition = rememberInfiniteTransition(label = "frame-pulse")
    val animated by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "frame-pulse-alpha",
    )
    val pulse = if (animatorOff) 1f else animated
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(213.dp)
            .alpha(pulse)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Text(
                stringResource(R.string.import_loading_frame),
                style = MaterialTheme.typography.bodySmall,
                color = VbtTheme.colors.onSurfaceVariant,
            )
        }
    }
}

internal fun defaultVariant(lift: Lift): LiftVariant = when (lift) {
    Lift.SQUAT -> LiftVariant.HIGH_BAR
    Lift.BENCH -> LiftVariant.TOUCH_AND_GO
    Lift.DEADLIFT -> LiftVariant.CONVENTIONAL
}

private fun variantsFor(lift: Lift): List<LiftVariant> = when (lift) {
    Lift.SQUAT -> listOf(LiftVariant.HIGH_BAR, LiftVariant.LOW_BAR)
    Lift.BENCH -> listOf(LiftVariant.PAUSED, LiftVariant.TOUCH_AND_GO)
    Lift.DEADLIFT -> listOf(LiftVariant.CONVENTIONAL, LiftVariant.SUMO)
}

private suspend fun queryDisplayName(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0) cursor.getString(column) else null
        } else {
            null
        }
    } ?: uri.lastPathSegment ?: uri.toString()
}

@Composable
private fun BrandLockup() {
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(if (light) R.drawable.brand_mark_light else R.drawable.brand_mark_dark),
            contentDescription = null,
            modifier = Modifier.height(30.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "VBT",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "droid",
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SettingsDialog(
    themeId: VbtThemeId,
    onThemeChange: (VbtThemeId) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var showThemes by remember { mutableStateOf(false) }
    var confirmClearCache by remember { mutableStateOf(false) }
    if (confirmClearCache) {
        AlertDialog(
            onDismissRequest = { confirmClearCache = false },
            title = { Text(stringResource(R.string.settings_clear_cache)) },
            text = { Text(stringResource(R.string.settings_clear_cache_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearCache = false
                    clearOverlayCache(context)
                    Toast.makeText(context, R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.settings_clear_cache)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCache = false }) {
                    Text(stringResource(R.string.settings_close))
                }
            },
        )
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(VbtSpacing.l)) {
                if (showThemes) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showThemes = false }) { Text("‹") }
                        Text(
                            stringResource(R.string.settings_theme),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.height(VbtSpacing.s))
                    VbtThemeId.values().forEach { id ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onThemeChange(id) }
                                .padding(horizontal = VbtSpacing.s, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ThemeSwatch(id)
                            Spacer(Modifier.width(VbtSpacing.m))
                            Text(stringResource(themeLabel(id)), modifier = Modifier.weight(1f))
                            if (id == themeId) {
                                Text("✓", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(VbtSpacing.m))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showThemes = true }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.settings_theme), modifier = Modifier.weight(1f))
                        Text(
                            stringResource(themeLabel(themeId)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("  ›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = VbtTheme.colors.outline.copy(alpha = 0.6f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { confirmClearCache = true }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.settings_clear_cache), modifier = Modifier.weight(1f))
                        Text("  ›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(VbtSpacing.m))
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.settings_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(id: VbtThemeId) {
    val palette = VbtPalettes.of(id)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(palette.bg))
        Box(Modifier.size(10.dp).clip(CircleShape).background(palette.primary))
        Box(Modifier.size(10.dp).clip(CircleShape).background(palette.onSurface))
    }
}

private fun buildAnalysisKey(
    uri: Uri,
    meta: VideoMeta?,
    lift: Lift,
    variant: LiftVariant,
    loadText: String,
    plateText: String,
    angleText: String,
    analyzePose: Boolean,
): String {
    fun number(text: String): String = text.trim().toDoubleOrNull()?.toString() ?: text.trim()
    // Duration comes from the container metadata as a float; rounding it keeps a re-probe from
    // producing a different identity for the same file.
    val durationKey = meta?.durationSec?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "-"
    // The ROI is deliberately absent: the auto-detector can shift it a few pixels between imports
    // of the same file, which used to make an already-analysed video look new.
    return listOf(
        uri.toString(),
        durationKey,
        "${meta?.width ?: 0}x${meta?.height ?: 0}",
        lift.name,
        variant.name,
        number(loadText),
        number(plateText),
        number(angleText),
        analyzePose.toString(),
    ).joinToString("|")
}

private fun clearOverlayCache(context: android.content.Context) {
    val cache = context.cacheDir ?: return
    cache.listFiles { file ->
        file.name.startsWith("overlay-") || file.name.startsWith("debug-")
    }?.forEach { runCatching { it.delete() } }
}

private fun themeLabel(id: VbtThemeId): Int = when (id) {
    VbtThemeId.LIGHT_RED -> R.string.theme_light_red
    VbtThemeId.DARK_RED -> R.string.theme_dark_red
    VbtThemeId.ARCUS_VBT -> R.string.theme_arcus
}
