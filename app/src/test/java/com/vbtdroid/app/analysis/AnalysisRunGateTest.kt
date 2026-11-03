package com.vbtdroid.app.analysis

import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisRunGateTest {
    @Test
    fun `active analysis rejects double start and accepts re-entry after clear`() {
        val gate = AnalysisRunGate()
        val first = Job()
        var created = 0

        assertSame(first, gate.tryStart { created++; first })
        assertTrue(gate.isRunning())
        assertNull(gate.tryStart { created++; Job() })
        assertEquals(1, created)

        gate.clear(first)
        val second = Job()
        assertSame(second, gate.tryStart { second })
        assertTrue(gate.isRunning())
    }

    @Test
    fun `stale completion does not clear a newer run`() {
        val gate = AnalysisRunGate()
        val first = Job()
        val second = Job()

        assertSame(first, gate.tryStart { first })
        gate.clear(first)
        assertSame(second, gate.tryStart { second })
        gate.clear(first)

        assertTrue(gate.isRunning())
        assertNull(gate.tryStart { Job() })
    }
}
