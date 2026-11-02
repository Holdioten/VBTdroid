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
class PlateTracker(private val detector: PlateDetector? = null) {
    private var centerX = 0f
    private var centerY = 0f
    private var velocityX = 0f
    private var velocityY = 0f
    private var lastTimeSec: Double? = null
    private var axisReference: PlateAxisReference? = null
    private var previousGray: Mat? = null

    fun initialize(bitmap: Bitmap, roi: RectF, tSec: Double): PlateObservation {
        ensureOpenCv()
        require(!roi.isEmpty && roi.width() >= 5f && roi.height() >= 5f) { "Plate ROI is too small" }
        val gray = toGray(bitmap)
        try {
            val fitted = findEllipse(gray, roi, null, null, null)
                ?: throw IllegalArgumentException("Could not fit a plate ellipse inside the selected ROI")
            centerX = fitted.ellipse.center.x.toFloat()
            centerY = fitted.ellipse.center.y.toFloat()
            velocityX = 0f
            velocityY = 0f
            lastTimeSec = tSec
            axisReference = PlateAxisReference(fitted.majorAxis)
            replacePreviousGray(gray)
            return observation(tSec, fitted)
        } finally {
            gray.release()
        }
    }

    /** 使用 PyrLK 光流预测中心，再在局部搜索区拟合椭圆精修。 */
    fun update(bitmap: Bitmap, tSec: Double): PlateObservation? {
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

            val radius = reference.majorAxisPx * 0.9f
            val roi = RectF(predictedX - radius, predictedY - radius, predictedX + radius, predictedY + radius)
            val fitted = findEllipse(currentGray, roi, reference.majorAxisPx, predictedX, predictedY)
            if (fitted != null && fitted.confidence >= MIN_TRACK_CONFIDENCE) {
                updatePosition(fitted.ellipse.center.x.toFloat(), fitted.ellipse.center.y.toFloat(), tSec)
                return observation(tSec, fitted)
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
                    updatePosition(recovered.ellipse.center.x.toFloat(), recovered.ellipse.center.y.toFloat(), tSec)
                    return observation(tSec, recovered)
                }
                if (reference.accepts(detectedDiameter)) {
                    updatePosition(detection.cxPx, detection.cyPx, tSec)
                    return PlateObservation(
                        tSec = tSec,
                        xPx = centerX.toDouble(),
                        yPx = centerY.toDouble(),
                        majorAxisPx = reference.majorAxisPx.toDouble(),
                        confidence = detection.confidence.toDouble(),
                    )
                }
            }
            return null
        } finally {
            replacePreviousGray(currentGray)
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

    private fun updatePosition(nextX: Float, nextY: Float, tSec: Double) {
        val previousTime = lastTimeSec
        if (previousTime != null && tSec > previousTime) {
            val elapsed = (tSec - previousTime).toFloat()
            velocityX = (nextX - centerX) / elapsed
            velocityY = (nextY - centerY) / elapsed
        }
        centerX = nextX
        centerY = nextY
        lastTimeSec = tSec
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

    private fun replacePreviousGray(gray: Mat) {
        previousGray?.release()
        previousGray = gray.clone()
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

    private data class EllipseFit(
        val ellipse: RotatedRect,
        val majorAxis: Float,
        val confidence: Float,
        val area: Float,
        val distance: Float,
    )

    private companion object {
        const val MAX_AXIS_DEVIATION = 0.25f
        const val MIN_TRACK_CONFIDENCE = 0.25f
    }
}
