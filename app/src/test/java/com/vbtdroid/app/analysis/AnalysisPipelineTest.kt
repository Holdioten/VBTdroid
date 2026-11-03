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
import com.vbtdroid.core.metrics.MotionSample
import com.vbtdroid.core.phases.Phase
import com.vbtdroid.core.phases.PhaseType
import com.vbtdroid.core.phases.RepSlice
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
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

    @Test
    fun `empty summary uses null velocity averages instead of zero`() {
        val summary = summarize(emptyList(), 100.0)

        assertNull(summary.mcvAvgMps)
        assertNull(summary.bestMcvMps)
    }

    @Test
    fun `explicit ROI wins over detector and progress finishes at one`() = runTest {
        val tracker = FakeTracker()
        val progress = mutableListOf<Float>()
        val explicitRoi = RectF(1f, 2f, 21f, 22f)
        val pipeline = AnalysisPipeline(FakeDecoder(), tracker, FakePose())

        pipeline.analyze(
            AnalysisRequest(Uri.EMPTY, Lift.SQUAT, 90.0, 0.45, 100.0, roi = explicitRoi),
            onProgress = progress::add,
        )

        assertEquals(0, tracker.initialRoiCalls)
        assertEquals(explicitRoi, tracker.initializedRoi)
        assertEquals(1f, progress.last())
    }

    @Test
    fun `R7 invalid rep is skipped with warning`() {
        val samples = listOf(
            motion(0.0, 0.0),
            motion(1.0, 0.5),
            motion(2.0, 1.5),
            motion(3.0, 2.0),
            motion(4.0, 3.0),
            motion(5.0, 4.0),
        )
        val valid = RepSlice(
            index = 1,
            startSec = 0.0,
            endSec = 2.0,
            phases = listOf(Phase(PhaseType.CONCENTRIC, 0.0, 2.0)),
        )
        val invalid = RepSlice(
            index = 2,
            startSec = 3.0,
            endSec = 5.0,
            phases = listOf(Phase(PhaseType.CONCENTRIC, 3.0, 3.5)),
        )

        val computation = computeValidReps(listOf(valid, invalid), samples, null)

        assertEquals(listOf(1), computation.reps.map { it.repIndex })
        assertEquals(listOf(1), computation.validSlices.map { it.index })
        assertEquals(listOf("REP_SKIPPED_2"), computation.warnings)
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
        var initialRoiCalls = 0
            private set
        var initializedRoi: RectF? = null
            private set

        override fun initialRoi(bitmap: Bitmap): RectF? {
            initialRoiCalls++
            return if (provideRoi) RectF(0f, 0f, 32f, 32f) else null
        }

        override fun initialize(bitmap: Bitmap, roi: RectF, tSec: Double): PlateObservation {
            initializedRoi = RectF(roi)
            return observationAt(tSec)
        }

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

        fun motion(tSec: Double, yM: Double) = MotionSample(
            tSec = tSec,
            xM = 0.0,
            yM = yM,
            vxMps = 0.0,
            vyMps = 0.5,
            axMps2 = 0.0,
            ayMps2 = 0.0,
        )

        // Image y grows downwards: descend, pause, ascend, then lock out.
        val syntheticY = listOf(
            100.0, 102.0, 106.0, 112.0, 120.0, 130.0, 142.0,
            142.0, 142.0, 142.0,
            138.0, 132.0, 124.0, 114.0, 102.0, 88.0,
            88.0, 88.0, 88.0, 88.0, 88.0, 88.0, 88.0, 88.0,
        )
    }
}
