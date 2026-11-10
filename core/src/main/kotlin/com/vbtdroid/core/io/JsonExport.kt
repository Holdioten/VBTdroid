package com.vbtdroid.core.io

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PhaseExport(
    val type: String,
    @SerialName("start_sec") val startSec: Double,
    @SerialName("end_sec") val endSec: Double,
    val manual: Boolean = false,
)

@Serializable
data class RepExport(
    val index: Int,
    val metrics: Map<String, Double?>,
    val quality: Map<String, Double> = emptyMap(),
    val warnings: List<String> = emptyList(),
    val phases: List<PhaseExport> = emptyList(),
)

@Serializable
data class CalibrationExport(
    @SerialName("px_per_m") val pxPerM: Double,
    @SerialName("camera_level") val cameraLevel: Boolean,
    @SerialName("calibration_confidence") val calibrationConfidence: Double,
)

@Serializable
data class FilterConfigExport(
    val method: String = "savitzky_golay",
    @SerialName("mean_window") val meanWindow: Int,
    @SerialName("mean_poly_order") val meanPolyOrder: Int,
    @SerialName("peak_window") val peakWindow: Int,
    @SerialName("peak_poly_order") val peakPolyOrder: Int,
    @SerialName("mean_applies_to") val meanAppliesTo: List<String>,
    @SerialName("peak_applies_to") val peakAppliesTo: List<String>,
    /** Effective window in seconds; present on new exports so 30/60 fps clips are comparable. */
    @SerialName("mean_window_sec") val meanWindowSec: Double? = null,
    @SerialName("peak_window_sec") val peakWindowSec: Double? = null,
)

@Serializable
data class SummaryExport(
    @SerialName("rep_count") val repCount: Int,
    @SerialName("mcv_avg_mps") val mcvAvgMps: Double?,
    @SerialName("best_mcv_mps") val bestMcvMps: Double?,
    @SerialName("set_velocity_loss_pct") val velocityLossPct: Double?,
    @SerialName("volume_load") val totalVolumeKg: Double?,
    @SerialName("work_j") val totalWorkJ: Double?,
)

@Serializable
data class LvpExport(
    @SerialName("lvp_slope") val slope: Double? = null,
    @SerialName("lvp_intercept") val intercept: Double? = null,
    @SerialName("lvp_r2") val r2: Double? = null,
    @SerialName("v1rm_mps") val v1rmMps: Double,
    @SerialName("v1rm_source") val v1rmSource: String,
    @SerialName("estimated_1rm_kg") val estimated1RmKg: Double? = null,
    @SerialName("load_point_count") val pointCount: Int,
    @SerialName("unavailable_reason") val unavailableReason: String? = null,
)

@Serializable
data class SetExport(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    val lift: String,
    val variant: String = "default",
    @SerialName("load_kg") val loadKg: Double?,
    @SerialName("plate_diameter_mm") val plateDiameterMm: Double,
    @SerialName("camera_azimuth_deg") val cameraAngleDeg: Double,
    @SerialName("video_uri") val videoUri: String = "",
    @SerialName("created_at_epoch_ms") val createdAtEpochMs: Long = 0L,
    val reps: List<RepExport>,
    val warnings: List<String>,
    val calibration: CalibrationExport? = null,
    val quality: Map<String, Double> = emptyMap(),
    val summary: SummaryExport? = null,
    val filters: FilterConfigExport? = null,
    val lvp: LvpExport? = null,
)

object JsonExport {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
    }

    fun encode(export: SetExport): String = json.encodeToString(export)

    fun encodeRep(rep: RepExport): String = json.encodeToString(rep)

    fun decode(value: String): SetExport = json.decodeFromString(value)
}
