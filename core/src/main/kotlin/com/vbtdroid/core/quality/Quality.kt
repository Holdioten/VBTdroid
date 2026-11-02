package com.vbtdroid.core.quality

data class QualityFlags(
    val trackConfidence: Double,
    val occlusionPct: Double,
    val repConfidence: Double,
    val calibrationConfidence: Double,
    val cameraLevel: Boolean,
    val warnings: List<String>,
)

object Quality {
    fun evaluate(
        trackConfidence: Double,
        occlusionPct: Double,
        repConfidence: Double,
        calibrationConfidence: Double,
        cameraLevel: Boolean,
    ): QualityFlags {
        val warnings = buildList {
            if (trackConfidence < 0.6) add("LOW_TRACK_CONFIDENCE")
            if (occlusionPct > 20.0) add("HIGH_OCCLUSION")
            if (!cameraLevel) add("CAMERA_NOT_LEVEL")
            if (calibrationConfidence < 0.5) add("LOW_CALIBRATION")
            if (repConfidence < 0.8) add("LOW_REP_CONFIDENCE")
        }

        return QualityFlags(
            trackConfidence = trackConfidence,
            occlusionPct = occlusionPct,
            repConfidence = repConfidence,
            calibrationConfidence = calibrationConfidence,
            cameraLevel = cameraLevel,
            warnings = warnings,
        )
    }
}
