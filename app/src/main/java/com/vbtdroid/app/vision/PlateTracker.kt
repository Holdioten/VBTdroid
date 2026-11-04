package com.vbtdroid.app.vision

import android.graphics.Bitmap
import android.graphics.RectF
import com.vbtdroid.core.model.PlateObservation
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.RotatedRect
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** 跟踪已初始化的杠铃片；神经网络检测仅用于初始化或丢失后的恢复。 */
open class PlateTracker(private val detector: PlateDetector? = null) : AutoCloseable {
    private val successfulPair = SuccessfulFramePair<Mat> { it.release() }
    private val centerX get() = successfulPair.centerX
    private val centerY get() = successfulPair.centerY
    private val lastTimeSec get() = successfulPair.timeSec
    private val previousGray get() = successfulPair.frame
    private var velocityX = 0f
    private var velocityY = 0f
    private var axisReference: PlateAxisReference? = null

    open fun initialRoi(bitmap: Bitmap): RectF? {
        val detection = detector?.let { PlateRoi.selectLargest(it.detect(bitmap)) } ?: return null
        return RectF(
            detection.cxPx - detection.widthPx / 2f,
            detection.cyPx - detection.heightPx / 2f,
            detection.cxPx + detection.widthPx / 2f,
            detection.cyPx + detection.heightPx / 2f,
        )
    }

    open fun reset() {
        successfulPair.clear()
        velocityX = 0f
        velocityY = 0f
        axisReference = null
    }

    override fun close() = reset()

    open fun initialize(bitmap: Bitmap, roi: RectF, tSec: Double): PlateObservation {
        ensureOpenCv()
        require(!roi.isEmpty && roi.width() >= 5f && roi.height() >= 5f) { "Plate ROI is too small" }
        val gray = toGray(bitmap)
        try {
            val fitted = findEllipse(gray, roi, null, null, null)
                ?: throw IllegalArgumentException("Could not fit a plate ellipse inside the selected ROI")
            velocityX = 0f
            velocityY = 0f
            axisReference = PlateAxisReference(fitted.majorAxis)
            commitSuccessfulFrame(gray, fitted.ellipse.center.x.toFloat(), fitted.ellipse.center.y.toFloat(), tSec)
            return observation(tSec, fitted)
        } finally {
            gray.release()
        }
    }

