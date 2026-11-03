package com.vbtdroid.core.io

import com.vbtdroid.core.model.MetricId
import com.vbtdroid.core.quality.Quality
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JsonExportTest {
    @Test
    fun `round trip`() {
        val export = SetExport(
            lift = "squat",
            loadKg = 100.0,
            plateDiameterMm = 450.0,
            cameraAngleDeg = 45.0,
            reps = emptyList(),
            warnings = listOf("VFR"),
        )

        val json = JsonExport.encode(export)
        val back = Json.decodeFromString<SetExport>(json)

        assertEquals(export, back)
        assertTrue(json.contains("\"plate_diameter_mm\""))
        assertTrue(json.contains("\"camera_azimuth_deg\""))
    }

    @Test
    fun `rep metrics use stable metric ids and serialize defaults`() {
        val export = SetExport(
            lift = "squat",
            loadKg = null,
            plateDiameterMm = 450.0,
            cameraAngleDeg = 90.0,
            reps = listOf(
                RepExport(
                    index = 1,
                    metrics = mapOf(MetricId.KNEE_ANGLE.id to 92.0),
                    quality = mapOf("track_confidence" to 0.95),
                ),
            ),
            warnings = emptyList(),
        )

        val json = JsonExport.encode(export)
        val back = Json.decodeFromString<SetExport>(json)

        assertEquals(1, back.schemaVersion)
        assertEquals(92.0, back.reps.single().metrics[MetricId.KNEE_ANGLE.id])
        assertTrue(json.contains("\"knee_angle_deg\""))
        assertTrue(json.contains("\"schema_version\""))
    }

    @Test
    fun `quality preserves inputs and returns warnings in deterministic order`() {
        val quality = Quality.evaluate(
            trackConfidence = 0.5,
            occlusionPct = 21.0,
            repConfidence = 0.7,
            calibrationConfidence = 0.4,
            cameraLevel = false,
        )

        assertEquals(0.5, quality.trackConfidence)
        assertEquals(21.0, quality.occlusionPct)
        assertEquals(0.7, quality.repConfidence)
        assertEquals(0.4, quality.calibrationConfidence)
        assertEquals(false, quality.cameraLevel)
        assertEquals(
            listOf(
                "LOW_TRACK_CONFIDENCE",
                "HIGH_OCCLUSION",
                "ANGLES_UNAVAILABLE_OCCLUSION",
                "CAMERA_NOT_LEVEL",
                "CAMERA_NOT_LEVEL_ESTIMATE",
                "LOW_CALIBRATION",
                "LOW_REP_CONFIDENCE",
            ),
            quality.warnings,
        )
    }
}
