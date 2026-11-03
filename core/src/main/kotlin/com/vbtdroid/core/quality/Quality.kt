package com.vbtdroid.core.quality

data class QualityFlags(
    val trackConfidence: Double,
    val occlusionPct: Double,
    val repConfidence: Double,
    val calibrationConfidence: Double,
    val cameraLevel: Boolean,
    val warnings: List<String>,
    val velocityEstimated: Boolean = false,
    val anglesAvailable: Boolean = true,
    val metricReasons: Map<String, List<String>> = emptyMap(),
)

object Quality {
    fun evaluate(
        trackConfidence: Double,
        occlusionPct: Double,
        repConfidence: Double,
        calibrationConfidence: Double,
        cameraLevel: Boolean,
        loadProvided: Boolean = true,
    ): QualityFlags {
        val warnings = buildList {
            if (trackConfidence < 0.6) add("LOW_TRACK_CONFIDENCE")
            if (occlusionPct > 20.0) {
                add("HIGH_OCCLUSION")
                add("ANGLES_UNAVAILABLE_OCCLUSION")
            }
            if (!cameraLevel) {
                add("CAMERA_NOT_LEVEL")
                add("CAMERA_NOT_LEVEL_ESTIMATE")
            }
            if (calibrationConfidence < 0.5) add("LOW_CALIBRATION")
            if (repConfidence < 0.8) add("LOW_REP_CONFIDENCE")
            if (!loadProvided) add("LOAD_REQUIRED")
        }
        val metricReasons = buildMap<String, List<String>> {
            if (trackConfidence < 0.6) put("velocity", listOf("LOW_TRACK_CONFIDENCE"))
            if (!cameraLevel) {
                put("vertical_velocity", listOf("CAMERA_NOT_LEVEL_ESTIMATE"))
                put("pr_eligibility", listOf("CAMERA_NOT_LEVEL"))
            }
            if (occlusionPct > 20.0) put("joint_angles", listOf("ANGLES_UNAVAILABLE_OCCLUSION"))
            if (!loadProvided) {
                put("force", listOf("LOAD_REQUIRED"))
                put("power", listOf("LOAD_REQUIRED"))
                put("work", listOf("LOAD_REQUIRED"))
                put("estimated_1rm_kg", listOf("LOAD_REQUIRED"))
            }
        }

        return QualityFlags(
            trackConfidence = trackConfidence,
            occlusionPct = occlusionPct,
            repConfidence = repConfidence,
            calibrationConfidence = calibrationConfidence,
            cameraLevel = cameraLevel,
            warnings = warnings,
            velocityEstimated = !cameraLevel,
            anglesAvailable = occlusionPct <= 20.0,
            metricReasons = metricReasons,
        )
    }
}
