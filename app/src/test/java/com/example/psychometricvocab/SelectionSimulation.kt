package com.example.psychometricvocab

import com.example.psychometricvocab.data.SessionSelector
import com.example.psychometricvocab.data.SrsEngine
import com.example.psychometricvocab.data.Word
import kotlin.math.exp
import kotlin.random.Random

/**
 * A synthetic learner and a calendar, to watch what SessionSelector does over weeks instead of
 * guessing from one session. Pure JVM; the clock, both Randoms and the Config are injected.
 *
 * The learner: the standard stability model of forgetting. A word seen once has a stability S
 * (hours); recall after t hours is e^(-t/S) and P(correct) = chance + (ceiling - chance) * recall.
 * A correct answer multiplies S by 1.5 + 2 * (1 - recall): even a repeat right after the last one
 * helps (x1.5), but reviewing a half-forgotten word helps far more (up to x3.5: the spacing
 * effect). A wrong answer cuts
 * it to 60% (floor 24 h). A first look at a word gives S = 72 h x its learnability (0.5..1.5):
 * this is multiple-choice RECOGNITION, which is remembered much longer than free recall. Words differ in how learnable they are. When the learner is wrong, some
 * of the time they press "No idea".
 * Answers go through the real SrsEngine, so the Word fields the selector reads (interval, counts,
 * isKnown, srsScore) evolve exactly as in the app.
 */
object SelectionSimulation {
    const val HOUR = 3_600_000L
    const val DAY = 24 * HOUR
    const val START = 1_800_000_000_000L

    data class Scenario(
        val name: String,
        val words: List<Word>,
        val masterMode: Boolean,
        val days: Int = 30,
        val sessionsPerDay: Int = 3,
        val sessionSize: Int = 10,
        /** Share of words the learner has already practised when the run starts (0 = cold start). */
        val warmShare: Double = 0.0
    )

    data class Result(
        val scenario: Scenario,
        val seed: Int,
        /** Mean share of a session that was also in the previous session. */
        val overlap: Double,
        /** Same, from day 8 on (after the pool has built up). */
        val overlapSteady: Double,
        /** New words per session, over the sessions where there were enough of both kinds. */
        val newMin: Int, val newMax: Int, val newMean: Double, val newSessions: Int,
        /**
         * Retention of the words seen during week 1 (the cohort): the mean true P(correct) if they
         * were asked a week after the end of weeks 1..4 (no further practice). Rises as practice
         * builds durable memory; this is the "P(correct) on practised words rises" band.
         */
        val pRetainByWeek: List<Double>,
        /** The same cohort asked right at the end of weeks 1..4 (it also feels the forgetting between quizzes). */
        val pCohortByWeek: List<Double>,
        /** Mean true P(correct) at the moment a previously-seen word was served, weeks 1..4. */
        val pServedByWeek: List<Double>,
        /** Share of answers that were right, per week, over words that had been seen before. */
        val rightRateByWeek: List<Double>,
        /** Share of all served words per unit. */
        val unitShare: Map<Int, Double>,
        /** The most times one word was asked on one day, and how many (word, day)s broke the rule. */
        val maxPerDay: Int, val perDayViolations: Int,
        /** Distinct words seen by the end of each day. */
        val coverageByDay: List<Int>
    ) {
        val unit1ToUnit10: Double
            get() {
                val u10 = unitShare[10] ?: 0.0
                return if (u10 == 0.0) Double.POSITIVE_INFINITY else (unitShare[1] ?: 0.0) / u10
            }
    }

    private class Learner(private val rng: Random, words: List<Word>) {
        private val stability = HashMap<Int, Double>()
        private val lastAt = HashMap<Int, Long>()
        private val learnability = words.associate { it.id to 0.5 + rng.nextDouble() }

        private fun recall(id: Int, now: Long): Double {
            val s = stability[id] ?: return 0.0
            return exp(-((now - (lastAt[id] ?: now)).toDouble() / HOUR) / s)
        }

        fun pCorrect(id: Int, now: Long): Double = CHANCE + (CEILING - CHANCE) * recall(id, now)

        /** Returns (isCorrect, pressedNoIdea). */
        fun answer(id: Int, now: Long): Pair<Boolean, Boolean> {
            val r = recall(id, now)
            val correct = rng.nextDouble() < CHANCE + (CEILING - CHANCE) * r
            val noIdea = !correct && rng.nextDouble() < NO_IDEA_WHEN_WRONG
            val s = stability[id]
            stability[id] = when {
                s == null -> BASE_STABILITY_HOURS * learnability.getValue(id) // first exposure shows the answer
                correct -> s * (MASSED_GAIN + 2.0 * (1 - r))
                else -> maxOf(MIN_STABILITY_HOURS, s * 0.6)
            }
            lastAt[id] = now
            return correct to noIdea
        }

