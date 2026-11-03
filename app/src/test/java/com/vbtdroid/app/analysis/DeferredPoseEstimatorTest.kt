package com.vbtdroid.app.analysis

import android.graphics.Bitmap
import com.vbtdroid.app.vision.PoseEstimator
import com.vbtdroid.app.vision.PoseFrame
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

    private class FakePose : PoseEstimator {
        override fun estimate(bitmap: Bitmap, tSec: Double): PoseFrame? = null
    }
}
