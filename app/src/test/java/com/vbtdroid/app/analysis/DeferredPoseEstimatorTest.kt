package com.vbtdroid.app.analysis

import android.graphics.Bitmap
import com.vbtdroid.app.vision.PoseEstimator
import com.vbtdroid.app.vision.PoseFrame
import com.vbtdroid.core.metrics.Point2
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeferredPoseEstimatorTest {
    @Test
    fun `reset does not initialize the provider`() {
        var created = 0
        val deferred = DeferredPoseEstimator {
            created++
            FakePose()
        }

        deferred.reset()

        assertEquals(0, created)
    }

    @Test
    fun `first estimate initializes the provider once`() {
        var created = 0
        val deferred = DeferredPoseEstimator {
            created++
            FakePose()
        }
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

        deferred.estimate(bitmap, 0.0)
        deferred.estimate(bitmap, 1.0)

        assertEquals(1, created)
    }

    @Test
    fun `bar center is forwarded to the delegate`() {
        val seen = mutableListOf<Point2?>()
        val deferred = DeferredPoseEstimator { RecordingPose(seen) }
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val hint = Point2(3.0, 4.0)

        deferred.estimate(bitmap, 0.0, hint)
        deferred.estimate(bitmap, 1.0)

        assertEquals(listOf(hint, null), seen)
    }

    private class FakePose : PoseEstimator {
        override fun estimate(bitmap: Bitmap, tSec: Double, barCenter: Point2?): PoseFrame? = null
    }

    private class RecordingPose(private val seen: MutableList<Point2?>) : PoseEstimator {
        override fun estimate(bitmap: Bitmap, tSec: Double, barCenter: Point2?): PoseFrame? {
            seen += barCenter
            return null
        }
    }
}
