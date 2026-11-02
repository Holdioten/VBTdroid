package com.vbtdroid.core.video

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TimelineTest {
    @Test
    fun `normalizes variable frame timestamps`() {
        val t = Timeline.normalizePts(longArrayOf(0, 33_333, 66_666, 150_000))
        assertArrayEquals(doubleArrayOf(0.0, 0.033333, 0.066666, 0.15), t, 1e-6)
    }

    @Test
    fun `rejects non monotonic timestamps`() {
        assertThrows(IllegalArgumentException::class.java) {
            Timeline.normalizePts(longArrayOf(0, 100, 50))
        }
    }
}