    /**
     * 使用 PyrLK 光流预测中心，再在局部搜索区（整块杠铃片 + 余量）内先做霍夫圆定位，
     * 用圆心作为椭圆拟合的种子；椭圆失败时退回霍夫圆结果。
     */
    open fun update(bitmap: Bitmap, tSec: Double): PlateObservation? {
        ensureOpenCv()
        val reference = axisReference ?: return null
        val currentGray = toGray(bitmap)
        try {
            val dt = (tSec - (lastTimeSec ?: tSec)).coerceAtLeast(0.0).toFloat()
            val velocityPredictionX = centerX + velocityX * dt
            val velocityPredictionY = centerY + velocityY * dt
            val opticalPrediction = opticalFlowCenter(currentGray)
            val predictedX = opticalPrediction?.x?.toFloat() ?: velocityPredictionX
            val predictedY = opticalPrediction?.y?.toFloat() ?: velocityPredictionY

            // Full plate diameter plus margin; a radius below the diameter only exposes an arc.
            val radius = PlateRoi.localSearchRadiusPx(reference.majorAxisPx)
            val circle = findHoughCircle(
                currentGray,
                RectF(predictedX - radius, predictedY - radius, predictedX + radius, predictedY + radius),
                reference.majorAxisPx,
                predictedX,
                predictedY,
                dt,
            )
            val seedX = circle?.centerX ?: predictedX
            val seedY = circle?.centerY ?: predictedY
            val roi = RectF(seedX - radius, seedY - radius, seedX + radius, seedY + radius)
            val fitted = findEllipse(currentGray, roi, reference.majorAxisPx, predictedX, predictedY)
                ?.takeIf { it.confidence >= MIN_TRACK_CONFIDENCE }
                ?.takeIf { fit ->
                    withinInnovationGate(
                        fit.ellipse.center.x.toFloat(),
                        fit.ellipse.center.y.toFloat(),
                        predictedX,
                        predictedY,
                        reference,
                        dt,
                    )
                }
            if (fitted != null) {
                updatePosition(fitted.ellipse.center.x.toFloat(), fitted.ellipse.center.y.toFloat(), tSec, currentGray)
                return observation(tSec, fitted)
            }

            // Ellipse fit needs a clean contour; the Hough circle still localizes a partially
            // occluded or blurred plate. Reference axis is required before this path runs.
            if (circle != null) {
                updatePosition(circle.centerX, circle.centerY, tSec, currentGray)
                return PlateObservation(
                    tSec = tSec,
                    xPx = circle.centerX.toDouble(),
                    yPx = circle.centerY.toDouble(),
                    majorAxisPx = reference.majorAxisPx.toDouble(),
                    confidence = circle.confidence.toDouble(),
                )
            }

            // 检测器只在跟踪丢失时调用，用于恢复而非逐帧主信号。
            val detection = detector?.let { PlateRoi.selectLargest(it.detect(bitmap)) }
            if (detection != null) {
                val detectedDiameter = max(detection.widthPx, detection.heightPx)
                val box = RectF(
                    detection.cxPx - detection.widthPx / 2f,
                    detection.cyPx - detection.heightPx / 2f,
                    detection.cxPx + detection.widthPx / 2f,
                    detection.cyPx + detection.heightPx / 2f,
                )
                val recovered = findEllipse(
                    currentGray,
                    box,
                    reference.majorAxisPx,
                    detection.cxPx,
                    detection.cyPx,
                )?.takeIf { it.confidence >= MIN_TRACK_CONFIDENCE }
                if (recovered != null) {
                    updatePosition(recovered.ellipse.center.x.toFloat(), recovered.ellipse.center.y.toFloat(), tSec, currentGray)
                    return observation(tSec, recovered)
                }
                if (reference.accepts(detectedDiameter)) {
                    updatePosition(detection.cxPx, detection.cyPx, tSec, currentGray)
                    return PlateObservation(
                        tSec = tSec,
                        xPx = centerX.toDouble(),
                        yPx = centerY.toDouble(),
                        majorAxisPx = reference.majorAxisPx.toDouble(),
                        confidence = detection.confidence.toDouble(),
                    )
                }
            }
            // A failed frame is never paired with the last successful center.
            successfulPair.failed()
            return null
        } finally {
            currentGray.release()
        }
    }

    private fun opticalFlowCenter(currentGray: Mat): Point? {
        val oldGray = previousGray ?: return null
        if (oldGray.empty() || oldGray.rows() != currentGray.rows() || oldGray.cols() != currentGray.cols()) return null

        val previousPoint = MatOfPoint2f(Point(centerX.toDouble(), centerY.toDouble()))
        val nextPoint = MatOfPoint2f()
        val status = MatOfByte()
        val error = MatOfFloat()
        try {
            Video.calcOpticalFlowPyrLK(oldGray, currentGray, previousPoint, nextPoint, status, error)
            val tracked = nextPoint.toArray().firstOrNull() ?: return null
            if (status.toArray().firstOrNull()?.toInt() != 1) return null
            if (!tracked.x.isFinite() || !tracked.y.isFinite()) return null
            if (tracked.x !in 0.0..(currentGray.cols() - 1).toDouble()) return null
            if (tracked.y !in 0.0..(currentGray.rows() - 1).toDouble()) return null
            return tracked
        } catch (_: RuntimeException) {
            return null
        } finally {
            previousPoint.release()
            nextPoint.release()
            status.release()
            error.release()
        }
    }

    private fun observation(tSec: Double, fit: EllipseFit): PlateObservation =
        PlateObservation(
            tSec = tSec,
            xPx = fit.ellipse.center.x,
            yPx = fit.ellipse.center.y,
            majorAxisPx = fit.majorAxis.toDouble(),
            confidence = fit.confidence.toDouble(),
        )

    private fun updatePosition(nextX: Float, nextY: Float, tSec: Double, currentGray: Mat) {
        val previousTime = lastTimeSec
        if (previousTime != null && tSec > previousTime) {
            val elapsed = (tSec - previousTime).toFloat()
            velocityX = (nextX - centerX) / elapsed
            velocityY = (nextY - centerY) / elapsed
        }
        commitSuccessfulFrame(currentGray, nextX, nextY, tSec)
    }

    private fun commitSuccessfulFrame(gray: Mat, x: Float, y: Float, tSec: Double) {
        successfulPair.matched(gray.clone(), x, y, tSec)
    }

