package com.vbtdroid.app.analysis

/**
 * Pure policy for the merged analysis + overlay job: one user-facing progress range and the
 * overlay cache file lifecycle. Kept Android-free so the mapping and naming can be unit tested.
 */
object AnalysisOverlayJob {
    /** Analysis fills 0..0.75 of the notification progress. */
    const val ANALYSIS_SHARE = 0.75f

    /** Overlay export fills 0.75..1.0. */
    const val OVERLAY_SHARE = 1.0f - ANALYSIS_SHARE

    /** Quality warning recorded when the overlay could not be produced. Analysis still succeeds. */
    const val OVERLAY_FAILED_WARNING = "OVERLAY_EXPORT_FAILED"

    /** Number of overlay MP4s kept in cache; older files are deleted after each export. */
    const val CACHE_FILES_TO_KEEP = 10

    fun analysisProgress(raw: Float): Float = raw.coerceIn(0f, 1f) * ANALYSIS_SHARE

    fun overlayProgress(raw: Float): Float =
        ANALYSIS_SHARE + raw.coerceIn(0f, 1f) * OVERLAY_SHARE

    /** `cacheDir/overlay-<createdAt>.mp4`, as requested by the report/export contract. */
    fun overlayFileName(createdAtEpochMs: Long): String = "overlay-$createdAtEpochMs.mp4"

    /**
     * Given cache names sorted newest first, returns the ones outside the newest [keep] entries.
     * The current export is always the newest file, so it is never in the returned list.
     */
    fun staleOverlayFiles(namesNewestFirst: List<String>, keep: Int = CACHE_FILES_TO_KEEP): List<String> =
        namesNewestFirst.drop(keep.coerceAtLeast(1))
}
