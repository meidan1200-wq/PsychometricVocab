package com.example.psychometricvocab.data

import android.content.Context
import kotlin.math.abs

/**
 * Remembers quiz settings the owner picks so Home's unit-card shortcuts can jump straight into
 * a quiz without asking again: the preferred quiz length, and per-unit (and per-track) whether
 * the last quiz for that unit was "all words" or "words I missed". Plain SharedPreferences, not
 * the encrypted account store or the Room DB: this is a UI preference, not personal data or a
 * schema change, and it must survive account removal being handled separately (see clear()).
 */
class QuizPreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Snapped to the nearest of ALLOWED_LENGTHS on both read and write: the length used to be a
    // free 5-15 slider, so a value picked back then (e.g. 8 or 11) matches none of today's three
    // pill options and would otherwise show nothing selected while still quietly being used.
    fun getQuizLength(): Int = snapToAllowed(prefs.getInt(KEY_LENGTH, DEFAULT_LENGTH))

    fun setQuizLength(length: Int) {
        prefs.edit().putInt(KEY_LENGTH, snapToAllowed(length)).apply()
    }

    private fun snapToAllowed(length: Int): Int =
        ALLOWED_LENGTHS.minByOrNull { abs(it - length) } ?: DEFAULT_LENGTH

    /** Null means no saved preference yet; callers should default to "all words". */
    fun getUnknownOnly(track: String, unit: Int?): Boolean? {
        val key = unitKey(track, unit)
        return if (prefs.contains(key)) prefs.getBoolean(key, false) else null
    }

    fun setUnknownOnly(track: String, unit: Int?, unknownOnly: Boolean) {
        prefs.edit().putBoolean(unitKey(track, unit), unknownOnly).apply()
    }

    /** Called from AccountViewModel.removeAccount() alongside the DB progress reset. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun unitKey(track: String, unit: Int?): String = "unknownOnly_${track}_${unit ?: "all"}"

    companion object {
        private const val PREFS_NAME = "quiz_prefs"
        private const val KEY_LENGTH = "quiz_length"
        const val DEFAULT_LENGTH = 10
        const val MIN_LENGTH = 5
        const val MAX_LENGTH = 15
        /** The only lengths selectable in the UI; also what stored values snap to. */
        val ALLOWED_LENGTHS = listOf(MIN_LENGTH, DEFAULT_LENGTH, MAX_LENGTH)
    }
}
