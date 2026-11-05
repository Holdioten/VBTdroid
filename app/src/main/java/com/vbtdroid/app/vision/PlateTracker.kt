package com.vbtdroid.app.vision

import android.graphics.Bitmap
import android.graphics.RectF
import com.vbtdroid.core.model.PlateObservation
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
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
import kotlin.math.hypot
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
    private var consecutiveMisses = 0
    private var xReferencePx = Float.NaN
    private var detectorRegion: RectF? = null

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
        consecutiveMisses = 0
        xReferencePx = Float.NaN
        detectorRegion = null
    }

    override fun close() = reset()

    /**
     * 在用户 ROI 内优先用霍夫圆初始化。
     *
     * 期望半径取 ROI 短边的一半；圆检测对孔洞、反光和多边形轮廓不敏感，避免椭圆拟合锁到
     * 内毂（尺寸不对）。霍夫失败时才退回轮廓椭圆拟合。
     */
    open fun initialize(bitmap: Bitmap, roi: RectF, tSec: Double): PlateObservation {
        ensureOpenCv()
        require(!roi.isEmpty && roi.width() >= 5f && roi.height() >= 5f) { "Plate ROI is too small" }
        val gray = toGray(bitmap)
        try {
            val expectedRadius = min(roi.width(), roi.height()) / 2f
            val roiCenterX = (roi.left + roi.right) / 2f
            val roiCenterY = (roi.top + roi.bottom) / 2f
            val circle = findCircle(gray, roi, expectedRadius, roiCenterX, roiCenterY)
            val fitted = if (circle == null) findEllipse(gray, roi, null, null, null) else null
            velocityX = 0f
            velocityY = 0f
            consecutiveMisses = 0
            xReferencePx = circle?.centerX ?: fitted?.ellipse?.center?.x?.toFloat() ?: roiCenterX
            detectorRegion = analysisColumn(
                width = bitmap.width,
                height = bitmap.height,
                centerX = xReferencePx,
                halfWidth = maxOf(roi.width(), expectedRadius * 3f),
            )
            return when {
                circle != null -> {
                    axisReference = PlateAxisReference(circle.radiusPx * 2f)
                    commitSuccessfulFrame(gray, circle.centerX, circle.centerY, tSec)
                    PlateObservation(
                        tSec = tSec,
                        xPx = circle.centerX.toDouble(),
                        yPx = circle.centerY.toDouble(),
                        majorAxisPx = (circle.radiusPx * 2f).toDouble(),
                        confidence = circle.confidence.toDouble(),
                    )
                }
                fitted != null -> {
                    axisReference = PlateAxisReference(fitted.majorAxis)
                    commitSuccessfulFrame(
                        gray,
                        fitted.ellipse.center.x.toFloat(),
                        fitted.ellipse.center.y.toFloat(),
                        tSec,
                    )
                    observation(tSec, fitted)
                }
                else -> throw IllegalArgumentException(
                    "Could not locate a plate circle or ellipse inside the selected ROI",
                )
            }
        } finally {
            gray.release()
        }
    }

    /**
     * 使用 PyrLK 光流/速度预测中心，再在局部搜索区（整块杠铃片 + 余量）内做主信号：
     * 霍夫圆。圆检测对部分遮挡、反光和模糊更稳，接受后直接用圆心与固定参考直径；
     * 霍夫失败才退回轮廓椭圆，再失败才调用检测器恢复。
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
            val searchRadius = PlateRoi.localSearchRadiusPx(reference.majorAxisPx)
            if (searchRadius <= 0f) {
                successfulPair.failed()
                return null
            }
            val circle = findCircle(
                currentGray,
                RectF(
                    predictedX - searchRadius,
                    predictedY - searchRadius,
                    predictedX + searchRadius,
                    predictedY + searchRadius,
                ),
                reference.majorAxisPx / 2f,
                predictedX,
                predictedY,
            )?.takeIf { withinLateralGate(it.centerX, reference) }
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

            // Fallback: contour ellipse fit inside the same local window. It needs a clean
            // contour, so it is no longer the primary signal for controlled side views.
            val roi = RectF(
                predictedX - searchRadius,
                predictedY - searchRadius,
                predictedX + searchRadius,
                predictedY + searchRadius,
            )
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
                ?.takeIf { withinLateralGate(it.ellipse.center.x.toFloat(), reference) }
            if (fitted != null) {
                updatePosition(fitted.ellipse.center.x.toFloat(), fitted.ellipse.center.y.toFloat(), tSec, currentGray)
                return observation(tSec, fitted)
            }

            // Full-frame Hough recovery: after a fast concentric the plate can be far from the
            // local window even though its size is unchanged. The reference radius is known, so
            // a global search is both cheap and unambiguous; the detector is the last resort.
            val recoveredCircle = if (consecutiveMisses >= 1) {
                val region = detectorRegion ?: RectF(
                    0f, 0f, currentGray.cols().toFloat(), currentGray.rows().toFloat(),
                )
                findCircle(
                    currentGray,
                    region,
                    reference.majorAxisPx / 2f,
                    predictedX,
                    predictedY,
                    maxDistancePx = maxOf(region.width(), region.height()),
                )?.takeIf { withinLateralGate(it.centerX, reference) }
            } else {
                null
            }
            if (recoveredCircle != null) {
                updatePosition(recoveredCircle.centerX, recoveredCircle.centerY, tSec, currentGray)
                return PlateObservation(
                    tSec = tSec,
                    xPx = recoveredCircle.centerX.toDouble(),
                    yPx = recoveredCircle.centerY.toDouble(),
                    majorAxisPx = reference.majorAxisPx.toDouble(),
                    confidence = recoveredCircle.confidence.toDouble(),
                )
            }

            // 检测器只在跟踪丢失时调用，用于恢复而非逐帧主信号。
            val detection = if (consecutiveMisses >= DETECTOR_RECOVERY_MISSES) {
                detectPlate(bitmap)
            } else {
                null
            }
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
                    ?.takeIf { withinLateralGate(it.ellipse.center.x.toFloat(), reference) }
                if (recovered != null) {
                    updatePosition(recovered.ellipse.center.x.toFloat(), recovered.ellipse.center.y.toFloat(), tSec, currentGray)
                    return observation(tSec, recovered)
                }
                if (reference.accepts(detectedDiameter) && withinLateralGate(detection.cxPx, reference)) {
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
            // A failed frame is never paired with the last successful center. Decay the
            // prediction so it cannot run away while the plate is briefly unobservable.
            consecutiveMisses++
            velocityX *= MISS_VELOCITY_DECAY
            velocityY *= MISS_VELOCITY_DECAY
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

    /**
     * Sub-pixel centre: fit an ellipse to Canny edges on the rim. Hough returns integer
     * accumulator centres, which is several millimetres of lateral noise at 650 px/m.
     * A bad fit (moved more than 20% of the radius) is ignored.
     */
    private fun refineCircleCenter(gray: Mat, cx: Float, cy: Float, radius: Float): Point? {
        if (radius < 4f) return null
        val pad = (radius * 1.15f).toInt().coerceAtLeast(2)
        val left = (cx - pad).toInt().coerceIn(0, (gray.cols() - 1).coerceAtLeast(0))
        val top = (cy - pad).toInt().coerceIn(0, (gray.rows() - 1).coerceAtLeast(0))
        val right = (cx + pad).toInt().coerceIn(left + 1, gray.cols())
        val bottom = (cy + pad).toInt().coerceIn(top + 1, gray.rows())
        if (right - left < 8 || bottom - top < 8) return null
        val crop = gray.submat(Rect(left, top, right - left, bottom - top)).clone()
        val edges = Mat()
        val nonZero = Mat()
        try {
            Imgproc.Canny(crop, edges, 60.0, 160.0)
            Core.findNonZero(edges, nonZero)
            if (nonZero.empty()) return null
            val minRadius = radius * 0.9f
            val maxRadius = radius * 1.1f
            val points = ArrayList<Point>()
            for (index in 0 until nonZero.rows()) {
                val value = nonZero.get(index, 0) ?: continue
                if (value.size < 2) continue
                val px = value[0].toFloat() + left
                val py = value[1].toFloat() + top
                val distance = hypot(px - cx, py - cy)
                if (distance in minRadius..maxRadius) points += Point(px.toDouble(), py.toDouble())
            }
            if (points.size < 12) return null
            val mat = MatOfPoint2f(*points.toTypedArray())
            val ellipse = try {
                Imgproc.fitEllipse(mat)
            } finally {
                mat.release()
            }
            if (hypot(ellipse.center.x - cx, ellipse.center.y - cy) > radius * 0.2) return null
            return Point(ellipse.center.x, ellipse.center.y)
        } catch (_: RuntimeException) {
            return null
        } finally {
            crop.release()
            edges.release()
            nonZero.release()
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
            val maxSpeed = (axisReference?.majorAxisPx ?: 0f) *
                PlateRoi.MAX_BAR_SPEED_DIAMETERS_PER_SEC.toFloat()
            velocityX = ((nextX - centerX) / elapsed).let {
                if (maxSpeed > 0f) it.coerceIn(-maxSpeed, maxSpeed) else it
            }
            velocityY = ((nextY - centerY) / elapsed).let {
                if (maxSpeed > 0f) it.coerceIn(-maxSpeed, maxSpeed) else it
            }
        }
        consecutiveMisses = 0
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

    /** Detection runs inside the plate column so the gym background cannot win the largest box. */
    private fun detectPlate(bitmap: Bitmap): PlateDetection? {
        val plateDetector = detector ?: return null
        val region = detectorRegion ?: return PlateRoi.selectLargest(plateDetector.detect(bitmap))
        val left = region.left.toInt().coerceIn(0, (bitmap.width - 2).coerceAtLeast(0))
        val top = region.top.toInt().coerceIn(0, (bitmap.height - 2).coerceAtLeast(0))
        val width = (region.right.toInt() - left).coerceIn(2, bitmap.width - left)
        val height = (region.bottom.toInt() - top).coerceIn(2, bitmap.height - top)
        val crop = Bitmap.createBitmap(bitmap, left, top, width, height)
        return try {
            PlateRoi.selectLargest(plateDetector.detect(crop))?.let { detection ->
                detection.copy(cxPx = detection.cxPx + left, cyPx = detection.cyPx + top)
            }
        } finally {
            if (!crop.isRecycled) crop.recycle()
        }
    }

    private fun analysisColumn(width: Int, height: Int, centerX: Float, halfWidth: Float): RectF {
        val safeHalf = halfWidth.coerceAtLeast(1f)
        return RectF(
            (centerX - safeHalf).coerceIn(0f, width.toFloat()),
            0f,
            (centerX + safeHalf).coerceIn(0f, width.toFloat()),
            height.toFloat(),
        )
    }

    /** A bar never travels more than half a plate diameter sideways; anything else is another object. */
    private fun withinLateralGate(candidateX: Float, reference: PlateAxisReference): Boolean {
        if (!xReferencePx.isFinite()) return true
        return abs(candidateX - xReferencePx) <= reference.majorAxisPx * MAX_LATERAL_DIAMETERS
    }

    /**
     * 在局部 ROI 内检测圆。候选按参考半径 ±15% 过滤，再选离预测中心最近的一个；
     * 该半径门拒绝内毂和相邻杠铃片。ROI 本身已经围绕预测点，因此不再叠加逐帧跳变门。
     */
    private fun findCircle(
        gray: Mat,
        requestedRoi: RectF,
        expectedRadius: Float,
        predictedX: Float,
        predictedY: Float,
        maxDistancePx: Float = -1f,
    ): HoughCircle? {
        if (!expectedRadius.isFinite() || expectedRadius <= 1f) return null
        val left = requestedRoi.left.toInt().coerceIn(0, gray.cols() - 1)
        val top = requestedRoi.top.toInt().coerceIn(0, gray.rows() - 1)
        val right = requestedRoi.right.toInt().coerceIn(left + 1, gray.cols())
        val bottom = requestedRoi.bottom.toInt().coerceIn(top + 1, gray.rows())
        if (right - left < 5 || bottom - top < 5) return null

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
            val candidates = ArrayList<CircleCandidate>(circles.cols())
            for (index in 0 until circles.cols()) {
                val values = circles.get(0, index) ?: continue
                if (values.size < 3) continue
                candidates += CircleCandidate(
                    centerX = (values[0] + left).toFloat(),
                    centerY = (values[1] + top).toFloat(),
                    radiusPx = values[2].toFloat(),
                )
            }
            val maxDistance = if (maxDistancePx > 0f) {
                maxDistancePx
            } else {
                min(requestedRoi.width(), requestedRoi.height()) / 2f
            }
            val accepted = PlateRoi.selectCircle(
                candidates = candidates,
                expectedRadiusPx = expectedRadius,
                predictedX = predictedX,
                predictedY = predictedY,
                maxDistancePx = maxDistance,
                radiusTolerance = HOUGH_MAX_RADIUS_DEVIATION,
            ) ?: return null

            val deviation = abs(accepted.radiusPx - expectedRadius) / expectedRadius
            val dx = accepted.centerX - predictedX
            val dy = accepted.centerY - predictedY
            val distance = sqrt(dx * dx + dy * dy)
            val radiusScore = (1f - deviation / HOUGH_MAX_RADIUS_DEVIATION).coerceIn(0f, 1f)
            val distanceScore = (1f - distance / maxDistance).coerceIn(0f, 1f)
            val confidence = (HOUGH_ACCEPT_CONFIDENCE + HOUGH_ACCEPT_BONUS * radiusScore * distanceScore)
                .coerceIn(HOUGH_ACCEPT_CONFIDENCE, 0.95f)
            val refined = refineCircleCenter(gray, accepted.centerX, accepted.centerY, accepted.radiusPx)
            return HoughCircle(
                refined?.x?.toFloat() ?: accepted.centerX,
                refined?.y?.toFloat() ?: accepted.centerY,
                accepted.radiusPx,
                confidence,
                distance,
            )
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

        // Hough circle tuned on the 720x1280 controlled side-view PC videos (plate r ~147 px).
        const val HOUGH_DP = 1.2
        const val HOUGH_MIN_DIST_FACTOR = 0.5
        const val HOUGH_PARAM1 = 120.0
        const val HOUGH_PARAM2 = 45.0
        const val HOUGH_MIN_RADIUS_FACTOR = 0.85f
        const val HOUGH_MAX_RADIUS_FACTOR = 1.15f
        const val HOUGH_MAX_RADIUS_DEVIATION = 0.15f

        // An accepted Hough circle is the primary signal; keep it above the 0.6 quality warning
        // threshold so a clean plate is not flagged LOW_CALIBRATION/LOW_TRACK_CONFIDENCE.
        const val HOUGH_ACCEPT_CONFIDENCE = 0.75f
        const val DETECTOR_RECOVERY_MISSES = 3
        const val MAX_LATERAL_DIAMETERS = 0.5f
        const val MISS_VELOCITY_DECAY = 0.5f
        const val HOUGH_ACCEPT_BONUS = 0.15f
    }
}