        fun seen(id: Int) = stability.containsKey(id)
    }

    private const val CHANCE = 0.25
    private const val MASSED_GAIN = 1.5
    private const val CEILING = 0.97
    private const val BASE_STABILITY_HOURS = 72.0
    private const val MIN_STABILITY_HOURS = 24.0
    private const val NO_IDEA_WHEN_WRONG = 0.3

    private fun answerWord(w: Word, learner: Learner, now: Long): Pair<Word, Boolean> {
        val (correct, noIdea) = learner.answer(w.id, now)
        val next = SrsEngine.processAnswer(w, correct, isQuiz = true, isNotSure = noIdea).copy(updatedAt = now)
        return next to correct
    }

    fun run(
        scenario: Scenario,
        seed: Int,
        config: SessionSelector.Config = SessionSelector.Config()
    ): Result {
        val learnerRng = Random(seed * 7919L)
        val selectRng = Random(seed * 104729L)
        val learner = Learner(learnerRng, scenario.words)
        val state = scenario.words.associateBy { it.id }.toMutableMap()

        // A learner who is not starting from zero: some words already practised, 1-4 days to 3
        // weeks ago, with a few answers each (through the same SrsEngine).
        if (scenario.warmShare > 0.0) {
            for (w in scenario.words) {
                if (learnerRng.nextDouble() >= scenario.warmShare) continue
                var cur = w
                var t = START - (1 + learnerRng.nextInt(20)) * DAY
                repeat(1 + learnerRng.nextInt(4)) {
                    cur = answerWord(cur, learner, t).first
                    t += 6 * HOUR
                }
                state[w.id] = cur
            }
        }

        val sessions = ArrayList<List<Word>>()
        val newCounts = ArrayList<Int>()
        val unitCounts = HashMap<Int, Int>()
        val perDay = Array(scenario.days) { HashMap<Int, Pair<Int, Int>>() } // id -> (asked, wrong)
        val weeks = scenario.days / 7
        val answers = Array(weeks) { IntArray(2) }                            // per week: (right, total) on seen words
        val pSum = DoubleArray(weeks)
        val seenIds = HashSet<Int>().also { s -> state.values.filter { learner.seen(it.id) }.forEach { s.add(it.id) } }
        val coverage = ArrayList<Int>()
        var cohort: List<Int> = emptyList()
        val cohortP = ArrayList<Double>()
        val retainP = ArrayList<Double>()
        val hours = if (scenario.sessionsPerDay == 1) listOf(12)
        else (0 until scenario.sessionsPerDay).map { 8 + it * 14 / (scenario.sessionsPerDay - 1) }

        for (day in 0 until scenario.days) {
            for (hour in hours) {
                val now = START + day * DAY + hour * HOUR
                val pool = state.values.toList()
                val freshBefore = pool.count { SessionSelector.isNew(it) }
                val practisedBefore = pool.size - freshBefore
                val picked = SessionSelector.select(pool, scenario.sessionSize, now, selectRng, scenario.masterMode, config)
                val newInSession = picked.count { SessionSelector.isNew(it) }
                if (freshBefore >= 3 && practisedBefore >= 8) newCounts.add(newInSession)
                sessions.add(picked)
                for (w in picked) {
                    unitCounts.merge(w.unit, 1, Int::plus)
                    val seenBefore = learner.seen(w.id)
                    val pBefore = learner.pCorrect(w.id, now)
                    val (next, correct) = answerWord(w, learner, now)
                    state[w.id] = next
                    seenIds.add(w.id)
                    if (seenBefore && day / 7 < weeks) {
                        val a = answers[day / 7]
                        if (correct) a[0]++
                        a[1]++
                        pSum[day / 7] += pBefore
                    }
                    perDay[day].merge(w.id, 1 to (if (correct) 0 else 1)) { o, n -> (o.first + n.first) to (o.second + n.second) }
                }
            }
            coverage.add(seenIds.size)
            if ((day + 1) % 7 == 0 && (day + 1) / 7 <= weeks) {
                if (cohort.isEmpty()) cohort = seenIds.toList()
                val end = START + (day + 1) * DAY
                cohortP.add(cohort.map { learner.pCorrect(it, end) }.average())
                retainP.add(cohort.map { learner.pCorrect(it, end + 7 * DAY) }.average())
            }
        }

        val overlaps = sessions.zipWithNext().map { (a, b) ->
            val ids = a.map { it.id }.toSet()
            b.count { it.id in ids }.toDouble() / scenario.sessionSize
        }
        val steadyFrom = 7 * scenario.sessionsPerDay
        var maxPerDay = 0
        var violations = 0
        for (d in perDay) for ((_, v) in d) {
            val (asked, wrong) = v
            if (asked > maxPerDay) maxPerDay = asked
            if (asked > 4 && wrong < asked) violations++
        }
        val total = unitCounts.values.sum().toDouble()
        return Result(
            scenario = scenario, seed = seed,
            overlap = overlaps.average(),
            overlapSteady = overlaps.drop(steadyFrom - 1).average(),
            newMin = newCounts.minOrNull() ?: 0, newMax = newCounts.maxOrNull() ?: 0,
            newMean = if (newCounts.isEmpty()) 0.0 else newCounts.average(), newSessions = newCounts.size,
            pRetainByWeek = retainP,
            pCohortByWeek = cohortP,
            pServedByWeek = answers.indices.map { pSum[it] / answers[it][1].coerceAtLeast(1) },
            rightRateByWeek = answers.map { it[0].toDouble() / it[1].coerceAtLeast(1) },
            unitShare = unitCounts.mapValues { it.value / total },
            maxPerDay = maxPerDay, perDayViolations = violations,
            coverageByDay = coverage
        )
    }

