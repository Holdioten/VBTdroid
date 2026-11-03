package com.vbtdroid.app.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SuccessfulFramePairTest {
    @Test
    fun `continuous occlusion retains one successful image center pair until recovery`() {
        val released = mutableListOf<String>()
        val pair = SuccessfulFramePair<String> { released += it }
        pair.matched("frame-0", centerX = 10f, centerY = 20f, timeSec = 0.0)

        repeat(5) { index ->
            pair.failed()
            assertSame("frame-0", pair.frame)
            assertEquals(10f, pair.centerX)
            assertEquals(20f, pair.centerY)
            assertEquals(0.0, pair.timeSec!!, 1e-9)
        }

        pair.matched("frame-6", centerX = 16f, centerY = 28f, timeSec = 0.6)

        assertSame("frame-6", pair.frame)
        assertEquals(16f, pair.centerX)
        assertEquals(28f, pair.centerY)
        assertEquals(0.6, pair.timeSec!!, 1e-9)
        assertEquals(listOf("frame-0"), released)
        pair.clear()
        assertEquals(listOf("frame-0", "frame-6"), released)
    }
}
