package com.example.psychometricvocab.data

import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Records how many quiz/flashcard answers happened per calendar day, for Home's daily-activity
 * chart. Nothing else stores per-day history — `Word` has no timestamps — so this is its own
 * small log: plain SharedPreferences, date string -> answer count, pruned to the last ~30 days
 * so it never grows without bound (the chart itself only ever shows the last 7). Cleared on
 * account removal, like the other prefs stores.
 */
class ActivityLog(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Called from VocabRepository.processAnswer — the one funnel every answer goes through. */
    fun recordAnswer() {
        val today = todayKey()
        val current = prefs.getInt(today, 0)
        prefs.edit().putInt(today, current + 1).apply()
        pruneOldEntries()
    }

    /** Oldest to newest (today last), always exactly 7 entries, 0 for days with no activity. */
    fun getLast7Days(): List<Pair<LocalDate, Int>> {
        val today = LocalDate.now()
        return (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            date to prefs.getInt(date.format(FORMATTER), 0)
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun pruneOldEntries() {
        val cutoff = LocalDate.now().minusDays(RETAIN_DAYS)
        val stale = prefs.all.keys.filter { key ->
            runCatching { LocalDate.parse(key, FORMATTER).isBefore(cutoff) }.getOrDefault(false)
        }
        if (stale.isNotEmpty()) {
            val editor = prefs.edit()
            stale.forEach { editor.remove(it) }
            editor.apply()
        }
    }

    private fun todayKey(): String = LocalDate.now().format(FORMATTER)

    companion object {
        private const val PREFS_NAME = "activity_log"
        private const val RETAIN_DAYS = 30L
        private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
