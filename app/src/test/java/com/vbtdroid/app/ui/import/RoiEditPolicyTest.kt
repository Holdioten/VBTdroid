package com.vbtdroid.app.ui.import

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression: a confirmed ROI must be locked; analysis must lock it too. */
class RoiEditPolicyTest {
    @Test
    fun editableOnlyWhileUnconfirmedAndIdle() {
        assertTrue(RoiEditPolicy.canEdit(roiConfirmed = false, analyzing = false))
    }

    @Test
    fun confirmedRoiIsLocked() {
        assertFalse(RoiEditPolicy.canEdit(roiConfirmed = true, analyzing = false))
    }

    @Test
    fun analysisLocksRoiEvenWhenUnconfirmed() {
        assertFalse(RoiEditPolicy.canEdit(roiConfirmed = false, analyzing = true))
    }

    @Test
    fun confirmedAndAnalyzingIsLocked() {
        assertFalse(RoiEditPolicy.canEdit(roiConfirmed = true, analyzing = true))
    }
}
