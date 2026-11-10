package com.vbtdroid.core.metrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StickingRegionTest {
    private fun samples(velocities: List<Double>, dyM: List<Double>? = null): List<MotionSample> {
        var y = 0.0
        return velocities.mapIndexed { index, velocity ->
            if (index > 0) y += dyM?.getOrNull(index) ?: 0.05
            MotionSample(
                tSec = index * 0.1,
                xM = 0.0,
                yM = y,
                vxMps = 0.0,
                vyMps = velocity,
                axMps2 = null,
                ayMps2 = null,
            )
        }
    }

    @Test
    fun `region starts at vpeak1 even when vpeak2 is higher`() {
        val region = StickingRegionDetector.detect(
            samples = samples(listOf(0.10, 0.30, 0.60, 0.40, 0.30, 0.55, 0.75, 0.50, 0.20)),
            startY = 0.0,
            romConcentric = 0.40,
        )

        assertNotNull(region)
        assertEquals(2, region!!.peakIndex)
        assertEquals(4, region.minIndex)
        assertEquals(0.60, region.peakVelocityMps, 1e-9)
        assertEquals(0.30, region.minVelocityMps, 1e-9)
        assertEquals(25.0, region.startPct, 1e-9)
        assertEquals(50.0, region.endPct, 1e-9)
    }

    @Test
    fun `monotonic velocity has no sticking region`() {
        val region = StickingRegionDetector.detect(
            samples = samples(listOf(0.10, 0.20, 0.30, 0.40, 0.50)),
            startY = 0.0,
            romConcentric = 0.20,
        )

        assertNull(region)
    }

    @Test
    fun `small drop below prominence is rejected`() {
        val region = StickingRegionDetector.detect(
            samples = samples(listOf(0.50, 0.60, 0.58, 0.62, 0.65)),
            startY = 0.0,
            romConcentric = 0.20,
        )

        assertNull(region)
    }

    @Test
    fun `flat minimum resolves to its last equal sample`() {
        val region = StickingRegionDetector.detect(
            samples = samples(listOf(0.10, 0.50, 0.20, 0.20, 0.20, 0.60)),
            startY = 0.0,
            romConcentric = 0.25,
        )

        assertNotNull(region)
        assertEquals(1, region!!.peakIndex)
        assertEquals(4, region.minIndex)
    }

    @Test
    fun `degenerate inputs return null`() {
        assertNull(
            StickingRegionDetector.detect(
                samples = samples(listOf(0.10, 0.50)),
                startY = 0.0,
                romConcentric = 0.05,
            )
        )
        assertNull(
            StickingRegionDetector.detect(
                samples = samples(listOf(0.10, 0.50, 0.20)),
                startY = 0.0,
                romConcentric = 0.0,
            )
        )
    }
}
