package com.example.psychometricvocab.data

import kotlin.math.roundToInt

/**
 * How long a personal Practice session should be: it adapts to the user, so someone who answers
 * 20 words a day gets short sessions and someone who answers 60 gets longer ones.
 * Pure functions (no Android), unit-tested.
 */
object PracticeSession {
    const val MIN_SIZE = 15
    const val MAX_SIZE = 40
    const val DEFAULT_SIZE = 15
    private const val ACTIVE_DAYS = 7
    private const val SHARE_OF_DAILY_MEDIAN = 0.6

    /**
     * The answers-per-day of the [maxDays] most recent ACTIVE days (days with at least one
     * answer), given the stored "ISO date -> count" map. Inactive days are skipped, not counted
     * as zero, so a weekend off doesn't shrink the next session.
     */
    fun recentActiveCounts(byDate: Map<String, Int>, maxDays: Int = ACTIVE_DAYS): List<Int> =
        byDate.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.key } // ISO dates sort chronologically as text
            .take(maxDays)
            .map { it.value }

    /** median(answers/day over the recent active days) x 0.6, clamped to 15..40; 15 with no history. */
    fun size(recentActiveCounts: List<Int>): Int {
        if (recentActiveCounts.isEmpty()) return DEFAULT_SIZE
        val sorted = recentActiveCounts.sorted()
        val mid = sorted.size / 2
        val median = if (sorted.size % 2 == 1) sorted[mid].toDouble() else (sorted[mid - 1] + sorted[mid]) / 2.0
        return (median * SHARE_OF_DAILY_MEDIAN).roundToInt().coerceIn(MIN_SIZE, MAX_SIZE)
    }
}
