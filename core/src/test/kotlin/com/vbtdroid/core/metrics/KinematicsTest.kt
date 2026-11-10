package com.vbtdroid.core.metrics

import com.vbtdroid.core.model.BarSample
import com.vbtdroid.core.signal.SavitzkyGolay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

class KinematicsTest {
    @Test
    fun `constant position gives zero velocity`() {
        val samples = (0..20).map { BarSample(it * 0.016, 0.0, 0.5) }
        val out = Kinematics.derive(samples)
        assertTrue(out.all { abs(it.vyMps) < 1e-6 })
    }

    @Test
    fun `linear position gives constant velocity`() {
        val samples = (0..20).map { BarSample(it * 0.05, 0.0, 0.5 * it * 0.05) }
        val out = Kinematics.derive(samples)
        assertTrue(out.drop(3).dropLast(3).all { abs(it.vyMps - 0.5) < 1e-3 })
    }

    @Test
    fun `quadratic position gives constant acceleration`() {
        val samples = (0..20).map { BarSample(it * 0.05, 0.0, 0.5 * (it * 0.05) * (it * 0.05)) }
        val out = Kinematics.derive(samples)
        assertTrue(out.drop(3).dropLast(3).all { abs(it.ayMps2!! - 1.0) < 0.05 })
    }

    @Test
    fun `linear position uses actual nonuniform timestamps`() {
        val times = listOf(0.0, 0.03, 0.11, 0.2, 0.36, 0.5, 0.73, 0.9)
        val samples = times.map { BarSample(it, 2.0 * it, -0.5 * it) }

        val out = Kinematics.derive(samples)

        assertTrue(out.all { abs(it.vxMps - 2.0) < 1e-6 })
        assertTrue(out.all { abs(it.vyMps + 0.5) < 1e-6 })
    }

    @Test
    fun `short input derives available values without crashing`() {
        val samples = listOf(BarSample(0.0, 0.0, 0.0), BarSample(1.0, 1.0, 2.0))

        val out = Kinematics.derive(samples)

        assertEquals(2, out.size)
        assertTrue(out.all { abs(it.vxMps - 1.0) < 1e-9 && abs(it.vyMps - 2.0) < 1e-9 })
        assertTrue(out.all { it.ayMps2 == null })
    }

    @Test
    fun `single sample acceleration is unavailable`() {
        val out = Kinematics.derive(listOf(BarSample(0.0, 0.0, 0.0))).single()

        assertNull(out.ayMps2)
    }

    @Test
    fun `time based window keeps 30 and 60 fps velocities comparable`() {
        fun samples(fps: Int): List<BarSample> {
            val dt = 1.0 / fps
            return (0..(2 * fps)).map { index ->
                val t = index * dt
                BarSample(t, 0.0, 0.3 * kotlin.math.sin(2.0 * PI * 1.5 * t))
            }
        }
        val fast = Kinematics.deriveByTime(samples(60), windowSec = 0.30)
        val slow = Kinematics.deriveByTime(samples(30), windowSec = 0.30)
        val slowByTime = slow.associateBy { kotlin.math.round(it.tSec * 1000.0).toLong() }

        val compared = fast.drop(20).dropLast(20).mapNotNull { sample ->
            slowByTime[kotlin.math.round(sample.tSec * 1000.0).toLong()]?.let { sample.vyMps - it.vyMps }
        }
        assertTrue(compared.size > 30)
        assertTrue(compared.all { abs(it) < 0.08 }, "max difference ${compared.maxOf { abs(it) }}")
    }

    @Test
    fun `Savitzky Golay smoothing preserves a constant signal`() {
        val smoothed = SavitzkyGolay.smooth(doubleArrayOf(3.0, 3.0, 3.0, 3.0))

        assertTrue(smoothed.all { abs(it - 3.0) < 1e-9 })
    }
}
