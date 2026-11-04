package com.vbtdroid.app.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.vbtdroid.core.metrics.Point2

class MediaPipePoseEstimator(context: Context) : PoseEstimator, AutoCloseable {
    private val landmarker: PoseLandmarker
    private var lastTimestampMs = -1L

    init {
        try {
            context.assets.open(MODEL_ASSET).use { }
        } catch (error: Exception) {
            throw IllegalStateException(
                "Pose model missing: add app/src/main/assets/$MODEL_ASSET. See assets/models/README.md.",
                error,
            )
        }

        landmarker = try {
            createLandmarker(context, Delegate.GPU)
        } catch (_: Exception) {
            createLandmarker(context, Delegate.CPU)
        }
    }

    @Synchronized
    override fun estimate(bitmap: Bitmap, tSec: Double, barCenter: Point2?): PoseFrame? {
        require(tSec.isFinite() && tSec >= 0.0) { "Video timestamp must be finite and non-negative" }
        val requestedTimestampMs = (tSec * 1000.0).toLong()
        val timestampMs = maxOf(requestedTimestampMs, lastTimestampMs + 1L)
        lastTimestampMs = timestampMs

        val result = landmarker.detectForVideo(BitmapImageBuilder(bitmap).build(), timestampMs)
        val candidates = result.landmarks().map { pose ->
            val points = LinkedHashMap<Int, Point2>(pose.size)
            val visibility = LinkedHashMap<Int, Float>(pose.size)
            pose.forEachIndexed { index, landmark ->
                points[index] = Point2(
                    landmark.x().toDouble() * bitmap.width,
                    landmark.y().toDouble() * bitmap.height,
                )
                visibility[index] = landmark.visibility().orElse(0f)
            }
            PoseCandidate(points, visibility)
        }
        // A lifter's torso stays near the bar; background people fall outside this generous gate
        // (~2-3 plate diameters for typical framing). null barCenter keeps the largest skeleton.
        val maxBarDistancePx = maxOf(bitmap.width, bitmap.height) * MAX_BAR_DISTANCE_FRAME_FRACTION
        val chosen = PosePersonSelector.select(candidates, barCenter, maxBarDistancePx) ?: return null
        val pose = candidates[chosen]
        return PoseFrame(
            tSec = tSec,
            landmarks = pose.landmarks,
            visibility = pose.visibility,
            frameWidth = bitmap.width,
            frameHeight = bitmap.height,
            normalized = false,
        )
    }

    @Synchronized
    override fun reset() {
        lastTimestampMs = -1L
    }

    override fun close() {
        landmarker.close()
    }

    private fun createLandmarker(context: Context, delegate: Delegate): PoseLandmarker {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .setDelegate(delegate)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(MAX_POSES)
            .build()
        return PoseLandmarker.createFromOptions(context, options)
    }

    private companion object {
        const val MODEL_ASSET = "models/pose_landmarker_lite.task"
        const val MAX_POSES = 3
        const val MAX_BAR_DISTANCE_FRAME_FRACTION = 0.45
    }
}
