package com.vbtdroid.core.calibration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PairCalibratorTest {
    @Test
    fun `larger primary is scaled from the smaller anchor`() {
        val result = PairCalibrator.calibrate(
            primaryBoxPx = 457.0,
            secondaryBoxPx = 291.6,
            ratios = listOf(1.79, 1.81, 1.82, 1.80),
            coverage = 1.0,
            correlation = 0.999,
        )

        assertTrue(result.usedPair)
        assertNull(result.fallbackReason)
        assertEquals(291.6 * 1.805, result.diameterPx, 0.2)
    }

    @Test
    fun `smaller primary stays the anchor`() {
        val result = PairCalibrator.calibrate(
            primaryBoxPx = 291.6,
            secondaryBoxPx = 457.0,
            ratios = listOf(1.8, 1.8),
            coverage = 1.0,
            correlation = 0.999,
        )

        assertFalse(result.usedPair)
        assertEquals(291.6, result.diameterPx, 1e-9)
    }

    @Test
    fun `missing or poorly tracked secondary falls back`() {
        assertEquals(
            PairCalibrator.NO_SECONDARY,
            PairCalibrator.calibrate(457.0, null, emptyList(), 1.0, 1.0).fallbackReason,
        )
        assertEquals(
            PairCalibrator.LOW_COVERAGE,
            PairCalibrator.calibrate(457.0, 291.6, listOf(1.8, 1.8), 0.5, 0.99).fallbackReason,
        )
        assertEquals(
            PairCalibrator.LOW_CORRELATION,
            PairCalibrator.calibrate(457.0, 291.6, listOf(1.8, 1.8), 1.0, 0.5).fallbackReason,
        )
        assertEquals(
            PairCalibrator.FEW_RATIOS,
            PairCalibrator.calibrate(457.0, 291.6, listOf(1.8), 1.0, 0.99).fallbackReason,
        )
    }

    @Test
    fun `implausible or unstable ratios are rejected`() {
        assertEquals(
            PairCalibrator.RATIO_OUT_OF_RANGE,
            PairCalibrator.calibrate(457.0, 291.6, listOf(4.0, 4.1), 1.0, 0.99).fallbackReason,
        )
        assertEquals(
            PairCalibrator.RATIO_UNSTABLE,
            PairCalibrator.calibrate(457.0, 291.6, listOf(1.2, 1.9), 1.0, 0.99).fallbackReason,
        )
    }

    @Test
    fun `outlier ratios are trimmed before the estimate`() {
        val result = PairCalibrator.calibrate(
            primaryBoxPx = 457.0,
            secondaryBoxPx = 291.6,
            ratios = listOf(1.80, 1.81, 1.82, 1.83, 2.30),
            coverage = 1.0,
            correlation = 0.999,
        )

        assertTrue(result.usedPair)
        assertEquals(291.6 * 1.815, result.diameterPx, 0.5)
    }
}