    /** Single unit: 208 words, ids 1..208, all unit 1. */
    fun singleUnit() = Scenario(
        name = "single unit (208 words)",
        words = (1..208).map { fresh(it, 1) },
        masterMode = false
    )

    /** Master mode: 10 units x 200 words, ids unit*1000 + n. */
    fun masterCold() = Scenario(
        name = "master mode, cold start (10 x 200)",
        words = (1..10).flatMap { u -> (1..200).map { fresh(u * 1000 + it, u) } },
        masterMode = true
    )

    /** Master mode for a learner who already practised ~10% of every unit (about 200 words). */
    fun masterWarm() = Scenario(
        name = "master mode, 10% practised in every unit",
        words = (1..10).flatMap { u -> (1..200).map { fresh(u * 1000 + it, u) } },
        masterMode = true,
        warmShare = 0.1
    )

    /** Master mode for a user two months in: ~40% of every unit practised (about 800 words). Not a band, just the table. */
    fun masterMature() = Scenario(
        name = "master mode, 40% practised in every unit (info only)",
        words = (1..10).flatMap { u -> (1..200).map { fresh(u * 1000 + it, u) } },
        masterMode = true,
        warmShare = 0.4
    )

    /** A heavy week: 8 sessions a day for 7 days in a 208-word unit (info only, the bands assume 3 a day). */
    fun heavyWeek() = Scenario(
        name = "single unit, heavy use: 8 sessions a day for 7 days (info only)",
        words = (1..208).map { fresh(it, 1) },
        masterMode = false,
        days = 7,
        sessionsPerDay = 8
    )

    private fun fresh(id: Int, unit: Int) = Word(id = id, track = "english", word = "w$id", definition = "d$id", unit = unit)

    fun coverageIncrements(r: Result): List<Int> =
        (7..r.scenario.days step 7).map { end -> r.coverageByDay[end - 1] - (if (end == 7) 0 else r.coverageByDay[end - 8]) }

    /**
     * The six bands, as messages (empty = all pass). Shared by the test and the tuning sweep.
     * [cold] is false for the warm master scenario, where the "new words" and "coverage" bands
     * are not what is being looked at (the unit-share band is).
     */
    fun violations(r: Result, config: SessionSelector.Config = SessionSelector.Config()): List<String> {
        val out = ArrayList<String>()
        val sc = r.scenario
        val total = sc.words.size
        // 1. consecutive overlap 30-60%
        if (r.overlap !in 0.30..0.60) out.add("overlap ${pct(r.overlap)} outside 30-60%")
        // 2. 2-3 new words per 10 (scaled) until the pool runs out
        val lo = Math.ceil(sc.sessionSize * config.newFractionMin).toInt()
        val hi = Math.ceil(sc.sessionSize * config.newFractionMax).toInt()
        if (r.warmShare() == 0.0) {
            if (r.newSessions == 0) out.add("no session had enough fresh+practised words to judge the new-word share")
            else if (r.newMin < lo || r.newMax > hi) out.add("new words per session ${r.newMin}..${r.newMax}, expected $lo..$hi")
        }
        // 3. mean P(correct) over practised words rises: judged on the mean of the seeds, see riseViolation
        // 4. master mode: unit 1 share >= 1.5x unit 10 share
        if (sc.masterMode && r.unit1ToUnit10 < 1.5) out.add("unit1/unit10 = ${"%.2f".format(r.unit1ToUnit10)} < 1.5")
        // 5. no word more than 4 times a day unless wrong each time
        if (r.perDayViolations > 0) out.add("${r.perDayViolations} (word, day) pairs asked >4x without being wrong every time")
        // 6. coverage grows steadily
        val cov = r.coverageByDay
        if (cov.zipWithNext().any { (a, b) -> b < a }) out.add("coverage decreased")
        if (r.warmShare() == 0.0) {
            val minPerWeek = (sc.sessionSize * config.newFractionMin * sc.sessionsPerDay * 7 - 7).toInt()
            val incs = coverageIncrements(r)
            incs.forEachIndexed { week, inc ->
                val startCov = if (week == 0) 0 else cov[week * 7 - 1]
                if (startCov <= total - 60 && inc < minPerWeek) out.add("week ${week + 1}: only +$inc new words seen (< $minPerWeek)")
            }
        }
        return out
    }

