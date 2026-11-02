package com.vbtdroid.core.metrics

import com.vbtdroid.core.phases.Phase
import com.vbtdroid.core.phases.PhaseType
import com.vbtdroid.core.phases.RepSlice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RepMetricsTest {
    private fun motion(t: Double, y: Double, vy: Double, ay: Double = 0.0, x: Double = 0.0) =
        MotionSample(t, x, y, 0.0, vy, 0.0, ay)

    private fun concentricRep(start: Double = 0.0, end: Double = 1.0) =
        RepSlice(0, start, end, listOf(Phase(PhaseType.CONCENTRIC, start, end)))

    @Test
    fun `constant concentric velocity gives expected mcv and rom`() {
        val samples = (0..20).map { motion(it * 0.05, it * 0.05 * 0.5, 0.5) }

        val metrics = RepMetricsCalculator.compute(concentricRep(), samples, null)

        assertEquals(0.5, metrics.mcvMps, 1e-6)
        assertEquals(0.5, metrics.romConcentricM, 1e-6)
        assertEquals(0.5, metrics.peakVelocityMps, 1e-6)
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

        assertEquals(metrics.mcvMps, metrics.mpvMps, 1e-6)
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
