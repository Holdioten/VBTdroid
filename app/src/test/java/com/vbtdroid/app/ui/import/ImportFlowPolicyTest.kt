package com.vbtdroid.app.ui.import

import com.vbtdroid.app.ui.import.ImportFlowPolicy.PAGE_COUNT
import com.vbtdroid.app.ui.import.ImportFlowPolicy.PAGE_ROI
import com.vbtdroid.app.ui.import.ImportFlowPolicy.PAGE_SETTINGS
import com.vbtdroid.app.ui.import.ImportFlowPolicy.PAGE_VIDEO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression: order is video → ROI → settings, and start only appears on the last page. */
class ImportFlowPolicyTest {
    @Test
    fun pageOrderIsVideoRoiSettings() {
        assertEquals(0, PAGE_VIDEO)
        assertEquals(1, PAGE_ROI)
        assertEquals(2, PAGE_SETTINGS)
        assertEquals(3, PAGE_COUNT)
    }

    @Test
    fun videoNextNeedsLoadedFrame() {
        assertTrue(ImportFlowPolicy.canLeaveVideo(hasFrame = true, loading = false))
        assertFalse(ImportFlowPolicy.canLeaveVideo(hasFrame = false, loading = false))
        assertFalse(ImportFlowPolicy.canLeaveVideo(hasFrame = true, loading = true))
    }

    @Test
    fun roiNextNeedsConfirmation() {
        assertFalse(ImportFlowPolicy.canLeaveRoi(roiConfirmed = false))
        assertTrue(ImportFlowPolicy.canLeaveRoi(roiConfirmed = true))
    }

    @Test
    fun startOnlyOnSettingsPageWhenConfirmedAndValid() {
        for (page in 0 until PAGE_COUNT) {
            for (confirmed in listOf(false, true)) {
                for (valid in listOf(false, true)) {
                    val expected = page == PAGE_SETTINGS && confirmed && valid
                    assertEquals("page=$page confirmed=$confirmed valid=$valid",
                        expected, ImportFlowPolicy.showStart(page, confirmed, valid))
                }
            }
        }
    }

    @Test
    fun roiPageNeverShowsStart() {
        assertFalse(ImportFlowPolicy.showStart(PAGE_ROI, roiConfirmed = true, validSettings = true))
    }

    @Test
    fun settingsWithoutRoiSendsBackToRoi() {
        assertTrue(ImportFlowPolicy.mustReturnToRoi(PAGE_SETTINGS, roiConfirmed = false))
        assertFalse(ImportFlowPolicy.mustReturnToRoi(PAGE_SETTINGS, roiConfirmed = true))
        assertFalse(ImportFlowPolicy.mustReturnToRoi(PAGE_ROI, roiConfirmed = false))
    }
}
