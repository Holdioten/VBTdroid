package com.vbtdroid.app.analysis

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import com.vbtdroid.app.video.Frame
import com.vbtdroid.app.video.VideoDecoder
import com.vbtdroid.app.video.VideoMeta
import com.vbtdroid.app.vision.PlateTracker
import com.vbtdroid.app.vision.PoseEstimator
import com.vbtdroid.core.model.Lift
import com.vbtdroid.core.model.PlateObservation
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
class AnalysisPipelineTest {
    @Test
    fun `pipeline returns one rep for synthetic frames`() = runTest {
        val decoder = FakeDecoder()
        val pipeline = AnalysisPipeline(decoder, FakeTracker(), FakePose())

        val result = pipeline.analyze(
            AnalysisRequest(Uri.EMPTY, Lift.SQUAT, 90.0, 0.45, 100.0),
        )

        assertEquals(1, result.reps.size)
        assertTrue(result.reps[0].mcvMps > 0.0)
        assertEquals(1.0, result.quality.trackConfidence, 1e-6)
        assertEquals(1, result.summary.repCount)
        assertTrue(result.motionSamples.isNotEmpty())
        assertTrue(decoder.bitmaps.all(Bitmap::isRecycled))
    }

    @Test
    fun `camera warning and missing pose do not block bar metrics`() = runTest {
        val pipeline = AnalysisPipeline(FakeDecoder(), FakeTracker(), FakePose())

        val result = pipeline.analyze(
            AnalysisRequest(
                uri = Uri.EMPTY,
                lift = Lift.SQUAT,
                cameraAngleDeg = 90.0,
                plateDiameterM = 0.45,
                loadKg = null,
                cameraLevel = false,
            ),
        )

        assertEquals(1, result.reps.size)
        assertTrue(result.quality.warnings.contains("CAMERA_NOT_LEVEL"))
        assertTrue(result.quality.warnings.contains("POSE_UNAVAILABLE"))
        assertEquals(null, result.summary.totalVolumeKg)
    }

    @Test
    fun `missing ROI and detector fails clearly and recycles first frame`() = runTest {
        val decoder = FakeDecoder(frameCount = 1)
        val pipeline = AnalysisPipeline(decoder, FakeTracker(provideRoi = false), FakePose())

        try {
            pipeline.analyze(AnalysisRequest(Uri.EMPTY, Lift.SQUAT, 90.0, 0.45, null))
            fail("Expected initialization failure")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("ROI"))
        }
        assertTrue(decoder.bitmaps.all(Bitmap::isRecycled))
    }

    private class FakeDecoder(frameCount: Int = syntheticY.size) : VideoDecoder(
        RuntimeEnvironment.getApplication(),
    ) {
        val bitmaps = mutableListOf<Bitmap>()
        private val frames = (0 until frameCount).map { index ->
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also { bitmaps += it }.let { bitmap ->
                Frame(index / FPS, bitmap)
            }
        }

        override fun readMeta(uri: Uri) = VideoMeta(
            durationSec = frames.size / FPS,
            width = 32,
            height = 32,
            rotationDeg = 0,
            nominalFps = FPS,
            effectiveFps = FPS,
        )

        override fun decode(uri: Uri, maxLongSide: Int) = frames.asFlow()
    }

    private class FakeTracker(private val provideRoi: Boolean = true) : PlateTracker() {
        override fun initialRoi(bitmap: Bitmap): RectF? =
            if (provideRoi) RectF(0f, 0f, 32f, 32f) else null

        override fun initialize(bitmap: Bitmap, roi: RectF, tSec: Double): PlateObservation =
            observationAt(tSec)

        override fun update(bitmap: Bitmap, tSec: Double): PlateObservation = observationAt(tSec)

        private fun observationAt(tSec: Double): PlateObservation {
            val index = (tSec * FPS).roundToInt().coerceIn(0, syntheticY.lastIndex)
            return PlateObservation(
                tSec = tSec,
                xPx = 16.0,
                yPx = syntheticY[index],
                majorAxisPx = 45.0,
                confidence = 1.0,
            )
        }
    }

    private class FakePose : PoseEstimator {
        override fun estimate(bitmap: Bitmap, tSec: Double) = null
    }

    private companion object {
        const val FPS = 10.0

        // Image y grows downwards: descend, pause, ascend, then lock out.
        val syntheticY = listOf(
            100.0, 102.0, 106.0, 112.0, 120.0, 130.0, 142.0,
            142.0, 142.0, 142.0,
            138.0, 132.0, 124.0, 114.0, 102.0, 88.0,
            88.0, 88.0, 88.0, 88.0, 88.0, 88.0, 88.0, 88.0,
        )
    }
}
