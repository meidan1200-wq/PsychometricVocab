package com.example.psychometricvocab.data

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Picks the words for one quiz/practice session. Pure Kotlin: the clock and the Random are
 * injected, so the whole thing is unit-tested and simulated deterministically.
 *
 * The owner's model: ONE combined, continuous score per word, sampled by probability. No
 * if/else tie-breaks between "kinds" of word (the earlier carry-over quota was exactly that and
 * kept needing patches).
 *
 *   weight = E * D * U * C
 *
 *   E  exposure / recency (practised words): 1 + 300*exp(-h/6) + 2*exp(-h/96), h = hours since
 *      the word was last answered. A very strong pull from the last session (the pool holds
 *      hundreds of other words, so a pull of a few units never made a word come back: simulated
 *      overlap with the previous session was 11-17%), a mild one from the last few days, and 1.0
 *      (no pull) for words not seen for weeks. Persisted already (updatedAt), so it survives
 *      restarts and syncs; no schema change.
 *   D  difficulty from the success rate: a = (correct+1)/(correct+wrong+2), D = 0.15 + (1 - a),
 *      times 1.5 if the LAST answer was wrong. isKnown never excludes such a word: in quiz mode
 *      a wrong answer leaves isKnown = true, and those are exactly the words to bring back.
 *   U  unit importance, ONLY in "all words"/Practice (master) mode, because lower units are
 *      more likely on the real test: U = 1/(1 + 0.1*(unit-1)) (unit 1 = 1.0, unit 10 ~ 0.53).
 *      A soft nudge, never a hard rule. In a single-unit quiz U = 1.
 *   C  cooldown: a word answered RIGHT in its last two answers, the last one within 24 h,
 *      is x0.3 so a word the user just nailed twice isn't served again right away.
 *
 * Sampling is weighted, without replacement (Efraimidis-Spirakis: key = u^(1/w), top N), and a
 * session is built from shares so repetition can't crowd out spaced review:
 *   - 20-30% brand-new (never answered) words: in unit order within a unit, U-weighted across
 *     units in master mode;
 *   - ~25% (Practice ~40%) spaced review: words DUE by SRS (nextReviewDate passed, not answered in
 *     the last 24 h), drawn by D*U;
 *   - the rest by the full E*D*U*C weight, with the whole session holding at most ~35% words
 *     answered in the last 48 h.
 *
 * All the constants live in [Config] so the simulation (SelectionSimulationTest) can show what
 * they do; the defaults are the tuned values.
 */
object SessionSelector {
    data class Config(
        /** New words per session: between these shares of the session (rounded up). */
        val newFractionMin: Double = 0.20,
        val newFractionMax: Double = 0.30,
        /** At most this share of a session may be words answered in the last 48 h (rounded up). */
        val recentMaxFraction: Double = 0.35,
        val recentWindowHours: Double = 48.0,
        /** E = 1 + shortBoost*exp(-h/shortHours) + longBoost*exp(-h/longHours) */
        val shortBoost: Double = 300.0,
        val shortHours: Double = 6.0,
        val longBoost: Double = 2.0,
        val longHours: Double = 96.0,
        /** D = floor + (1 - accuracy), x lastWrongFactor if the last answer was wrong. */
        val difficultyFloor: Double = 0.15,
        val lastWrongFactor: Double = 1.5,
        /** U = 1 / (1 + unitSlope * (unit - 1)), master mode only. */
        val unitSlope: Double = 0.1,
        /** C = cooldownFactor for a word right twice in a row, the last answer within cooldownHours. */
        val cooldownFactor: Double = 0.3,
        val cooldownHours: Double = 24.0,
        /**
         * Spaced review: this share of a session is reserved for words that are DUE by SRS
         * (nextReviewDate passed, practised, not answered in the last [dueMinHours]), drawn by
         * D*U. Practice uses [practiceDueFraction].
         */
        val dueFraction: Double = 0.25,
        val practiceDueFraction: Double = 0.40,
        val dueMinHours: Double = 24.0,
        /**
         * Whether due words answered in the last 48 h (the missed-yesterday ones) use up slots of
         * the recent cap. true keeps the plain rule "at most 35% of a session from the last 48 h";
         * false gives the recency draw the whole cap back (more overlap with the previous session,
         * less retention: see the trade-off table in HANDOFF).
         */
        val dueCountsTowardRecentCap: Boolean = true
    )

    private const val MS_PER_HOUR = 3_600_000.0

    /** The Config Practice uses: same split, bigger spaced-review share. */
    fun practiceConfig(base: Config = Config()): Config = base.copy(dueFraction = base.practiceDueFraction)

