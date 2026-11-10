package com.vbtdroid.core.metrics

import com.vbtdroid.core.calibration.Calibration
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.model.PlateObservation
import com.vbtdroid.core.phases.PhaseSegmenter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class GoldenParityTest {
    private val json = Json

    @Test
    fun `kotlin matches python synthetic squat golden samples`() {
        val sampleRoot = json.parseToJsonElement(
            Path.of("../prototype/golden/synthetic_squat_samples.json").toFile().readText(),
        ).jsonObject
        val goldenText = Path.of("../prototype/golden/synthetic_squat.json").toFile().readText()
        val goldenRoot = json.parseToJsonElement(goldenText).jsonObject
        val majorAxis = sampleRoot.getValue("major_axis_px").jsonPrimitive.double
        val calibration = Calibration.fromPlate(
            plateDiameterM = sampleRoot.getValue("plate_diameter_m").jsonPrimitive.double,
            majorAxisPx = majorAxis,
            cameraAngleDeg = sampleRoot.getValue("camera_angle_deg").jsonPrimitive.double,
        )
        val observations = sampleRoot.getValue("samples").jsonArray.map { element ->
            val sample = element.jsonObject
            PlateObservation(
                tSec = sample.getValue("t_sec").jsonPrimitive.double,
                xPx = sample.getValue("x_px").jsonPrimitive.double,
                yPx = sample.getValue("y_px").jsonPrimitive.double,
                majorAxisPx = majorAxis,
                confidence = 1.0,
            )
        }

        val motion = Kinematics.derive(observations.map(calibration::toMeters))
        val slice = PhaseSegmenter.segment(motion, Lift.SQUAT).single()
        val actual = RepMetricsCalculator.compute(slice, motion, loadKg = null)
        val expectedRep = goldenRoot.getValue("reps").jsonArray.single().jsonObject
        val expected = expectedRep.getValue("metrics").jsonObject

        assertEquals(expected.getValue("rep_duration_s").jsonPrimitive.double, actual.durationS, 1e-9)
        assertEquals(expected.getValue("rom_total_m").jsonPrimitive.double, actual.romTotalM, 1e-9)
        assertEquals(expected.getValue("rom_concentric_m").jsonPrimitive.double, actual.romConcentricM, 1e-9)
        assertEquals(expected.getValue("mcv_mps").jsonPrimitive.double, actual.mcvMps, 1e-9)
        assertEquals(expected.getValue("mpv_mps").jsonPrimitive.double, actual.mpvMps!!, 1e-9)
        assertEquals(expected.getValue("peak_velocity_mps").jsonPrimitive.double, actual.peakVelocityMps!!, 1e-9)
        // The synthetic curve has no significant velocity dip, so the Vpeak1 -> Vmin
        // definition (correctly) reports no sticking region.
        val expectedSticking = expected.getValue("sticking_point_pct")
        if (expectedSticking is JsonNull) {
            assertNull(actual.stickingPointPct)
        } else {
            assertEquals(expectedSticking.jsonPrimitive.double, actual.stickingPointPct!!, 1e-9)
        }
        assertTrue(goldenText.contains("\"rep_duration_s\""))
        assertFalse(goldenText.contains("\"duration_s\""))
    }
}
