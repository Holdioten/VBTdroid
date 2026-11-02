package com.vbtdroid.core

import com.vbtdroid.core.model.MetricId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MetricIdTest {
    @Test
    fun `ids are unique non-empty snake case and have units`() {
        val ids = MetricId.entries.map(MetricId::id)

        assertTrue(ids.all(String::isNotBlank))
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all(SNAKE_CASE::matches), "Every metric ID must use snake_case")
        assertTrue(MetricId.entries.all { it.unit.isNotBlank() })
    }

    @Test
    fun `technical fields are not business metric ids`() {
        val ids = MetricId.entries.map(MetricId::id)
        val forbidden = setOf("pts", "fps", "px_per_m", "track_confidence", "occlusion_pct")

        assertTrue(ids.none { it in forbidden })
        assertFalse(ids.any { it.startsWith("camera_") })
    }

    @Test
    fun `required business metrics are present`() {
        val ids = MetricId.entries.map(MetricId::id).toSet()
        val required = setOf(
            "mean_velocity_mps",
            "mcv_mps",
            "mpv_mps",
            "peak_velocity_mps",
            "rom_total_m",
            "rom_concentric_m",
            "velocity_loss_pct",
            "sticking_point_pct",
            "mean_force_n",
            "peak_force_n",
            "mean_power_w",
            "peak_power_w",
            "impulse_ns",
            "work_j",
            "estimated_1rm_kg",
            "hip_angle_deg",
            "knee_angle_deg",
            "trunk_angle_deg",
            "elbow_angle_deg",
            "shoulder_angle_deg",
        )

        assertTrue(ids.containsAll(required), "Missing required business metrics: ${required - ids}")
    }

    private companion object {
        val SNAKE_CASE = Regex("[a-z][a-z0-9]*(?:_[a-z0-9]+)*")
    }
}
