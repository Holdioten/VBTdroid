package com.vbtdroid.app.data

import android.content.Context

/**
 * Whether re-analysing the same video and inputs should replace the previous result instead of
 * adding another row to history. Off by default: keeping every run is the safer behaviour.
 */
object AnalysisRetentionPreference {
    private const val PREFS = "vbt_prefs"
    private const val KEY = "keep_latest_analysis_only"

    fun load(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun save(context: Context, keepLatestOnly: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY, keepLatestOnly)
            .apply()
    }
}
