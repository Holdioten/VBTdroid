package com.vbtdroid.app.vision

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure post-processing for EfficientDet-Lite0 object detectors that emit four tensors:
 * scores `[1, N]`, boxes `[1, N, 4]` (normalized `ymin, xmin, ymax, xmax`), count `[1]`,
 * and class ids `[1, N]`.
 *
 * Kept free of Android/TFLite types so the mapping and NMS can be unit-tested on the JVM.
 */
object EfficientDetPostprocess {
    const val SCORE_THRESHOLD = 0.35f
    const val NMS_IOU_THRESHOLD = 0.45f
    const val TARGET_CLASS_ID = 0

    /** A detection in normalized image coordinates, as returned by the model. */
    data class Candidate(
        val confidence: Float,
        val ymin: Float,
        val xmin: Float,
        val ymax: Float,
        val xmax: Float,
    )

    /** A sub-region of the original bitmap, in native pixel coordinates. */
    data class CropRegion(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
    ) {
        init {
            require(left >= 0 && top >= 0 && width > 0 && height > 0) {
                "Invalid crop region: left=$left top=$top width=$width height=$height"
            }
        }
    }

    /**
     * Filters model outputs down to scored, class-0, positive boxes.
     *
     * @param classIds optional class ids; when null every detection is treated as [TARGET_CLASS_ID].
     * @param count the model's detection count (may be fractional before conversion).
     */
    fun parse(
        scores: FloatArray,
        boxes: FloatArray,
        classIds: FloatArray?,
        count: Float,
        scoreThreshold: Float = SCORE_THRESHOLD,
        targetClassId: Int = TARGET_CLASS_ID,
    ): List<Candidate> {
        if (!count.isFinite() || count <= 0f) return emptyList()
        val available = min(scores.size, boxes.size / BOX_VALUES)
            .let { if (classIds == null) it else min(it, classIds.size) }
        if (available <= 0) return emptyList()
        val limit = min(available, count.roundToInt().coerceAtLeast(0))

        val candidates = ArrayList<Candidate>(limit)
        for (index in 0 until limit) {
            val score = scores[index]
            if (!score.isFinite() || score < scoreThreshold) continue
            if (classIds != null) {
                val classId = classIds[index]
                if (!classId.isFinite() || classId.roundToInt() != targetClassId) continue
            }
            val offset = index * BOX_VALUES
            val ymin = boxes[offset]
            val xmin = boxes[offset + 1]
            val ymax = boxes[offset + 2]
            val xmax = boxes[offset + 3]
            if (!ymin.isFinite() || !xmin.isFinite() || !ymax.isFinite() || !xmax.isFinite()) continue
            if (ymax <= ymin || xmax <= xmin) continue
            candidates += Candidate(
                confidence = score,
                ymin = ymin,
                xmin = xmin,
                ymax = ymax,
                xmax = xmax,
            )
        }
        return candidates
    }

