package com.vbtdroid.app.ui.import

/**
 * Pre-analysis page order and gating rules, kept pure so the flow can be unit-tested.
 *
 * Order: 1 视频 (choose video) → 2 框选 (ROI draw/confirm) → 3 参数 (settings, then start).
 */
object ImportFlowPolicy {
    const val PAGE_VIDEO = 0
    const val PAGE_ROI = 1
    const val PAGE_SETTINGS = 2
    const val PAGE_COUNT = 3

    /** 下一步 on the video page once a first frame is ready. */
    fun canLeaveVideo(hasFrame: Boolean, loading: Boolean): Boolean = hasFrame && !loading

    /** 下一步 on the ROI page only after the region is confirmed. */
    fun canLeaveRoi(roiConfirmed: Boolean): Boolean = roiConfirmed

    /** 开始分析 is offered only on the settings page, after ROI confirmation and valid settings. */
    fun showStart(page: Int, roiConfirmed: Boolean, validSettings: Boolean): Boolean =
        page == PAGE_SETTINGS && roiConfirmed && validSettings

    /** Settings page without a confirmed region: send the user back to step 2 instead of starting. */
    fun mustReturnToRoi(page: Int, roiConfirmed: Boolean): Boolean =
        page == PAGE_SETTINGS && !roiConfirmed
}
