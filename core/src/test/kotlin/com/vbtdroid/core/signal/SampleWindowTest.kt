package com.vbtdroid.core.signal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SampleWindowTest {
    @Test
    fun `thirty and sixty fps get the same smoothing time`() {
        val at30 = DoubleArray(61) { it / 30.0 }
        val at60 = DoubleArray(121) { it / 60.0 }

        assertEquals(9, SampleWindow.samplesFor(at30, 0.30))
        assertEquals(19, SampleWindow.samplesFor(at60, 0.30))
    }

    @Test
    fun `window is odd and clamped to the input`() {
        val times = DoubleArray(4) { it * 0.1 }

        assertEquals(3, SampleWindow.samplesFor(times, 0.05))
        assertEquals(4, SampleWindow.samplesFor(times, 10.0))
    }

    @Test
    fun `invalid input falls back to the minimum`() {
        assertEquals(3, SampleWindow.samplesFor(DoubleArray(0), 0.3))
        assertEquals(3, SampleWindow.samplesFor(DoubleArray(10) { it * 0.0 }, 0.3))
    }
}
