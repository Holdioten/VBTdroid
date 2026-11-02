package com.vbtdroid.core.calibration

import com.vbtdroid.core.model.BarSample
import com.vbtdroid.core.model.PlateObservation
import kotlin.math.PI
import kotlin.math.sin

data class Calibration(
    val plateDiameterM: Double,
    val pxPerM: Double,
    val cameraAngleDeg: Double,
    val cameraLevel: Boolean = true,
) {
    fun toMeters(observation: PlateObservation): BarSample {
        val angleRadians = cameraAngleDeg * PI / 180.0
        return BarSample(
            tSec = observation.tSec,
            xM = (observation.xPx / pxPerM) / sin(angleRadians),
            yM = -observation.yPx / pxPerM,
        )
    }

    companion object {
        fun fromPlate(
            plateDiameterM: Double,
            majorAxisPx: Double,
            cameraAngleDeg: Double,
        ): Calibration {
            require(plateDiameterM > 0.0) { "plateDiameterM must be positive" }
            require(majorAxisPx > 0.0) { "majorAxisPx must be positive" }
            require(cameraAngleDeg > 0.0 && cameraAngleDeg <= 90.0) {
                "cameraAngleDeg must be in (0, 90]"
            }
            return Calibration(
                plateDiameterM = plateDiameterM,
                pxPerM = majorAxisPx / plateDiameterM,
                cameraAngleDeg = cameraAngleDeg,
            )
        }
    }
}
