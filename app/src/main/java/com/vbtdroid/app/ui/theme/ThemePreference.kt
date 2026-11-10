package com.vbtdroid.app.ui.theme

import android.content.Context

/** Persists the selected theme across launches. */
object ThemePreference {
    private const val PREFS = "vbt_prefs"
    private const val KEY = "theme_id"

    fun load(context: Context): VbtThemeId {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return VbtThemeId.values().firstOrNull { it.name == raw } ?: VbtThemeId.LIGHT_RED
    }

    fun save(context: Context, id: VbtThemeId) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, id.name)
            .apply()
    }
}
