package com.vbtdroid.app.ui.import

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vbtdroid.app.R
import com.vbtdroid.app.analysis.AnalysisRequest
import com.vbtdroid.app.video.VideoDecoder
import com.vbtdroid.app.video.VideoLimits
import com.vbtdroid.app.video.VideoMeta
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.model.LiftVariant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val validRoi = roi?.let { !it.isEmpty && it.width() >= 5f && it.height() >= 5f } == true

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.import_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch(arrayOf("video/*")) }, enabled = !analyzing) {
                Text(stringResource(R.string.import_choose_video))
            }
            OutlinedButton(onClick = onShowHistory, enabled = !analyzing) {
                Text(stringResource(R.string.history_title))
            }
        }
        Spacer(Modifier.height(16.dp))
        if (loading) CircularProgressIndicator()
        videoError?.let { Text(stringResource(R.string.import_error_reason, it), color = MaterialTheme.colorScheme.error) }
        fileName?.let { Text(stringResource(R.string.import_file_name, it)) }
        videoMeta?.let { meta ->
            Text(stringResource(R.string.import_duration, meta.durationSec))
            Text(stringResource(R.string.import_resolution, meta.width, meta.height))
            Text(stringResource(R.string.import_effective_fps, meta.effectiveFps))

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.import_analysis_settings), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.import_lift))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Lift.entries.forEach { option ->
                    FilterChip(
                        selected = lift == option,
                        onClick = {
                            lift = option
                            variant = defaultVariant(option)
                        },
                        enabled = !analyzing,
                        label = { Text(liftDisplayName(option)) },
                    )
                }
            }
            Text(stringResource(R.string.import_variant))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                variantsFor(lift).forEach { option ->
                    FilterChip(
                        selected = variant == option,
                        onClick = { variant = option },
                        enabled = !analyzing,
                        label = { Text(variantDisplayName(option)) },
                    )
                }
            }
            OutlinedTextField(
                value = loadText,
                onValueChange = { loadText = it },
                enabled = !analyzing,
                label = { Text(stringResource(R.string.import_load_kg)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
            OutlinedTextField(
                value = plateDiameterText,
                onValueChange = { plateDiameterText = it },
                enabled = !analyzing,
                label = { Text(stringResource(R.string.import_plate_diameter_m)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
            OutlinedTextField(
                value = cameraAngleText,
                onValueChange = { cameraAngleText = it },
                enabled = !analyzing,
                label = { Text(stringResource(R.string.import_camera_angle_deg)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.import_camera_level))
                Switch(checked = cameraLevel, onCheckedChange = { cameraLevel = it }, enabled = !analyzing)
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.import_roi_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(if (autoRoiUnavailable) R.string.import_roi_manual_required else R.string.import_roi_confirm_hint))
            preview?.let { frame ->
                RoiSelector(
                    bitmap = frame,
                    roi = roi,
                    onRoiChanged = {
                        roi = it
                        roiConfirmed = false
                    },
                )
            }
            Button(
                onClick = { roiConfirmed = true },
                enabled = !analyzing && validRoi && !roiConfirmed,
            ) {
                Text(stringResource(if (roiConfirmed) R.string.import_roi_confirmed else R.string.import_roi_confirm))
            }

            if (!validSettings) Text(stringResource(R.string.import_invalid_settings))
            Button(
                onClick = {
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
                                roi = RectF(roi ?: return@let),
                            ),
                        )
                    }
                },
                enabled = !analyzing && validSettings && validRoi && roiConfirmed,
            ) {
                Text(stringResource(R.string.import_start_analysis))
            }
        }
        if (analyzing) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { analysisProgress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.import_analysis_progress, (analysisProgress * 100).toInt()))
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.import_cancel_analysis)) }
        }
        analysisError?.let {
            Text(stringResource(R.string.import_analysis_failed_reason, it), color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun defaultVariant(lift: Lift): LiftVariant = when (lift) {
    Lift.SQUAT -> LiftVariant.HIGH_BAR
    Lift.BENCH -> LiftVariant.TOUCH_AND_GO
    Lift.DEADLIFT -> LiftVariant.CONVENTIONAL
}

private fun variantsFor(lift: Lift): List<LiftVariant> = when (lift) {
    Lift.SQUAT -> listOf(LiftVariant.HIGH_BAR, LiftVariant.LOW_BAR)
    Lift.BENCH -> listOf(LiftVariant.PAUSED, LiftVariant.TOUCH_AND_GO)
    Lift.DEADLIFT -> listOf(LiftVariant.CONVENTIONAL, LiftVariant.SUMO)
}

@Composable
private fun liftDisplayName(lift: Lift): String = stringResource(
    when (lift) {
        Lift.SQUAT -> R.string.lift_squat
        Lift.BENCH -> R.string.lift_bench
        Lift.DEADLIFT -> R.string.lift_deadlift
    },
)

@Composable
private fun variantDisplayName(variant: LiftVariant): String = stringResource(
    when (variant) {
        LiftVariant.HIGH_BAR -> R.string.variant_high_bar
        LiftVariant.LOW_BAR -> R.string.variant_low_bar
        LiftVariant.CONVENTIONAL -> R.string.variant_conventional
        LiftVariant.SUMO -> R.string.variant_sumo
        LiftVariant.PAUSED -> R.string.variant_paused
        LiftVariant.TOUCH_AND_GO -> R.string.variant_touch_and_go
        LiftVariant.DEFAULT -> R.string.variant_default
    },
)

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
