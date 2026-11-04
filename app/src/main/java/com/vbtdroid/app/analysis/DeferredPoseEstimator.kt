package com.vbtdroid.app.analysis

import android.graphics.Bitmap
import com.vbtdroid.app.vision.PoseEstimator
import com.vbtdroid.app.vision.PoseFrame
import com.vbtdroid.core.metrics.Point2

/**
 * Defers creating the real pose estimator until the first [estimate] call.
 *
 * MediaPipe initialization is expensive. Keeping the provider lazy means an analysis run with
 * joint angles switched off never loads the pose model just because the pipeline was constructed.
 */
class DeferredPoseEstimator(private val provider: () -> PoseEstimator) : PoseEstimator {
    private val delegate = lazy(provider)

    override fun estimate(bitmap: Bitmap, tSec: Double, barCenter: Point2?): PoseFrame? =
        delegate.value.estimate(bitmap, tSec, barCenter)

    override fun reset() {
        if (delegate.isInitialized()) delegate.value.reset()
    }
}
