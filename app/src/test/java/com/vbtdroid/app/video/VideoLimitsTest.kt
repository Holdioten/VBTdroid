package com.vbtdroid.app.video

import org.junit.Assert.assertThrows
import org.junit.Test

class VideoLimitsTest {
    @Test
    fun `ten minutes is accepted and longer video is rejected`() {
        VideoLimits.requireSupportedDuration(600.0)
        assertThrows(IllegalArgumentException::class.java) {
            VideoLimits.requireSupportedDuration(600.001)
        }
    }
}
