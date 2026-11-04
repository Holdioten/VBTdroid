package com.vbtdroid.app.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for multi-crop plate detection: box mapping and cross-crop NMS merge. */
class MultiCropPlateDetectionTest {
    @Test
    fun `crop detections are scaled and offset back to original coordinates`() {
        // Detections come from a 50x100 bitmap that represents a 100x200 region at (10, 20).
        val region = EfficientDetPostprocess.CropRegion(left = 10, top = 20, width = 100, height = 200)
        val detection = PlateDetection(cxPx = 25f, cyPx = 30f, widthPx = 10f, heightPx = 20f, confidence = 0.7f)

        val mapped = EfficientDetPostprocess.mapCropDetections(
            detections = listOf(detection),
            cropWidth = 50,
            cropHeight = 100,
            region = region,
        ).single()

        assertEquals(60f, mapped.cxPx, 1e-4f)
        assertEquals(80f, mapped.cyPx, 1e-4f)
        assertEquals(20f, mapped.widthPx, 1e-4f)
        assertEquals(40f, mapped.heightPx, 1e-4f)
        assertEquals(0.7f, mapped.confidence, 1e-6f)
    }

    @Test
    fun `native resolution crop maps by offset only`() {
        val region = EfficientDetPostprocess.CropRegion(left = 0, top = 200, width = 720, height = 768)
        val detection = PlateDetection(cxPx = 360f, cyPx = 100f, widthPx = 80f, heightPx = 80f, confidence = 0.5f)

        val mapped = EfficientDetPostprocess.mapCropDetections(
            detections = listOf(detection),
            cropWidth = 720,
            cropHeight = 768,
            region = region,
        ).single()

        assertEquals(360f, mapped.cxPx, 1e-4f)
        assertEquals(300f, mapped.cyPx, 1e-4f)
        assertEquals(80f, mapped.widthPx, 1e-4f)
        assertEquals(80f, mapped.heightPx, 1e-4f)
    }

    @Test
    fun `mapping drops detections when crop dimensions are invalid`() {
        val region = EfficientDetPostprocess.CropRegion(0, 0, 100, 100)
        val detection = PlateDetection(50f, 50f, 20f, 20f, 0.9f)

        assertTrue(
            EfficientDetPostprocess.mapCropDetections(listOf(detection), 0, 100, region).isEmpty(),
        )
    }

    @Test
    fun `merge drops duplicates from overlapping crops and sorts by score`() {
        val fullCrop = PlateDetection(cxPx = 360f, cyPx = 120f, widthPx = 80f, heightPx = 80f, confidence = 0.42f)
        // Same plate seen in the top crop, mapped back a few pixels off.
        val topCrop = PlateDetection(cxPx = 364f, cyPx = 122f, widthPx = 80f, heightPx = 80f, confidence = 0.51f)
        val otherPlate = PlateDetection(cxPx = 600f, cyPx = 400f, widthPx = 60f, heightPx = 60f, confidence = 0.62f)

        val merged = EfficientDetPostprocess.mergeDetections(listOf(fullCrop, topCrop, otherPlate))

        assertEquals(2, merged.size)
        assertEquals(listOf(otherPlate, topCrop), merged)
        assertTrue(!merged.contains(fullCrop))
    }

    @Test
    fun `merge ignores empty crops and keeps disjoint detections`() {
        val first = PlateDetection(100f, 100f, 40f, 40f, 0.4f)
        val second = PlateDetection(300f, 300f, 40f, 40f, 0.45f)

        val merged = EfficientDetPostprocess.mergeDetections(listOf(first, second))

        assertEquals(listOf(second, first), merged)
    }

    @Test
    fun `merge returns empty for no detections`() {
        assertTrue(EfficientDetPostprocess.mergeDetections(emptyList()).isEmpty())
    }
}
