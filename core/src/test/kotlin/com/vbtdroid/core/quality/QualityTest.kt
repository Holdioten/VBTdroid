package com.vbtdroid.core.quality

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QualityTest {
    @Test
    fun `missing load camera tilt and occlusion execute degradation rules`() {
        val quality = Quality.evaluate(
            trackConfidence = 0.5,
            occlusionPct = 21.0,
            repConfidence = 0.7,
            calibrationConfidence = 0.9,
            cameraLevel = false,
            loadProvided = false,
        )

        assertTrue("LOAD_REQUIRED" in quality.warnings)
        assertTrue("CAMERA_NOT_LEVEL_ESTIMATE" in quality.warnings)
        assertTrue("ANGLES_UNAVAILABLE_OCCLUSION" in quality.warnings)
        assertTrue("LOW_REP_CONFIDENCE" in quality.warnings)
        assertTrue(quality.velocityEstimated)
        assertFalse(quality.anglesAvailable)
        assertEquals(listOf("LOAD_REQUIRED"), quality.metricReasons["force"])
        assertEquals(listOf("ANGLES_UNAVAILABLE_OCCLUSION"), quality.metricReasons["joint_angles"])
    }
}
