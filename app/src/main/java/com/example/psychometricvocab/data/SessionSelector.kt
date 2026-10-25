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
 *   weight = (0.1 + (1 - m)) * (1 + 6 * recent * (1 - m)) * (dueMultiplier if due else 1)
 * A word is "due" if it has been practised and nextReviewDate <= now.
 * Sampling is weighted, without replacement (Efraimidis-Spirakis: key = u^(1/w), top N).
 *
 * The weights alone were too weak for the owner's real data: a unit can already hold ~80
 * wrong-not-yet-known words, and a recency factor of at most 2.5x can't make the words from the
 * quiz he just did win a draw against 80 of those (3 consecutive quizzes overlapped 0-2 words).
 * So a session is built in three steps:
 *   1. CARRY-OVER: up to 50% of the session is reserved for words practised in the last 48 h
 *      whose LAST answer was wrong (even if the word is known: see lastAnswerWrong) or that are
 *      not yet mastered (m < 0.9 and not known), drawn by weight
 *      (3 if last answer wrong) * max(1 - m, 0.15) * exp(-hours/3), so the LAST session's
 *      words dominate and recent misses come first. A word leaves carry-over by itself once it
 *      is answered right (and is known or at accuracy 0.9), so the chance of seeing it falls as
 *      the user answers it correctly. (Recency scales tried against a heavy user's day, where ~60 words practised
 *      20 h ago are still inside the window: exp(-h/48) can't tell "10 minutes ago" from "20
 *      hours ago" (1.0 vs 0.66) and gave ~1 word of overlap between consecutive quizzes, the
 *      IT finding; exp(-h/6) gave ~3.8; exp(-h/3) gives ~4.9 and never fewer than 3.)
 *   2. Then at most 30% brand-new words.
 *   3. Then the rest from the remaining practised words by the weights above.
 */
object SessionSelector {
    /** At most this share of a session may be brand-new words (rounded up). */
    const val NEW_WORD_FRACTION = 0.30

    /** Up to this share of a session is reserved for carry-over words (rounded up). */
    const val CARRY_OVER_FRACTION = 0.50

    /** A word stops being carry-over once its mastery reaches this (or it becomes known). */
    const val CARRY_OVER_MASTERY_LIMIT = 0.9
    private const val CARRY_OVER_WINDOW_HOURS = 48.0

    /**
     * Carry-over weight decays with this time constant: 10 min ~0.95, 3 h ~0.37, 6 h ~0.14,
     * 24 h ~0.0003. Short on purpose: the previous session's words must dominate the quota
     * even when a day of older unmastered words is still inside the 48 h window.
     */
    private const val CARRY_OVER_RECENCY_HOURS = 3.0

    /** How strongly recency lifts the weight of a not-yet-mastered word. */
    private const val RECENCY_BOOST = 6.0
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

    /**
     * True if the user's LAST answer to this word was wrong. There is no "last answer" column (and
     * no schema change for one), but SrsEngine.processAnswer leaves a trace we can rely on: a wrong
     * answer ALWAYS sets interval = 1, while any correct answer sets interval >= 6 (1 -> 6, then
     * x ease, which only grows; the default for a never-answered word is also 1). So after at least
     * one answer, interval == 1 means the last answer was wrong. updatedAt > 0 keeps out progress
     * that predates the v5 timestamp (we can't date it). SessionSelectorTest ties this to
     * SrsEngine.processAnswer, so a future change there breaks that test instead of silently
     * breaking this.
     *
     * Why it matters: in QUIZ mode a wrong answer leaves isKnown = true, and the words the owner
     * gets wrong are mostly known words with long correct histories (accuracy >= 0.9). Filtering
     * carry-over on "not known / not mastered" threw out exactly the words he wants back.
     */
    fun lastAnswerWrong(w: Word): Boolean =
        w.updatedAt > 0L && w.interval == 1 && (w.correctCount + w.wrongCount) > 0

    /**
     * Practised in the last 48 h, and either the last answer was wrong (even for a known word) or
     * it is not yet mastered (not known and m < 0.9): the words to keep bringing back.
     */
    fun isCarryOver(w: Word, now: Long): Boolean {
        if (isNew(w) || w.updatedAt <= 0L) return false
        if ((now - w.updatedAt) / MS_PER_HOUR > CARRY_OVER_WINDOW_HOURS) return false
        return lastAnswerWrong(w) || (!w.isKnown && mastery(w) < CARRY_OVER_MASTERY_LIMIT)
    }

    /**
     * Carry-over draw weight: (3 if the last answer was wrong, else 1) * max(1 - m, 0.15) *
     * exp(-hours/3). The 0.15 floor keeps a just-missed word with a long good history (m >= 0.85)
     * in the running; > 0 for any carry-over candidate.
     */
    fun carryOverWeight(w: Word, now: Long): Double {
        val hours = max(0L, now - w.updatedAt) / MS_PER_HOUR
        val missed = if (lastAnswerWrong(w)) 3.0 else 1.0
        return missed * max(1.0 - mastery(w), 0.15) * exp(-hours / CARRY_OVER_RECENCY_HOURS)
    }

    /** The sampling weight of a PRACTISED word (always > 0). */
    fun weight(w: Word, now: Long, dueMultiplier: Double = DUE_MULTIPLIER_QUIZ): Double {
        val m = mastery(w)
        val recent = recency(w, now)
        val due = if (isDue(w, now)) dueMultiplier else 1.0
        return (0.1 + (1.0 - m)) * (1.0 + RECENCY_BOOST * recent * (1.0 - m)) * due
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

        // 1. carry-over quota, by weight (1 - m) * exp(-hours/3) (> 0 for a candidate)
        val carryCandidates = practised.filter { isCarryOver(it, now) }
        val carryQuota = min(ceil(count * CARRY_OVER_FRACTION).toInt(), carryCandidates.size)
        val carry = sampleBy(carryCandidates, carryQuota, random) { carryOverWeight(it, now) }
        val carryIds = carry.map { it.id }.toHashSet()

        // 2. at most 30% brand-new words; 3. the rest from the remaining practised words
        val remaining = count - carry.size
        val maxNew = ceil(count * NEW_WORD_FRACTION).toInt()
        var newQuota = min(min(maxNew, fresh.size), remaining)
        val restPool = practised.filter { it.id !in carryIds }
        val restQuota = min(restPool.size, remaining - newQuota)
        // Not enough practised words to fill the rest: top up with more new ones.
        newQuota = min(fresh.size, remaining - restQuota)

        val rest = sampleBy(restPool, restQuota, random) { weight(it, now, dueMultiplier) }
        val chosen = carry + rest + pickNewInOrder(fresh, newQuota, random)
        // Shuffle so the new words and the carry-over aren't in a fixed order.
        return chosen.shuffled(random)
    }

    /** Weighted sampling without replacement (Efraimidis-Spirakis), top [n] keys. */
    private fun sampleBy(pool: List<Word>, n: Int, random: Random, weightOf: (Word) -> Double): List<Word> {
        if (n <= 0) return emptyList()
        return pool
            .map { w ->
                // key = u^(1/weight); ln(u)/weight orders identically and doesn't underflow.
                val u = max(random.nextDouble(), 1e-12)
                w to ln(u) / weightOf(w)
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