    /**
     * Band 3, judged on the mean over the seeds (a single seed's retention wobbles by a couple of
     * points). A cold start must show practice building memory: +2 points over the month and no
     * week more than 2 points below the one before. A learner who starts with ~200 practised words
     * spread over a 2000-word pool gets about one review per word a month, so there the band is
     * "no collapse" (the table shows the number).
     */
    fun riseViolation(rs: List<Result>): String? {
        val weeks = rs.minOf { it.pRetainByWeek.size }
        val p = (0 until weeks).map { w -> rs.map { it.pRetainByWeek[w] }.average() }
        val ok = if (rs.first().scenario.warmShare == 0.0)
            weeks >= 4 && p.last() >= p.first() + 0.02 && p.zipWithNext().none { (a, b) -> b < a - 0.02 }
        else weeks >= 4 && p.last() >= p.first() - 0.03
        return if (ok) null else "${rs.first().scenario.name}: P(correct) does not rise: ${p.joinToString { pct(it) }}"
    }

    private fun Result.warmShare() = scenario.warmShare
    private fun pct(x: Double) = "%.1f%%".format(x * 100)

    fun table(results: List<Result>): String {
        val sb = StringBuilder()
        for (group in results.groupBy { it.scenario.name }) {
            val rs = group.value
            fun mean(f: (Result) -> Double) = rs.map(f).average()
            sb.appendLine("== ${group.key}: ${rs.size} seeds, ${rs.first().scenario.days} days x ${rs.first().scenario.sessionsPerDay} sessions x ${rs.first().scenario.sessionSize} words")
            sb.appendLine("  consecutive overlap        mean ${pct(mean { it.overlap })}  (day 8+: ${pct(mean { it.overlapSteady })})  range ${pct(rs.minOf { it.overlap })}..${pct(rs.maxOf { it.overlap })}")
            sb.appendLine("  new words per session      ${rs.minOf { it.newMin }}..${rs.maxOf { it.newMax }}  mean ${"%.2f".format(mean { it.newMean })}  (${rs.first().newSessions} judged sessions)")
            sb.appendLine("  P(correct) in a week, wk-1 words " + (0 until rs.minOf { it.pRetainByWeek.size }).joinToString(" -> ") { i -> pct(mean { it.pRetainByWeek[i] }) } + "  (retention, from the end of weeks 1..4)")
            sb.appendLine("  P(correct) right then, wk-1 words " + (0 until rs.minOf { it.pCohortByWeek.size }).joinToString(" -> ") { i -> pct(mean { it.pCohortByWeek[i] }) } + "  (end of weeks 1..4)")
            sb.appendLine("  P(correct) when served       " + (0 until rs.minOf { it.pServedByWeek.size }).joinToString(" -> ") { i -> pct(mean { it.pServedByWeek[i] }) } + "  (weeks 1..4; answers actually right: " + (0 until rs.minOf { it.rightRateByWeek.size }).joinToString(" -> ") { i -> pct(mean { it.rightRateByWeek[i] }) } + ")")
            if (rs.first().scenario.masterMode) {
                val share = (1..10).joinToString(" ") { u -> "u$u=${pct(mean { it.unitShare[u] ?: 0.0 })}" }
                sb.appendLine("  unit shares                $share")
                sb.appendLine("  unit1 / unit10             " + rs.joinToString { r -> if (r.unit1ToUnit10.isInfinite()) "inf" else "%.2f".format(r.unit1ToUnit10) })
            }
            sb.appendLine("  most asks of one word/day  ${rs.maxOf { it.maxPerDay }}  (violations of the >4x rule: ${rs.sumOf { it.perDayViolations }})")
            val marks = listOf(7, 14, 21, 30).filter { it <= rs.first().scenario.days }
            sb.appendLine("  distinct words seen        " + marks.joinToString("  ") { d -> "day $d: ${rs.map { it.coverageByDay[d - 1] }.average().toInt()}" } + "  of ${rs.first().scenario.words.size}")
        }
        return sb.toString()
    }
}
