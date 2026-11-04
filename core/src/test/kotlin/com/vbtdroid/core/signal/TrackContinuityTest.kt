package com.vbtdroid.core.signal

import com.vbtdroid.core.model.PlateObservation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TrackContinuityTest {
    private fun track(
        count: Int,
        axisAt: (Int) -> Double = { 260.0 },
        startSec: Double = 0.0,
    ): List<PlateObservation> = (0 until count).map { index ->
        PlateObservation(
            tSec = startSec + index / 30.0,
            xPx = 300.0,
            yPx = 600.0,
            majorAxisPx = axisAt(index),
            confidence = 0.9,
        )
    }

    @Test
    fun `stable track has no break`() {
        assertNull(TrackContinuity.detect(track(60)))
    }

    @Test
    fun `plate scale change is reported at the last good frame`() {
        // 20 good frames, then the camera moves closer and the plate looks 20% larger.
        val samples = track(50, axisAt = { index -> if (index < 20) 260.0 else 312.0 })

        val result = TrackContinuity.detect(samples)

        assertEquals(TrackBreakReason.SCALE_CHANGE, result?.reason)
        assertEquals(samples[19].tSec, result?.fromSec!!, 1e-9)
    }

    @Test
    fun `single bad radius is not a camera move`() {
        val samples = track(40, axisAt = { index -> if (index == 12) 320.0 else 260.0 })

        assertNull(TrackContinuity.detect(samples))
    }

    @Test
    fun `track ending long before the clip is reported`() {
        val samples = track(40)

        val result = TrackContinuity.detect(samples, durationSec = 20.0)

        assertEquals(TrackBreakReason.TRACK_ENDED, result?.reason)
        assertEquals(samples.last().tSec, result?.fromSec!!, 1e-9)
    }

    @Test
    fun `track that spans the clip is not reported`() {
        val samples = track(40)

        assertNull(TrackContinuity.detect(samples, durationSec = samples.last().tSec + 1.0))
    }

    @Test
    fun `reference axis uses the first observations`() {
        val samples = track(30, axisAt = { index -> if (index < 10) 260.0 else 300.0 })

        assertEquals(260.0, TrackContinuity.referenceAxisPx(samples), 1e-9)
    }
}