    /** Never answered, never marked known, no score: still a brand-new word. */
    fun isNew(w: Word): Boolean =
        w.correctCount == 0 && w.wrongCount == 0 && !w.isKnown && w.srsScore == 0f

    /** Smoothed success rate in (0, 1): (correct + 1) / (correct + wrong + 2). */
    fun accuracy(w: Word): Double = (w.correctCount + 1.0) / (w.correctCount + w.wrongCount + 2.0)

    fun hoursSince(w: Word, now: Long): Double = max(0L, now - w.updatedAt) / MS_PER_HOUR

    /**
     * True if the user's LAST answer to this word was wrong. There is no "last answer" column (and
     * no schema change for one), but SrsEngine.processAnswer leaves a trace we can rely on: a wrong
     * answer ALWAYS sets interval = 1, while any correct answer sets interval >= 6 (1 -> 6, then
     * x ease, which only grows; the default for a never-answered word is also 1). So after at least
     * one answer, interval == 1 means the last answer was wrong. updatedAt > 0 keeps out progress
     * that predates the v5 timestamp (we can't date it). SessionSelectorTest ties this to
     * SrsEngine.processAnswer, so a future change there breaks that test instead of silently
     * breaking this.
     */
    fun lastAnswerWrong(w: Word): Boolean =
        w.updatedAt > 0L && w.interval == 1 && (w.correctCount + w.wrongCount) > 0

    /**
     * True if the last TWO answers were right (interval >= 7: 1 -> 6 after the first right answer,
     * then x ease after the second) and the last one was within [Config.cooldownHours]. The exact
     * "within 24 h" of the earlier answer isn't stored, so only the latest answer's age is checked.
     */
    fun answeredRightTwiceRecently(w: Word, now: Long, config: Config = Config()): Boolean =
        w.updatedAt > 0L && w.interval >= 7 && hoursSince(w, now) <= config.cooldownHours

    /** E: exposure / recency. 1.0 for a word with no timestamp (nothing to date it by). */
    fun exposure(w: Word, now: Long, config: Config = Config()): Double {
        if (w.updatedAt <= 0L) return 1.0
        val h = hoursSince(w, now)
        return 1.0 + config.shortBoost * exp(-h / config.shortHours) + config.longBoost * exp(-h / config.longHours)
    }

    /** D: difficulty from the success rate, raised if the last answer was wrong. */
    fun difficulty(w: Word, config: Config = Config()): Double {
        val d = config.difficultyFloor + (1.0 - accuracy(w))
        return if (lastAnswerWrong(w)) d * config.lastWrongFactor else d
    }

    /** U: unit importance. 1.0 outside master mode. */
    fun unitImportance(w: Word, masterMode: Boolean, config: Config = Config()): Double =
        if (masterMode) 1.0 / (1.0 + config.unitSlope * (w.unit - 1)) else 1.0

    /** The combined sampling weight of a PRACTISED word (always > 0). */
    fun weight(w: Word, now: Long, masterMode: Boolean = false, config: Config = Config()): Double {
        val cooldown = if (answeredRightTwiceRecently(w, now, config)) config.cooldownFactor else 1.0
        return exposure(w, now, config) * difficulty(w, config) * unitImportance(w, masterMode, config) * cooldown
    }

    /** "רק מילים שלא ידעתי": not known yet, or known but just answered wrong. */
    fun unknownOnlyPool(words: List<Word>): List<Word> = words.filter { !it.isKnown || lastAnswerWrong(it) }

    /** Due for spaced review: practised, SRS says it's time, and not just answered. */
    fun isDue(w: Word, now: Long, config: Config = Config()): Boolean =
        !isNew(w) && w.nextReviewDate <= now && (w.updatedAt <= 0L || hoursSince(w, now) >= config.dueMinHours)

    fun isRecent(w: Word, now: Long, config: Config = Config()): Boolean =
        !isNew(w) && w.updatedAt > 0L && hoursSince(w, now) <= config.recentWindowHours

    /** What a selection looked like, for the on-device log line. */
    data class Summary(
        val pool: Int, val recentInPool: Int, val recentTaken: Int, val newTaken: Int, val taken: Int,
        val dueInPool: Int = 0, val dueTaken: Int = 0
    )

    fun summarize(pool: List<Word>, selected: List<Word>, now: Long, config: Config = Config()): Summary =
        Summary(
            pool = pool.size,
            recentInPool = pool.count { isRecent(it, now, config) },
            recentTaken = selected.count { isRecent(it, now, config) },
            newTaken = selected.count { isNew(it) },
            taken = selected.size,
            dueInPool = pool.count { isDue(it, now, config) },
            dueTaken = selected.count { isDue(it, now, config) }
        )

