package com.vbtdroid.app.analysis

import com.vbtdroid.core.quality.QualityFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisStateHolderTest {
    @Test
    fun `start clears previous result and progress`() {
        val holder = AnalysisStateHolder()
        holder.start()
        holder.updateProgress(0.4f)
        holder.succeed(result("first"))

        holder.start()

        val state = holder.snapshot()
        assertTrue(state.running)
        assertEquals(0f, state.progress, 0f)
        assertNull(state.result)
        assertNull(state.error)
    }

    @Test
    fun `progress is clamped and ignored after completion`() {
        val holder = AnalysisStateHolder()
        holder.start()

        holder.updateProgress(-1f)
        assertEquals(0f, holder.snapshot().progress, 0f)

        holder.updateProgress(1.4f)
        assertEquals(1f, holder.snapshot().progress, 0f)

        val result = result("done")
        holder.succeed(result)
        holder.updateProgress(0.2f)

        val state = holder.snapshot()
        assertFalse(state.running)
        assertEquals(1f, state.progress, 0f)
        assertSame(result, state.result)
    }

    @Test
    fun `result is consumed once`() {
        val holder = AnalysisStateHolder()
        holder.start()
        val result = result("saved")
        holder.succeed(result)

        assertSame(result, holder.consumeResult())
        assertNull(holder.consumeResult())
        assertNull(holder.snapshot().result)
        assertEquals(1f, holder.snapshot().progress, 0f)
    }

    @Test
    fun `error is consumed once and keeps last progress`() {
        val holder = AnalysisStateHolder()
        holder.start()
        holder.updateProgress(0.6f)
        holder.fail("decoder failed")

        val state = holder.snapshot()
        assertFalse(state.running)
        assertEquals(0.6f, state.progress, 1e-6f)
        assertEquals("decoder failed", holder.consumeError())
        assertNull(holder.consumeError())
        assertNull(holder.snapshot().error)
    }

    @Test
    fun `cancel returns to idle and drops progress`() {
        val holder = AnalysisStateHolder()
        holder.start()
        holder.updateProgress(0.9f)

        holder.cancel()

        assertEquals(AnalysisState(), holder.snapshot())
        holder.updateProgress(0.5f)
        assertEquals(0f, holder.snapshot().progress, 0f)
    }

    private fun result(name: String): AnalysisResult = AnalysisResult(
        reps = emptyList(),
        summary = SetSummary(0, null, null, null, null, null),
        quality = QualityFlags(1.0, 0.0, 1.0, 1.0, true, emptyList()),
        fps = 30.0,
        savedSetId = name,
    )
}
