package com.vbtdroid.app.ui.import

/**
 * Pure rules for when the plate ROI may be edited.
 *
 * Confirmation locks the region: no drag, no resize, no redraw until the user explicitly chooses
 * "修改区域" (which clears confirmation) or "重置". Analysis also locks it.
 */
object RoiEditPolicy {
    fun canEdit(roiConfirmed: Boolean, analyzing: Boolean): Boolean = !roiConfirmed && !analyzing
}