    fun select(
        words: List<Word>,
        count: Int,
        now: Long,
        random: Random,
        masterMode: Boolean = false,
        config: Config = Config()
    ): List<Word> {
        if (count <= 0 || words.isEmpty()) return emptyList()
        // A pool no bigger than the session: everything, once, in random order.
        if (words.size <= count) return words.shuffled(random)

        val fresh = words.filter { isNew(it) }
        val practised = words.filter { !isNew(it) }

        // 20-30% brand-new words (a random share in that range per session), topped up with
        // more new words only if there aren't enough practised ones.
        val newMin = ceil(count * config.newFractionMin).toInt()
        val newMax = max(newMin, ceil(count * config.newFractionMax).toInt())
        var newQuota = min(fresh.size, newMin + random.nextInt(newMax - newMin + 1))
        val practisedQuota = min(practised.size, count - newQuota)
        newQuota = min(fresh.size, count - practisedQuota)

        // Spaced review first: the due share is drawn by D*U among the due words; if there aren't
        // that many due, the remaining practised slots go to the recency-weighted draw.
        val dueQuota = min(practisedQuota, randomRound(count * config.dueFraction, random))
        val due = sampleWeighted(practised.filter { isDue(it, now, config) }, dueQuota, random) {
            difficulty(it, config) * unitImportance(it, masterMode, config)
        }
        val dueIds = due.mapTo(HashSet()) { it.id }

        val fromE = sampleCapped(
            practised.filter { it.id !in dueIds }, practisedQuota - due.size, count,
            if (config.dueCountsTowardRecentCap) due.count { isRecent(it, now, config) } else 0,
            now, random, masterMode, config
        )
        val fromNew = pickNew(fresh, newQuota, random, masterMode, config)
        // Shuffle so the new words aren't always the last questions.
        return (due + fromE + fromNew).shuffled(random)
    }

    /** 2.5 -> 2 or 3 with equal chance, so the average share is exactly the configured one. */
    private fun randomRound(x: Double, random: Random): Int {
        val whole = x.toInt()
        return whole + if (random.nextDouble() < x - whole) 1 else 0
    }

    /** Weighted sampling without replacement (Efraimidis-Spirakis: key = u^(1/w), top n). */
    private fun sampleWeighted(pool: List<Word>, n: Int, random: Random, weight: (Word) -> Double): List<Word> {
        if (n <= 0 || pool.isEmpty()) return emptyList()
        // ln(u)/w orders identically to u^(1/w) and doesn't underflow.
        return pool
            .map { w -> w to ln(max(random.nextDouble(), 1e-12)) / weight(w) }
            .sortedByDescending { it.second }
            .take(n)
            .map { it.first }
    }

    /**
     * The recency-weighted draw (E*D*U*C), keeping the whole session at no more than
     * ceil(35% of the session) words answered in the last 48 h ([recentAlready] of those slots were
     * taken by the due words); if the pool has too few other words to fill the slots, the cap gives
     * way rather than returning a short session.
     */
    private fun sampleCapped(
        pool: List<Word>, n: Int, sessionSize: Int, recentAlready: Int,
        now: Long, random: Random, masterMode: Boolean, config: Config
    ): List<Word> {
        if (n <= 0) return emptyList()
        val ranked = pool
            .map { w -> w to ln(max(random.nextDouble(), 1e-12)) / weight(w, now, masterMode, config) }
            .sortedByDescending { it.second }
            .map { it.first }

        val cap = max(0, ceil(sessionSize * config.recentMaxFraction).toInt() - recentAlready)
        val picked = ArrayList<Word>(n)
        val skipped = ArrayList<Word>()
        var recent = 0
        for (w in ranked) {
            if (picked.size >= n) break
            if (isRecent(w, now, config)) {
                if (recent >= cap) { skipped.add(w); continue }
                recent++
            }
            picked.add(w)
        }
        for (w in skipped) { // not enough non-recent words: relax the cap
            if (picked.size >= n) break
            picked.add(w)
        }
        return picked
    }

    /**
     * Never-seen words. In a single unit they come in unit/id order with only light randomness:
     * [n] from a window of the first ~3n new words, so a unit is introduced progressively. In master
     * mode ("All words", Practice) they are drawn from every unit's unseen words weighted by unit
     * importance: unit 1 is likelier, but every unit is represented.
     */
    private fun pickNew(
        fresh: List<Word>, n: Int, random: Random, masterMode: Boolean, config: Config
    ): List<Word> {
        if (n <= 0) return emptyList()
        if (masterMode) return sampleWeighted(fresh, n, random) { unitImportance(it, true, config) }
        val ordered = fresh.sortedWith(compareBy<Word>({ it.unit }, { it.id }))
        return ordered.take(max(n, min(ordered.size, n * 3))).shuffled(random).take(n)
    }
}
