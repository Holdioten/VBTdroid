package com.vbtdroid.core.signal

import com.vbtdroid.core.model.PlateObservation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class TrackSmootherTest {
    private fun track(
        count: Int,
        yAt: (Int) -> Double = { 100.0 + it * 0.5 },
        axisAt: (Int) -> Double = { 260.0 },
        confidence: Double = 0.9,
    ): List<PlateObservation> = (0 until count).map { index ->
        PlateObservation(
            tSec = index / 30.0,
            xPx = 300.0,
            yPx = yAt(index),
            majorAxisPx = axisAt(index),
            confidence = confidence,
        )
    }

    @Test
    fun `single frame teleport is removed from the track`() {
        val clean = track(20)
        val spiked = clean.toMutableList().also {
            it[10] = it[10].copy(yPx = it[10].yPx + 600.0)
        }

        val smoothed = TrackSmoother.smooth(spiked)

        assertEquals(clean.size - 1, smoothed.size)
        assertTrue(smoothed.none { it.tSec == clean[10].tSec })
        // No remaining sample jumps by more than the innovation gate.
        smoothed.zipWithNext().forEach { (left, right) ->
            assertTrue(abs(right.yPx - left.yPx) < 260.0)
        }
    }

    @Test
    fun `radius outlier is dropped instead of precessing the calibration`() {
        val clean = track(20)
        val wrongObject = clean.toMutableList().also {
            it[5] = it[5].copy(majorAxisPx = 180.0)
            it[6] = it[6].copy(majorAxisPx = 300.0)
        }

        val smoothed = TrackSmoother.smooth(wrongObject)

        assertEquals(clean.size - 2, smoothed.size)
        assertTrue(smoothed.none { it.tSec == clean[5].tSec || it.tSec == clean[6].tSec })
        assertTrue(smoothed.all { abs(it.majorAxisPx - 260.0) < 1e-9 })
    }

    @Test
    fun `smooth linear track is preserved within subpixel tolerance`() {
        val clean = track(30)

        val smoothed = TrackSmoother.smooth(clean)

        assertEquals(clean.size, smoothed.size)
        smoothed.zip(clean).forEach { (actual, expected) ->
            assertEquals(expected.tSec, actual.tSec, 0.0)
            assertEquals(expected.confidence, actual.confidence, 0.0)
            assertEquals(expected.yPx, actual.yPx, 0.25)
            assertEquals(expected.xPx, actual.xPx, 0.25)
        }
    }

    @Test
    fun `camera scale change drops later frames but keeps early reps`() {
        val samples = track(40, axisAt = { index -> if (index < 8) 260.0 else 312.0 })

        val smoothed = TrackSmoother.smooth(samples)

        assertEquals(8, smoothed.size)
        assertTrue(smoothed.all { it.majorAxisPx == 260.0 })
    }

    @Test
    fun `short tracks pass through unchanged`() {
        val short = track(2).toMutableList().also {
            it[1] = it[1].copy(yPx = it[1].yPx + 300.0)
        }

        assertEquals(short, TrackSmoother.smooth(short))
        assertEquals(emptyList<PlateObservation>(), TrackSmoother.smooth(emptyList()))
    }

    @Test
    fun `non finite samples are discarded`() {
        val samples = track(10).toMutableList().also {
            it[3] = it[3].copy(yPx = Double.NaN)
        }

        val smoothed = TrackSmoother.smooth(samples)

        assertEquals(samples.size - 1, smoothed.size)
        assertTrue(smoothed.all { it.yPx.isFinite() })
    }
}
