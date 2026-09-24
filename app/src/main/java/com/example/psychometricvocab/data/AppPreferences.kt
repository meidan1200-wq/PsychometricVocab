package com.example.psychometricvocab.data

import android.content.Context

/**
 * General app preferences (Settings screen), separate from QuizPreferences (which only holds
 * per-unit quiz choices). Meant to grow over time as more Preferences are added.
 * Plain SharedPreferences, no DB change. Cleared on account removal, like QuizPreferences.
 */
class AppPreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** "מעבר אוטומטי" — auto-advance to the next quiz question after answering. Default off. */
    fun isAutoPassEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_PASS, false)

    fun setAutoPassEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PASS, enabled).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_AUTO_PASS = "auto_pass"
    }
}
