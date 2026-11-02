package com.vbtdroid.core.io

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RepExport(
    val index: Int,
    val metrics: Map<String, Double?>,
    val quality: Map<String, Double>,
)

@Serializable
data class SetExport(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    val lift: String,
    @SerialName("load_kg") val loadKg: Double?,
    @SerialName("plate_diameter_mm") val plateDiameterMm: Double,
    @SerialName("camera_azimuth_deg") val cameraAngleDeg: Double,
    val reps: List<RepExport>,
    val warnings: List<String>,
)

object JsonExport {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(export: SetExport): String = json.encodeToString(export)
}
