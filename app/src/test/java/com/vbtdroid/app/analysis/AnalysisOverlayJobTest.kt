package com.vbtdroid.app.analysis

import com.vbtdroid.core.quality.QualityFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalysisOverlayJobTest {
    @Test
    fun `analysis progress fills the first three quarters`() {
        assertEquals(0f, AnalysisOverlayJob.analysisProgress(0f), 1e-6f)
        assertEquals(0.375f, AnalysisOverlayJob.analysisProgress(0.5f), 1e-6f)
        assertEquals(0.75f, AnalysisOverlayJob.analysisProgress(1f), 1e-6f)
        assertEquals(0.75f, AnalysisOverlayJob.analysisProgress(4f), 1e-6f)
        assertEquals(0f, AnalysisOverlayJob.analysisProgress(-1f), 1e-6f)
    }

    @Test
    fun `overlay progress fills the last quarter`() {
        assertEquals(0.75f, AnalysisOverlayJob.overlayProgress(0f), 1e-6f)
        assertEquals(0.875f, AnalysisOverlayJob.overlayProgress(0.5f), 1e-6f)
        assertEquals(1f, AnalysisOverlayJob.overlayProgress(1f), 1e-6f)
        assertEquals(1f, AnalysisOverlayJob.overlayProgress(3f), 1e-6f)
        assertEquals(0.75f, AnalysisOverlayJob.overlayProgress(-1f), 1e-6f)
    }

    @Test
    fun `progress is continuous and monotonic across the handoff`() {
        assertEquals(
            AnalysisOverlayJob.analysisProgress(1f),
            AnalysisOverlayJob.overlayProgress(0f),
            1e-6f,
        )
        var previous = 0f
        for (step in 0..20) {
            val value = AnalysisOverlayJob.overlayProgress(step / 20f)
            assertEquals(true, value >= previous)
            previous = value
        }
        assertEquals(1f, previous, 1e-6f)
    }

    @Test
    fun `cache file name uses createdAt millis`() {
        assertEquals("overlay-1700000000000.mp4", AnalysisOverlayJob.overlayFileName(1_700_000_000_000L))
    }

    @Test
    fun `stale files keep the newest entries`() {
        val names = listOf("overlay-4.mp4", "overlay-3.mp4", "overlay-2.mp4", "overlay-1.mp4")
        assertEquals(
            listOf("overlay-1.mp4"),
            AnalysisOverlayJob.staleOverlayFiles(names, keep = 3),
        )
        assertEquals(emptyList<String>(), AnalysisOverlayJob.staleOverlayFiles(names, keep = 4))
        assertEquals(emptyList<String>(), AnalysisOverlayJob.staleOverlayFiles(emptyList(), keep = 2))
    }

    @Test
    fun `cache cleanup always keeps at least one file`() {
        val names = listOf("overlay-2.mp4", "overlay-1.mp4")
        assertEquals(
            listOf("overlay-1.mp4"),
            AnalysisOverlayJob.staleOverlayFiles(names, keep = 0),
        )
    }

    @Test
    fun `analysis result copy preserves the overlay path`() {
        val result = AnalysisResult(
            reps = emptyList(),
            summary = SetSummary(0, null, null, null, null, null),
            quality = QualityFlags(
                trackConfidence = 1.0,
                occlusionPct = 0.0,
                repConfidence = 1.0,
                calibrationConfidence = 1.0,
                cameraLevel = true,
                warnings = emptyList(),
            ),
            fps = 30.0,
            overlayVideoPath = "/data/cache/overlay-42.mp4",
        )

        assertEquals("/data/cache/overlay-42.mp4", result.copy(savedSetId = "set").overlayVideoPath)
        assertNull(result.copy(overlayVideoPath = null).overlayVideoPath)
    }
}
