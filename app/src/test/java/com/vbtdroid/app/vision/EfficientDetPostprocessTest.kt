package com.vbtdroid.app.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EfficientDetPostprocessTest {
    @Test
    fun `parse filters by score class and positive box`() {
        val scores = floatArrayOf(0.9f, 0.34f, 0.8f, 0.7f, 0.6f)
        val boxes = floatArrayOf(
            0.1f, 0.1f, 0.5f, 0.5f,
            0.1f, 0.1f, 0.5f, 0.5f,
            0.2f, 0.2f, 0.4f, 0.4f,
            0.5f, 0.5f, 0.5f, 0.8f,
            Float.NaN, 0.1f, 0.5f, 0.5f,
        )
        val classes = floatArrayOf(0f, 0f, 1f, 0f, 0f)

        val candidates = EfficientDetPostprocess.parse(scores, boxes, classes, count = 5f)

        assertEquals(1, candidates.size)
        assertEquals(0.9f, candidates.first().confidence, 1e-6f)
    }

    @Test
    fun `parse only reads the reported detection count`() {
        val scores = floatArrayOf(0.9f, 0.8f)
        val boxes = floatArrayOf(
            0f, 0f, 1f, 1f,
            0.2f, 0.2f, 0.6f, 0.6f,
        )

        val candidates = EfficientDetPostprocess.parse(scores, boxes, null, count = 1f)

        assertEquals(1, candidates.size)
        assertEquals(0f, candidates.first().xmin, 1e-6f)
    }

    @Test
    fun `parse accepts null class ids and ignores non-positive count`() {
        val scores = floatArrayOf(0.9f)
        val boxes = floatArrayOf(0.1f, 0.1f, 0.5f, 0.5f)

        assertEquals(1, EfficientDetPostprocess.parse(scores, boxes, null, count = 1.4f).size)
        assertTrue(EfficientDetPostprocess.parse(scores, boxes, null, count = 0f).isEmpty())
        assertTrue(EfficientDetPostprocess.parse(scores, boxes, null, count = Float.NaN).isEmpty())
    }

    @Test
    fun `normalized boxes map to original bitmap coordinates`() {
        val candidate = EfficientDetPostprocess.Candidate(
            confidence = 0.9f,
            ymin = 0.2f,
            xmin = 0.1f,
            ymax = 0.6f,
            xmax = 0.5f,
        )

        val detection = EfficientDetPostprocess.toDetections(listOf(candidate), 100, 200).single()

        assertEquals(30f, detection.cxPx, 1e-4f)
        assertEquals(80f, detection.cyPx, 1e-4f)
        assertEquals(40f, detection.widthPx, 1e-4f)
        assertEquals(80f, detection.heightPx, 1e-4f)
        assertEquals(0.9f, detection.confidence, 1e-6f)
    }

    @Test
    fun `boxes are clamped to bitmap and degenerate boxes dropped`() {
        val outside = EfficientDetPostprocess.Candidate(0.8f, -0.2f, -0.3f, 0.4f, 0.5f)
        val degenerate = EfficientDetPostprocess.Candidate(0.9f, 0.5f, 0.5f, 0.5f, 0.9f)

        val detections = EfficientDetPostprocess.toDetections(listOf(outside, degenerate), 100, 100)

        val detection = detections.single()
        assertEquals(25f, detection.cxPx, 1e-4f)
        assertEquals(20f, detection.cyPx, 1e-4f)
        assertEquals(50f, detection.widthPx, 1e-4f)
        assertEquals(40f, detection.heightPx, 1e-4f)
    }

    @Test
    fun `class-id heuristic separates integer ids from scores`() {
        assertTrue(EfficientDetPostprocess.looksLikeClassIds(floatArrayOf(0f, 0f, 0f), 3))
        assertTrue(!EfficientDetPostprocess.looksLikeClassIds(floatArrayOf(0.9f, 0.2f), 2))
        assertTrue(!EfficientDetPostprocess.looksLikeClassIds(floatArrayOf(-1f, 0f), 2))
        assertTrue(!EfficientDetPostprocess.looksLikeClassIds(floatArrayOf(0f, 0f), 0))
    }

    @Test
    fun `nms keeps the highest scoring overlapping box`() {
        val high = PlateDetection(50f, 50f, 40f, 40f, 0.9f)
        val overlapping = PlateDetection(52f, 52f, 40f, 40f, 0.8f)
        val separate = PlateDetection(150f, 150f, 20f, 20f, 0.7f)

        val kept = EfficientDetPostprocess.nonMaximumSuppression(listOf(overlapping, separate, high))

        assertEquals(listOf(high, separate), kept)
    }

    @Test
    fun `iou is zero for disjoint boxes and one for identical boxes`() {
        val a = PlateDetection(50f, 50f, 20f, 20f, 0.9f)
        val b = PlateDetection(200f, 200f, 20f, 20f, 0.8f)

        assertEquals(1f, EfficientDetPostprocess.intersectionOverUnion(a, a), 1e-6f)
        assertEquals(0f, EfficientDetPostprocess.intersectionOverUnion(a, b), 1e-6f)
    }
}