    private fun toGray(bitmap: Bitmap): Mat {
        val rgba = Mat()
        val gray = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            return gray
        } catch (error: Throwable) {
            gray.release()
            throw error
        } finally {
            rgba.release()
        }
    }

    /**
     * 在局部 ROI 内检测圆，作为椭圆拟合的种子和失败回退。
     *
     * 半径相对于参考直径（直径/2）做 ±15% 校验，超出即视为别的圆（内毂、旁边的片）；圆心离
     * 预测点过远也拒绝，避免整帧跳到另一个物体。
     */
    private fun findHoughCircle(
        gray: Mat,
        requestedRoi: RectF,
        expectedAxisPx: Float,
        predictedX: Float,
        predictedY: Float,
        dtSec: Float,
    ): HoughCircle? {
        val left = requestedRoi.left.toInt().coerceIn(0, gray.cols() - 1)
        val top = requestedRoi.top.toInt().coerceIn(0, gray.rows() - 1)
        val right = requestedRoi.right.toInt().coerceIn(left + 1, gray.cols())
        val bottom = requestedRoi.bottom.toInt().coerceIn(top + 1, gray.rows())
        if (right - left < 5 || bottom - top < 5) return null

        val expectedRadius = expectedAxisPx / 2f
        if (!expectedRadius.isFinite() || expectedRadius <= 1f) return null
        val minRadius = (expectedRadius * HOUGH_MIN_RADIUS_FACTOR).toInt().coerceAtLeast(1)
        val maxRadius = (expectedRadius * HOUGH_MAX_RADIUS_FACTOR).toInt().coerceAtLeast(minRadius + 1)

        val crop = gray.submat(Rect(left, top, right - left, bottom - top)).clone()
        val blurred = Mat()
        val circles = Mat()
        try {
            Imgproc.GaussianBlur(crop, blurred, org.opencv.core.Size(5.0, 5.0), 1.5)
            Imgproc.HoughCircles(
                blurred,
                circles,
                Imgproc.HOUGH_GRADIENT,
                HOUGH_DP,
                max(8.0, expectedRadius.toDouble() * HOUGH_MIN_DIST_FACTOR),
                HOUGH_PARAM1,
                HOUGH_PARAM2,
                minRadius,
                maxRadius,
            )
            var best: HoughCircle? = null
            for (index in 0 until circles.cols()) {
                val values = circles.get(0, index) ?: continue
                if (values.size < 3) continue
                val centerX = (values[0] + left).toFloat()
                val centerY = (values[1] + top).toFloat()
                val radiusPx = values[2].toFloat()
                if (!centerX.isFinite() || !centerY.isFinite() || !radiusPx.isFinite()) continue
                if (radiusPx <= 0f) continue
                // Reject a circle whose radius moved too far from the reference plate.
                val radiusDeviation = abs(radiusPx - expectedRadius) / expectedRadius
                if (radiusDeviation > HOUGH_MAX_RADIUS_DEVIATION) continue
                val dx = centerX - predictedX
                val dy = centerY - predictedY
                val distance = sqrt(dx * dx + dy * dy)
                val maxJump = PlateRoi.maxInnovationPx(expectedAxisPx, dtSec.toDouble())
                if (maxJump <= 0f || distance > maxJump) continue
                val radiusScore = (1f - radiusDeviation / HOUGH_MAX_RADIUS_DEVIATION).coerceIn(0f, 1f)
                val distanceScore = (1f - distance / maxJump).coerceIn(0f, 1f)
                val confidence = (0.5f + 0.5f * radiusScore) * (0.5f + 0.5f * distanceScore)
                val candidate = HoughCircle(centerX, centerY, radiusPx, confidence, distance)
                if (best == null || candidate.confidence > best.confidence ||
                    (candidate.confidence == best.confidence && distance < best.distance)
                ) {
                    best = candidate
                }
            }
            return best
        } catch (_: RuntimeException) {
            return null
        } finally {
            crop.release()
            blurred.release()
            circles.release()
        }
    }

    private fun withinInnovationGate(
        measuredX: Float,
        measuredY: Float,
        predictedX: Float,
        predictedY: Float,
        reference: PlateAxisReference,
        dtSec: Float,
    ): Boolean {
        val maxJump = PlateRoi.maxInnovationPx(reference.majorAxisPx, dtSec.toDouble())
        if (maxJump <= 0f) return false
        val dx = measuredX - predictedX
        val dy = measuredY - predictedY
        return dx * dx + dy * dy <= maxJump * maxJump
    }

    private fun findEllipse(
        gray: Mat,
        requestedRoi: RectF,
        expectedAxis: Float?,
        predictedX: Float?,
        predictedY: Float?,
    ): EllipseFit? {
        val left = requestedRoi.left.toInt().coerceIn(0, gray.cols() - 1)
        val top = requestedRoi.top.toInt().coerceIn(0, gray.rows() - 1)
        val right = requestedRoi.right.toInt().coerceIn(left + 1, gray.cols())
        val bottom = requestedRoi.bottom.toInt().coerceIn(top + 1, gray.rows())
        if (right - left < 5 || bottom - top < 5) return null

        val blurred = Mat()
        val edges = Mat()
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        val crop = gray.submat(Rect(left, top, right - left, bottom - top))
        try {
            Imgproc.GaussianBlur(crop, blurred, org.opencv.core.Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 40.0, 120.0)
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_NONE)

            val candidates = contours.mapNotNull { contour ->
                val points = contour.toArray()
                if (points.size < 5) return@mapNotNull null
                val globalPoints = points.map { Point(it.x + left, it.y + top) }.toTypedArray()
                val points2f = MatOfPoint2f(*globalPoints)
                try {
                    val ellipse = Imgproc.fitEllipse(points2f)
                    val major = max(ellipse.size.width, ellipse.size.height).toFloat()
                    val minor = min(ellipse.size.width, ellipse.size.height).toFloat()
                    if (major <= 0f || minor <= 0f) return@mapNotNull null
                    if (expectedAxis != null && !PlateRoi.isValidMajorAxis(major, expectedAxis, MAX_AXIS_DEVIATION)) {
                        return@mapNotNull null
                    }
                    val area = abs(Imgproc.contourArea(contour)).toFloat()
                    val ellipseArea = (PI * major * minor / 4.0).toFloat()
                    val fill = (area / ellipseArea).coerceIn(0f, 1f)
                    val circularity = (minor / major).coerceIn(0f, 1f)
                    val confidence = (0.55f * circularity + 0.45f * fill).coerceIn(0f, 1f)
                    val distance = if (predictedX != null && predictedY != null) {
                        val dx = ellipse.center.x.toFloat() - predictedX
                        val dy = ellipse.center.y.toFloat() - predictedY
                        sqrt(dx * dx + dy * dy)
                    } else {
                        val dx = ellipse.center.x.toFloat() - (requestedRoi.left + requestedRoi.right) / 2f
                        val dy = ellipse.center.y.toFloat() - (requestedRoi.top + requestedRoi.bottom) / 2f
                        sqrt(dx * dx + dy * dy)
                    }
                    EllipseFit(ellipse, major, confidence, area, distance)
                } finally {
                    points2f.release()
                }
            }
            return candidates.maxWithOrNull(
                compareBy<EllipseFit> { it.area / (1f + it.distance) }.thenBy { it.confidence },
            )
        } finally {
            crop.release()
            contours.forEach(MatOfPoint::release)
            hierarchy.release()
            blurred.release()
            edges.release()
        }
    }

    private fun ensureOpenCv() {
        check(OpenCVLoader.initLocal()) { "OpenCV initialization failed; verify the OpenCV Android dependency." }
    }

    private data class HoughCircle(
        val centerX: Float,
        val centerY: Float,
        val radiusPx: Float,
        val confidence: Float,
        val distance: Float,
    )

    private data class EllipseFit(
        val ellipse: RotatedRect,
        val majorAxis: Float,
        val confidence: Float,
        val area: Float,
        val distance: Float,
    )

    private companion object {
        const val MAX_AXIS_DEVIATION = 0.15f
        const val MIN_TRACK_CONFIDENCE = 0.25f

        // Hough circle fallback tuned on the 720x1280 side-view PC videos (plate r ~130 px).
        const val HOUGH_DP = 1.2
        const val HOUGH_MIN_DIST_FACTOR = 0.5
        const val HOUGH_PARAM1 = 120.0
        const val HOUGH_PARAM2 = 45.0
        const val HOUGH_MIN_RADIUS_FACTOR = 0.85f
        const val HOUGH_MAX_RADIUS_FACTOR = 1.15f
        const val HOUGH_MAX_RADIUS_DEVIATION = 0.15f
    }
}
