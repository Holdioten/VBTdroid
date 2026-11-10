package com.vbtdroid.app.ui.report

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vbtdroid.app.R
import java.util.Locale

/** MCV shown with two decimals across hero, tiles, and history (display only). */
internal fun formatMcv(value: Double?): String? =
    value?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.2f", it) }

/** Load without trailing zeros: 100.0 -> "100", 2.5 -> "2.5". */
internal fun formatKgValue(value: Double?): String? =
    value?.takeIf { it.isFinite() }?.let {
        String.format(Locale.US, "%.2f", it).trimEnd('0').trimEnd('.')
    }

@Composable
internal fun qualityWarningText(warning: String): String = stringResource(
    when {
        warning == "LOW_TRACK_CONFIDENCE" -> R.string.quality_low_track_confidence
        warning == "HIGH_OCCLUSION" -> R.string.quality_high_occlusion
        warning == "CAMERA_NOT_LEVEL" -> R.string.quality_camera_not_level
        warning == "LOW_CALIBRATION" -> R.string.quality_low_calibration
        warning == "LOW_REP_CONFIDENCE" -> R.string.quality_low_rep_confidence
        warning == "POSE_UNAVAILABLE" -> R.string.quality_pose_unavailable
        warning == "POSE_PARTIAL" -> R.string.quality_pose_partial
        warning == "NO_VALID_REPS" -> R.string.quality_no_valid_reps
        warning == "LOAD_REQUIRED" -> R.string.quality_load_required
        warning == "CAMERA_NOT_LEVEL_ESTIMATE" -> R.string.quality_camera_estimate
        warning == "ANGLES_UNAVAILABLE_OCCLUSION" -> R.string.quality_angles_unavailable_occlusion
        warning == "INCOMPLETE_REP_NO_LOCKOUT" -> R.string.quality_incomplete_rep
        warning == "INCOMPLETE_REP" -> R.string.quality_incomplete_rep
        warning == "NO_LOCKOUT" -> R.string.quality_no_lockout
        warning == "CAMERA_MOVED" -> R.string.quality_camera_moved
        warning == "TRACK_LOST" -> R.string.quality_track_lost
        warning == "LATERAL_DRIFT" -> R.string.quality_lateral_drift
        warning == "TRACK_NOISY" -> R.string.quality_track_noisy
        warning == "POSE_LOW_CONFIDENCE" -> R.string.quality_pose_low_confidence
        warning == "ACCELERATION_UNAVAILABLE" -> R.string.quality_acceleration_unavailable
        warning.contains("NON_MONOTONIC_CONCENTRIC") -> R.string.quality_non_monotonic
        warning.startsWith("REP_SKIPPED_") -> R.string.quality_rep_skipped
        else -> R.string.quality_unknown
    },
)