    /**
     * Converts normalized candidates to pixel detections in the original bitmap coordinate space,
     * clamps them to the bitmap and applies NMS.
     */
    fun toDetections(
        candidates: List<Candidate>,
        bitmapWidth: Int,
        bitmapHeight: Int,
        iouThreshold: Float = NMS_IOU_THRESHOLD,
    ): List<PlateDetection> {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) return emptyList()
        val width = bitmapWidth.toFloat()
        val height = bitmapHeight.toFloat()
        val mapped = candidates.mapNotNull { candidate ->
            val left = (candidate.xmin.coerceIn(0f, 1f) * width).coerceIn(0f, width)
            val right = (candidate.xmax.coerceIn(0f, 1f) * width).coerceIn(0f, width)
            val top = (candidate.ymin.coerceIn(0f, 1f) * height).coerceIn(0f, height)
            val bottom = (candidate.ymax.coerceIn(0f, 1f) * height).coerceIn(0f, height)
            val boxWidth = right - left
            val boxHeight = bottom - top
            if (boxWidth <= 0f || boxHeight <= 0f) return@mapNotNull null
            PlateDetection(
                cxPx = (left + right) / 2f,
                cyPx = (top + bottom) / 2f,
                widthPx = boxWidth,
                heightPx = boxHeight,
                confidence = candidate.confidence.coerceIn(0f, 1f),
            )
        }
        return nonMaximumSuppression(mapped, iouThreshold)
    }

    /**
     * Maps detections expressed in a crop's pixel space back to the original bitmap.
     *
     * [cropWidth]/[cropHeight] are the coordinate space the detections refer to (the bitmap that
     * was fed to the model), while [region] is where that crop sits in the original bitmap. This
     * covers both a native-resolution crop (scale 1) and a resized crop (scale > 1).
     */
    fun mapCropDetections(
        detections: List<PlateDetection>,
        cropWidth: Int,
        cropHeight: Int,
        region: CropRegion,
    ): List<PlateDetection> {
        if (cropWidth <= 0 || cropHeight <= 0) return emptyList()
        val scaleX = region.width.toFloat() / cropWidth
        val scaleY = region.height.toFloat() / cropHeight
        return detections.mapNotNull { detection ->
            val width = detection.widthPx * scaleX
            val height = detection.heightPx * scaleY
            if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) {
                return@mapNotNull null
            }
            PlateDetection(
                cxPx = detection.cxPx * scaleX + region.left,
                cyPx = detection.cyPx * scaleY + region.top,
                widthPx = width,
                heightPx = height,
                confidence = detection.confidence,
            )
        }
    }

    /**
     * Flattens detections from several crops and drops cross-crop duplicates with NMS.
     * The result is sorted by descending confidence (see [nonMaximumSuppression]).
     */
    fun mergeDetections(
        detections: List<PlateDetection>,
        iouThreshold: Float = NMS_IOU_THRESHOLD,
    ): List<PlateDetection> = nonMaximumSuppression(
        detections.filter { it.confidence.isFinite() && it.widthPx > 0f && it.heightPx > 0f },
        iouThreshold,
    )

    /**
     * Heuristic used to tell the class-id tensor apart from the score tensor when tensor names do
     * not expose `score`/`class`. Class ids are small non-negative integers; scores are not.
     */
    fun looksLikeClassIds(values: FloatArray, count: Int): Boolean {
        val limit = min(values.size, count).coerceAtLeast(0)
        if (limit == 0) return false
        for (index in 0 until limit) {
            val value = values[index]
            if (!value.isFinite() || value < 0f || value > MAX_CLASS_ID || value != value.toInt().toFloat()) {
                return false
            }
        }
        return true
    }

    fun nonMaximumSuppression(
        detections: List<PlateDetection>,
        iouThreshold: Float = NMS_IOU_THRESHOLD,
    ): List<PlateDetection> {
        val remaining = detections.sortedByDescending { it.confidence }.toMutableList()
        val kept = mutableListOf<PlateDetection>()
        while (remaining.isNotEmpty()) {
            val best = remaining.removeAt(0)
            kept += best
            remaining.removeAll { intersectionOverUnion(best, it) > iouThreshold }
        }
        return kept
    }

    fun intersectionOverUnion(a: PlateDetection, b: PlateDetection): Float {
        val left = max(a.cxPx - a.widthPx / 2f, b.cxPx - b.widthPx / 2f)
        val top = max(a.cyPx - a.heightPx / 2f, b.cyPx - b.heightPx / 2f)
        val right = min(a.cxPx + a.widthPx / 2f, b.cxPx + b.widthPx / 2f)
        val bottom = min(a.cyPx + a.heightPx / 2f, b.cyPx + b.heightPx / 2f)
        val intersection = max(0f, right - left) * max(0f, bottom - top)
        val union = a.widthPx * a.heightPx + b.widthPx * b.heightPx - intersection
        return if (union > 0f) intersection / union else 0f
    }

    const val BOX_VALUES = 4
    const val MAX_CLASS_ID = 1000f
}
