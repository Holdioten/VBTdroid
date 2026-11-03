package com.vbtdroid.app.ui.import

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.app.analysis.AnalysisRequest
import com.vbtdroid.app.ui.components.ChipTone
import com.vbtdroid.app.ui.components.MessageBanner
import com.vbtdroid.app.ui.components.PrimaryActionBar
import com.vbtdroid.app.ui.components.PrimaryButton
import com.vbtdroid.app.ui.components.SectionCard
import com.vbtdroid.app.ui.components.SectionLabel
import com.vbtdroid.app.ui.components.SegmentedPicker
import com.vbtdroid.app.ui.components.StatusChip
import com.vbtdroid.app.ui.components.SwitchRow
import com.vbtdroid.app.ui.theme.Bg
import com.vbtdroid.app.ui.theme.MinTouchTarget
import com.vbtdroid.app.ui.common.liftDisplayName
import com.vbtdroid.app.ui.common.variantDisplayName
import com.vbtdroid.app.ui.theme.VbtSpacing
import com.vbtdroid.app.ui.theme.VbtTheme
import com.vbtdroid.app.video.VideoDecoder
import com.vbtdroid.app.video.VideoLimits
import com.vbtdroid.app.video.VideoMeta
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.model.LiftVariant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportScreen(
    analyzing: Boolean = false,
    analysisProgress: Float = 0f,
    analysisError: String? = null,
    onAnalyze: (AnalysisRequest) -> Unit = {},
    onCancel: () -> Unit = {},
    onShowHistory: () -> Unit = {},
    suggestRoi: (Bitmap) -> RectF? = { null },
) {
    val context = LocalContext.current
    val decoder = remember(context) { VideoDecoder(context) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var videoMeta by remember { mutableStateOf<VideoMeta?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var roi by remember { mutableStateOf<RectF?>(null) }
    var roiConfirmed by remember { mutableStateOf(false) }
    var autoRoiUnavailable by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var videoError by remember { mutableStateOf<String?>(null) }
    var lift by remember { mutableStateOf(Lift.SQUAT) }
    var variant by remember { mutableStateOf(LiftVariant.HIGH_BAR) }
    var loadText by remember { mutableStateOf("") }
    val defaultPlateDiameter = stringResource(R.string.import_default_plate_diameter)
    val defaultCameraAngle = stringResource(R.string.import_default_camera_angle)
    var plateDiameterText by remember { mutableStateOf(defaultPlateDiameter) }
    var cameraAngleText by remember { mutableStateOf(defaultCameraAngle) }
    var cameraLevel by remember { mutableStateOf(true) }
    var analyzePose by remember { mutableStateOf(true) }

    val currentPreview = preview
    DisposableEffect(currentPreview) {
        onDispose { currentPreview?.takeIf { !it.isRecycled }?.recycle() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
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
        }
    }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        loading = true
        videoError = null
        autoRoiUnavailable = false
        try {
            fileName = queryDisplayName(context, uri)
            val loaded = withContext(Dispatchers.IO) {
                val meta = decoder.readMeta(uri)
                VideoLimits.requireSupportedDuration(meta.durationSec)
                val firstFrame = decoder.readFirstFrame(uri)
                val suggestion = runCatching { suggestRoi(firstFrame) }.getOrNull()
                Triple(meta, firstFrame, suggestion)
            }
            videoMeta = loaded.first
            preview = loaded.second
            roi = loaded.third
            roiConfirmed = false
            autoRoiUnavailable = loaded.third == null
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
    val plateDiameter = plateDiameterText.toDoubleOrNull()
    val cameraAngle = cameraAngleText.toDoubleOrNull()
    val validLoad = loadText.isBlank() || (loadKg != null && loadKg.isFinite() && loadKg > 0.0)
    val validPlate = plateDiameter != null && plateDiameter.isFinite() && plateDiameter > 0.0
    val validAngle = cameraAngle != null && cameraAngle.isFinite() &&
        cameraAngle > 0.0 && cameraAngle <= 90.0
    val validSettings = validLoad && validPlate && validAngle
    val minRoiSize = preview?.let { RoiGeometry.minimumSize(it.width, it.height) }
        ?: RoiGeometry.MIN_SIZE_PX
    val validRoi = roi?.let { !it.isEmpty && it.width() >= minRoiSize && it.height() >= minRoiSize } == true

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val cancelledMessage = stringResource(R.string.analysis_cancelled)
    val liftNames = Lift.entries.associateWith { liftDisplayName(it) }
    val variantNames = LiftVariant.entries.associateWith { variantDisplayName(it) }

    val ctaHint = when {
        analyzing -> null
        selectedUri == null -> stringResource(R.string.import_cta_need_video)
        !roiConfirmed -> stringResource(R.string.import_cta_need_roi)
        !validSettings -> stringResource(R.string.import_invalid_settings)
        else -> null
    }
    val canAnalyze = !analyzing && validSettings && validRoi && roiConfirmed

    fun startAnalysis() {
        selectedUri?.let { uri ->
            onAnalyze(
                AnalysisRequest(
                    uri = uri,
                    lift = lift,
                    variant = variant,
                    cameraAngleDeg = cameraAngle ?: return@let,
                    plateDiameterM = plateDiameter ?: return@let,
                    loadKg = if (loadText.isBlank()) null else loadKg,
                    cameraLevel = cameraLevel,
                    analyzePose = analyzePose,
                    roi = RectF(roi ?: return@let),
                ),
            )
        }
    }

    val actionBar: @Composable () -> Unit = {
        if (analyzing) {
            PrimaryActionBar {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.import_analysis_progress, (analysisProgress * 100).toInt()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                LinearProgressIndicator(
                    progress = { analysisProgress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
                Text(
                    stringResource(R.string.analysis_notification_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = VbtTheme.colors.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        onCancel()
                        scope.launch { snackbar.showSnackbar(cancelledMessage) }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.import_cancel_analysis))
                }
            }
        } else {
            PrimaryActionBar(hint = ctaHint) {
                PrimaryButton(
                    text = stringResource(R.string.import_start_analysis),
                    onClick = ::startAnalysis,
                    enabled = canAnalyze,
                )
            }
        }
    }

    val hero: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
            VideoHero(
                preview = preview,
                roi = roi,
                loading = loading,
                roiConfirmed = roiConfirmed,
                autoRoiUnavailable = autoRoiUnavailable,
                enabled = !analyzing,
                onChooseVideo = { picker.launch(arrayOf("video/*")) },
                onRoiChanged = {
                    roi = it
                    roiConfirmed = false
                },
            )
            videoMeta?.let { meta ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s), verticalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
                    fileName?.let {
                        StatusChip(
                            text = stringResource(R.string.import_file_name, it),
                            maxWidth = 220.dp,
                        )
                    }
                    StatusChip(stringResource(R.string.import_duration, meta.durationSec))
                    StatusChip(stringResource(R.string.import_resolution, meta.width, meta.height))
                    StatusChip(stringResource(R.string.import_effective_fps, meta.effectiveFps))
                }
            }
            if (selectedUri != null && !loading && preview != null) {
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("video/*")) },
                    enabled = !analyzing,
                    modifier = Modifier.heightIn(min = MinTouchTarget),
                ) {
                    Text(stringResource(R.string.import_replace_video))
                }
            }
        }
    }

    val roiCard: @Composable () -> Unit = {
        if (preview != null) {
            SectionCard {
                SectionLabel(stringResource(R.string.import_roi_title))
                Text(
                    stringResource(if (autoRoiUnavailable) R.string.import_roi_manual_required else R.string.import_roi_confirm_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.import_roi_edit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = VbtTheme.colors.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
                    OutlinedButton(
                        onClick = {
                            roi = null
                            roiConfirmed = false
                        },
                        enabled = !analyzing && roi != null,
                        modifier = Modifier.heightIn(min = MinTouchTarget),
                    ) {
                        Text(stringResource(R.string.import_roi_reset))
                    }
                    FilledTonalButton(
                        onClick = { roiConfirmed = true },
                        enabled = !analyzing && validRoi && !roiConfirmed,
                        modifier = Modifier.heightIn(min = MinTouchTarget),
                    ) {
                        Text(stringResource(if (roiConfirmed) R.string.import_roi_confirmed else R.string.import_roi_confirm))
                    }
                }
            }
        }
    }

    val settings: @Composable () -> Unit = {
        videoMeta?.let {
            SectionLabel(stringResource(R.string.import_lift))
            SegmentedPicker(
                options = Lift.entries,
                selected = lift,
                label = { liftNames.getValue(it) },
                onSelect = {
                    lift = it
                    variant = defaultVariant(it)
                },
                enabled = !analyzing,
            )
            SectionLabel(stringResource(R.string.import_variant))
            SegmentedPicker(
                options = variantsFor(lift),
                selected = variant,
                label = { variantNames.getValue(it) },
                onSelect = { variant = it },
                enabled = !analyzing,
            )

            SectionLabel(stringResource(R.string.import_analysis_settings))
            SectionCard {
                Row(horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
                    OutlinedTextField(
                        value = loadText,
                        onValueChange = { loadText = it },
                        enabled = !analyzing,
                        label = { Text(stringResource(R.string.import_load_label)) },
                        suffix = { Text(stringResource(R.string.unit_kg)) },
                        isError = !validLoad,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = plateDiameterText,
                        onValueChange = { plateDiameterText = it },
                        enabled = !analyzing,
                        label = { Text(stringResource(R.string.import_plate_label)) },
                        suffix = { Text(stringResource(R.string.unit_m)) },
                        isError = !validPlate,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = cameraAngleText,
                    onValueChange = { cameraAngleText = it },
                    enabled = !analyzing,
                    label = { Text(stringResource(R.string.import_camera_label)) },
                    suffix = { Text(stringResource(R.string.unit_deg)) },
                    isError = !validAngle,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(VbtSpacing.s)) {
                    AssistChip(
                        onClick = { cameraAngleText = "90" },
                        enabled = !analyzing,
                        label = { Text(stringResource(R.string.import_camera_preset_side)) },
                    )
                    AssistChip(
                        onClick = { cameraAngleText = "45" },
                        enabled = !analyzing,
                        label = { Text(stringResource(R.string.import_camera_preset_diagonal)) },
                    )
                }
            }

            SectionCard(padding = VbtSpacing.s) {
                SwitchRow(
                    title = stringResource(R.string.import_camera_level),
                    checked = cameraLevel,
                    onCheckedChange = { cameraLevel = it },
                    enabled = !analyzing,
                )
                HorizontalDivider(color = VbtTheme.colors.outline.copy(alpha = 0.6f))
                SwitchRow(
                    title = stringResource(R.string.import_analyze_pose),
                    checked = analyzePose,
                    onCheckedChange = { analyzePose = it },
                    enabled = !analyzing,
                    description = stringResource(R.string.import_pose_hint),
                )
            }
            if (!validSettings) {
                Text(
                    stringResource(R.string.import_invalid_settings),
                    style = MaterialTheme.typography.bodySmall,
                    color = VbtTheme.colors.warning,
                )
            }
        }
    }

    val banners: @Composable () -> Unit = {
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

    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp >= 600 || configuration.screenWidthDp > configuration.screenHeightDp

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.import_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                actions = {
                    TextButton(
                        onClick = onShowHistory,
                        enabled = !analyzing,
                        modifier = Modifier.heightIn(min = MinTouchTarget),
                    ) {
                        Text(stringResource(R.string.history_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (!wide) actionBar() },
    ) { inner ->
        if (!wide) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = VbtSpacing.l, vertical = VbtSpacing.m),
                verticalArrangement = Arrangement.spacedBy(VbtSpacing.l),
            ) {
                banners()
                hero()
                roiCard()
                settings()
                Spacer(Modifier.height(16.dp))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize().padding(inner).padding(horizontal = VbtSpacing.xl, vertical = VbtSpacing.m),
                horizontalArrangement = Arrangement.spacedBy(VbtSpacing.xl),
            ) {
                Column(
                    modifier = Modifier.weight(0.45f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(VbtSpacing.l),
                ) {
                    hero()
                    roiCard()
                }
                Column(modifier = Modifier.weight(0.55f)) {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(VbtSpacing.l),
                    ) {
                        banners()
                        settings()
                    }
                    actionBar()
                }
            }
        }
    }
}

@Composable
private fun VideoHero(
    preview: Bitmap?,
    roi: RectF?,
    loading: Boolean,
    roiConfirmed: Boolean,
    autoRoiUnavailable: Boolean,
    enabled: Boolean,
    onChooseVideo: () -> Unit,
    onRoiChanged: (RectF) -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(BorderStroke(1.dp, VbtTheme.colors.outline.copy(alpha = 0.6f)), shape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            preview != null -> RoiSelector(bitmap = preview, roi = roi, onRoiChanged = onRoiChanged)
            loading -> LoadingFrame()
            else -> EmptyDropZone(enabled = enabled, onChooseVideo = onChooseVideo)
        }
        if (preview != null) {
            val (text, tone) = when {
                roiConfirmed -> stringResource(R.string.import_status_confirmed) to ChipTone.PRIMARY
                autoRoiUnavailable -> stringResource(R.string.import_status_manual) to ChipTone.WARNING
                else -> stringResource(R.string.import_status_suggested) to ChipTone.PRIMARY
            }
            StatusChip(
                text = text,
                tone = tone,
                modifier = Modifier.align(Alignment.TopStart).padding(VbtSpacing.s),
            )
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
