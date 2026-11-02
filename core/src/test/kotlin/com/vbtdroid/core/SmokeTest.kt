package com.vbtdroid.core

import com.vbtdroid.core.model.MetricId
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmokeTest {
    @Test
    fun `core test runtime starts and exposes metric catalog`() {
        assertTrue(MetricId.entries.isNotEmpty())
    }
}
