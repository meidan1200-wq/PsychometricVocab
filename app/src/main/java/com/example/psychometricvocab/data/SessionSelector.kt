package com.example.psychometricvocab.data

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Picks the words for one quiz/practice session. Pure Kotlin: the clock and the Random are
 * injected, so the whole thing is unit-tested deterministically.
 *
 * Why it exists: ~2000 words per track start out brand-new, and the old picker gave every new
 * word a weight of 2, so a session was mostly random new words and nothing got memorised. Here a
 * word the user practised recently comes back with HIGH probability, and that probability falls
 * as they answer it correctly; brand-new words are capped at 30% of a session and introduced
 * progressively (unit by unit) instead of scattered.
 *
 * Per practised word:
 *   a      = (correct + 1) / (correct + wrong + 2)            accuracy, smoothed
 *   m      = a, raised to at least 0.85 if isKnown            mastery
 *   recent = exp(-hoursSince(updatedAt) / 48)                 0 if updatedAt == 0
 *   weight = (0.1 + (1 - m)) * (1 + 3 * recent * (1 - m)) * (dueMultiplier if due else 1)
 * A word is "due" if it has been practised and nextReviewDate <= now.
 * Sampling is weighted, without replacement (Efraimidis-Spirakis: key = u^(1/w), top N).
 */
object SessionSelector {
    /** At most this share of a session may be brand-new words (rounded up). */
    const val NEW_WORD_FRACTION = 0.30
    private const val RECENCY_HALF_SCALE_HOURS = 48.0
    private const val MS_PER_HOUR = 3_600_000.0

    /** Weight multiplier for a due word: quizzes/shortcuts use 2 (= 1 + due), Practice favours due words more. */
    const val DUE_MULTIPLIER_QUIZ = 2.0
    const val DUE_MULTIPLIER_PRACTICE = 3.0

    /** Never answered, never marked known, no score: still a brand-new word. */
    fun isNew(w: Word): Boolean =
        w.correctCount == 0 && w.wrongCount == 0 && !w.isKnown && w.srsScore == 0f

    fun mastery(w: Word): Double {
        val a = (w.correctCount + 1.0) / (w.correctCount + w.wrongCount + 2.0)
        return if (w.isKnown) max(a, 0.85) else a
    }

    fun recency(w: Word, now: Long): Double {
        if (w.updatedAt <= 0L) return 0.0
        val hours = max(0L, now - w.updatedAt) / MS_PER_HOUR
        return exp(-hours / RECENCY_HALF_SCALE_HOURS)
    }

    fun isDue(w: Word, now: Long): Boolean = !isNew(w) && w.nextReviewDate <= now

    /** The sampling weight of a PRACTISED word (always > 0). */
    fun weight(w: Word, now: Long, dueMultiplier: Double = DUE_MULTIPLIER_QUIZ): Double {
        val m = mastery(w)
        val recent = recency(w, now)
        val due = if (isDue(w, now)) dueMultiplier else 1.0
        return (0.1 + (1.0 - m)) * (1.0 + 3.0 * recent * (1.0 - m)) * due
    }

    fun select(
        words: List<Word>,
        count: Int,
        now: Long,
        random: Random,
        dueMultiplier: Double = DUE_MULTIPLIER_QUIZ
    ): List<Word> {
        if (count <= 0 || words.isEmpty()) return emptyList()
        // A pool no bigger than the session: everything, once, in random order.
        if (words.size <= count) return words.shuffled(random)

        val fresh = words.filter { isNew(it) }
        val practised = words.filter { !isNew(it) }

        val maxNew = ceil(count * NEW_WORD_FRACTION).toInt()
        var newQuota = min(maxNew, fresh.size)
        val practisedQuota = min(practised.size, count - newQuota)
        // Not enough practised words to fill the rest: top up with more new ones.
        newQuota = min(fresh.size, count - practisedQuota)

        val chosen = sampleWeighted(practised, practisedQuota, now, random, dueMultiplier) +
            pickNewInOrder(fresh, newQuota, random)
        // Shuffle so the new words aren't always the last questions.
        return chosen.shuffled(random)
    }

    /** Weighted sampling without replacement (Efraimidis-Spirakis), top [n] keys. */
    private fun sampleWeighted(
        pool: List<Word>, n: Int, now: Long, random: Random, dueMultiplier: Double
    ): List<Word> {
        if (n <= 0) return emptyList()
        return pool
            .map { w ->
                // key = u^(1/weight); ln(u)/weight orders identically and doesn't underflow.
                val u = max(random.nextDouble(), 1e-12)
                w to ln(u) / weight(w, now, dueMultiplier)
            }
            .sortedByDescending { it.second }
            .take(n)
            .map { it.first }
    }

    /**
     * New words come in asset/unit order with only light randomness: choose [n] from a window of
     * the first ~3n new words (sorted by unit, then id), so a unit's new words are introduced
     * progressively rather than scattered across the whole track.
     */
    private fun pickNewInOrder(fresh: List<Word>, n: Int, random: Random): List<Word> {
        if (n <= 0) return emptyList()
        val ordered = fresh.sortedWith(compareBy<Word>({ it.unit }, { it.id }))
        return ordered.take(max(n, min(ordered.size, n * 3))).shuffled(random).take(n)
    }
}
