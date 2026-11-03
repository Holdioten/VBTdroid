package com.vbtdroid.core.metrics

import com.vbtdroid.core.phases.Phase
import com.vbtdroid.core.phases.PhaseType
import com.vbtdroid.core.phases.RepSlice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RepMetricsTest {
    private fun motion(t: Double, y: Double, vy: Double, ay: Double? = 0.0, x: Double = 0.0) =
        MotionSample(t, x, y, 0.0, vy, 0.0, ay)

    private fun concentricRep(start: Double = 0.0, end: Double = 1.0) =
        RepSlice(0, start, end, listOf(Phase(PhaseType.CONCENTRIC, start, end)))

    @Test
    fun `constant concentric velocity gives expected mcv and rom`() {
        val samples = (0..20).map { motion(it * 0.05, it * 0.05 * 0.5, 0.5) }

        val metrics = RepMetricsCalculator.compute(concentricRep(), samples, null)

        assertEquals(0.5, metrics.mcvMps, 1e-6)
        assertEquals(0.5, metrics.romConcentricM, 1e-6)
        assertEquals(0.5, metrics.peakVelocityMps!!, 1e-6)
        assertNull(metrics.meanForceN)
    }

    @Test
    fun `velocity loss uses best rep`() {
        val samples = (0..20).map { motion(it * 0.05, it * 0.05 * 0.6, 0.6) }

        val metrics = RepMetricsCalculator.compute(concentricRep(), samples, null, bestMcvMps = 0.8)

        assertEquals(25.0, metrics.velocityLossPct!!, 1e-6)
    }

    @Test
    fun `load metrics use external load`() {
        val samples = (0..20).map { motion(it * 0.05, it * 0.05 * 0.5, 0.5) }

        val metrics = RepMetricsCalculator.compute(concentricRep(), samples, loadKg = 100.0)

        assertEquals(980.665, metrics.meanForceN!!, 1e-3)
        assertEquals(490.3325, metrics.meanPowerW!!, 1e-3)
    }

    @Test
    fun `mpv equals mcv when acceleration never becomes negative`() {
        val samples = listOf(
            motion(0.0, 0.0, 0.2, 0.0),
            motion(0.5, 0.25, 0.5, 0.0),
            motion(1.0, 0.5, 0.2, 0.0),
        )

        val metrics = RepMetricsCalculator.compute(concentricRep(), samples, null)

        assertEquals(metrics.mcvMps, metrics.mpvMps!!, 1e-6)
    }

    @Test
    fun `mpv stops at first negative acceleration`() {
        val samples = listOf(
            motion(0.0, 0.0, 0.2, 0.0),
            motion(0.5, 0.2, 0.8, 0.0),
            motion(1.0, 0.4, 0.5, -0.2),
            motion(1.5, 1.0, 0.3, -0.1),
        )

        val metrics = RepMetricsCalculator.compute(concentricRep(end = 1.5), samples, null)

        assertEquals(2.0 / 3.0, metrics.mcvMps, 1e-6)
        assertEquals(0.4, metrics.mpvMps!!, 1e-6)
        assertEquals(1.0, metrics.romTotalM, 1e-6)
        assertEquals(2.0 / 3.0, metrics.meanVelocityMps, 1e-6)
        assertNull(metrics.velocityLossPct)
    }

    @Test
    fun `sticking point is first local velocity minimum after peak`() {
        val samples = listOf(
            motion(0.0, 0.0, 0.2),
            motion(0.25, 0.2, 0.8),
            motion(0.5, 0.3, 0.3),
            motion(0.75, 0.45, 0.5),
            motion(1.0, 0.5, 0.2),
        )

        val metrics = RepMetricsCalculator.compute(concentricRep(), samples, null)

        assertEquals(60.0, metrics.stickingPointPct!!, 1e-6)
    }

    @Test
    fun `load integrals use actual unequal sample intervals`() {
        val samples = listOf(
            motion(0.0, 0.0, 2.0, 0.0),
            motion(0.5, 1.0, 2.0, 2.0),
            motion(2.0, 3.0, 2.0, 0.0),
        )

        val metrics = RepMetricsCalculator.compute(concentricRep(end = 2.0), samples, loadKg = 2.0)

        assertEquals(43.2266, metrics.impulseNs!!, 1e-4)
        assertEquals(64.8399, metrics.workJ!!, 1e-4)
    }

    @Test
    fun `duplicate timestamps in rep are rejected`() {
        val samples = listOf(
            motion(0.0, 0.0, 0.5),
            motion(0.5, 0.25, 0.5),
            motion(0.5, 0.3, 0.5),
            motion(1.0, 0.5, 0.5),
        )

        assertThrows(IllegalArgumentException::class.java) {
            RepMetricsCalculator.compute(concentricRep(), samples, null)
        }
    }

    @Test
    fun `non monotonic concentric path uses endpoints and carries quality warning`() {
        val samples = listOf(
            motion(0.0, 0.0, 0.5),
            motion(0.5, 0.4, 0.5),
            motion(1.0, 0.3, 0.5),
        )

        val metrics = RepMetricsCalculator.compute(concentricRep(), samples, null)

        assertEquals(0.3, metrics.romConcentricM, 1e-9)
        assertEquals(listOf(RepMetricsCalculator.NON_MONOTONIC_CONCENTRIC), metrics.qualityWarnings)
    }

    @Test
    fun `peaks use independent lower smoothing series`() {
        val mean = (0..4).map { motion(it * 0.25, it * 0.125, 0.5, ay = 1.0) }
        val peak = (0..4).map { motion(it * 0.25, it * 0.125, 0.9, ay = 3.0) }

        val metrics = RepMetricsCalculator.compute(concentricRep(), mean, 100.0, peakSamples = peak)

        assertEquals(0.9, metrics.peakVelocityMps!!, 1e-9)
        assertEquals(3.0, metrics.peakAccelerationMps2!!, 1e-9)
        assertEquals(100.0 * (9.80665 + 1.0), metrics.meanForceN!!, 1e-6)
        assertEquals(100.0 * (9.80665 + 3.0), metrics.peakForceN!!, 1e-6)
    }

    @Test
    fun `missing second derivative yields null not zero`() {
        val samples = listOf(
            motion(0.0, 0.0, 0.5, ay = null),
            motion(1.0, 0.5, 0.5, ay = null),
        )

        val metrics = RepMetricsCalculator.compute(concentricRep(), samples, 100.0)

        assertNull(metrics.mpvMps)
        assertNull(metrics.peakAccelerationMps2)
        assertNull(metrics.peakDecelerationMps2)
        assertNull(metrics.meanForceN)
        assertEquals(listOf(RepMetricsCalculator.ACCELERATION_UNAVAILABLE), metrics.unavailableReasons)
    }

    @Test
    fun `invalid or absent load leaves all load metrics null`() {
        val samples = listOf(motion(0.0, 0.0, 0.5), motion(1.0, 0.5, 0.5))

        for (load in listOf(null, 0.0, -20.0)) {
            val metrics = RepMetricsCalculator.compute(concentricRep(), samples, load)
            assertNull(metrics.meanForceN)
            assertNull(metrics.peakForceN)
            assertNull(metrics.meanPowerW)
            assertNull(metrics.peakPowerW)
            assertNull(metrics.impulseNs)
            assertNull(metrics.workJ)
        }
    }
}
