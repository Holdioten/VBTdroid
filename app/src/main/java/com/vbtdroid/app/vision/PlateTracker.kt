package com.vbtdroid.app.vision

import android.graphics.Bitmap
import android.graphics.RectF
import com.vbtdroid.core.model.PlateObservation
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.RotatedRect
import org.opencv.imgproc.Imgproc
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
    private var expectedMajorAxis = 0f
    private var lastObservation: PlateObservation? = null

    fun initialize(bitmap: Bitmap, roi: RectF, tSec: Double): PlateObservation {
        ensureOpenCv()
        require(!roi.isEmpty && roi.width() >= 5f && roi.height() >= 5f) { "Plate ROI is too small" }
        val fitted = findEllipse(bitmap, roi, null, null, null)
            ?: throw IllegalArgumentException("Could not fit a plate ellipse inside the selected ROI")
        centerX = fitted.ellipse.center.x.toFloat()
        centerY = fitted.ellipse.center.y.toFloat()
        velocityX = 0f
        velocityY = 0f
        lastTimeSec = tSec
        expectedMajorAxis = fitted.majorAxis
        return observation(tSec, fitted)
    }

    /** 根据前序中心和速度预测当前位置，再用轮廓拟合进行逐帧修正。 */
    fun update(bitmap: Bitmap, tSec: Double): PlateObservation? {
        ensureOpenCv()
        if (expectedMajorAxis <= 0f) return null

        // 扩大局部搜索范围以适应杠铃片移动，同时借助轴长限制排除内圈候选。
        val dt = (tSec - (lastTimeSec ?: tSec)).coerceAtLeast(0.0).toFloat()
        val predictedX = centerX + velocityX * dt
        val predictedY = centerY + velocityY * dt
        val radius = expectedMajorAxis * 0.9f
        val roi = RectF(predictedX - radius, predictedY - radius, predictedX + radius, predictedY + radius)
        val fitted = findEllipse(bitmap, roi, expectedMajorAxis, predictedX, predictedY)
        if (fitted != null && fitted.confidence >= MIN_TRACK_CONFIDENCE) {
            updatePosition(fitted.ellipse.center.x.toFloat(), fitted.ellipse.center.y.toFloat(), tSec)
            expectedMajorAxis = fitted.majorAxis
            return observation(tSec, fitted)
        }

        val detection = detector?.let { PlateRoi.selectLargest(it.detect(bitmap)) }
        if (detection != null) {
            val diameter = max(detection.widthPx, detection.heightPx)
            val box = RectF(
                detection.cxPx - detection.widthPx / 2f,
                detection.cyPx - detection.heightPx / 2f,
                detection.cxPx + detection.widthPx / 2f,
                detection.cyPx + detection.heightPx / 2f,
            )
            val recovered = findEllipse(bitmap, box, expectedMajorAxis, detection.cxPx, detection.cyPx)
            val result = recovered?.takeIf { it.confidence >= MIN_TRACK_CONFIDENCE }
            if (result != null) {
                updatePosition(result.ellipse.center.x.toFloat(), result.ellipse.center.y.toFloat(), tSec)
                expectedMajorAxis = result.majorAxis
                return observation(tSec, result)
            }
            if (PlateRoi.isValidMajorAxis(diameter, expectedMajorAxis, MAX_AXIS_DEVIATION)) {
                updatePosition(detection.cxPx, detection.cyPx, tSec)
                expectedMajorAxis = diameter
                val fallback = PlateObservation(
                    tSec = tSec,
                    xPx = centerX.toDouble(),
                    yPx = centerY.toDouble(),
                    majorAxisPx = expectedMajorAxis.toDouble(),
                    confidence = detection.confidence.toDouble(),
                )
                lastObservation = fallback
                return fallback
            }
        }
        lastObservation = null
        return null
    }

    private fun observation(tSec: Double, fit: EllipseFit): PlateObservation =
        PlateObservation(
            tSec = tSec,
            xPx = fit.ellipse.center.x,
            yPx = fit.ellipse.center.y,
            majorAxisPx = fit.majorAxis.toDouble(),
            confidence = fit.confidence.toDouble(),
        ).also { lastObservation = it }

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

    private fun findEllipse(
        bitmap: Bitmap,
        requestedRoi: RectF,
        expectedAxis: Float?,
        predictedX: Float?,
        predictedY: Float?,
    ): EllipseFit? {
        val left = requestedRoi.left.toInt().coerceIn(0, bitmap.width - 1)
        val top = requestedRoi.top.toInt().coerceIn(0, bitmap.height - 1)
        val right = requestedRoi.right.toInt().coerceIn(left + 1, bitmap.width)
        val bottom = requestedRoi.bottom.toInt().coerceIn(top + 1, bitmap.height)
        if (right - left < 5 || bottom - top < 5) return null

        val rgba = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        var crop: Mat? = null
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            crop = gray.submat(Rect(left, top, right - left, bottom - top))
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
            crop?.release()
            contours.forEach(MatOfPoint::release)
            hierarchy.release()
            rgba.release()
            gray.release()
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
