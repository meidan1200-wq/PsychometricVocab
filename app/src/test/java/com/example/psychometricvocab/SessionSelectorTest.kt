package com.example.psychometricvocab

import com.example.psychometricvocab.data.SessionSelector
import com.example.psychometricvocab.data.SrsEngine
import com.example.psychometricvocab.data.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.random.Random

/**
 * The learning-v2 picker: one combined weight E*D*U*C per word, sampled by probability, with a
 * 20-30% new-word share and a 48 h cap. Deterministic: the Random is seeded, "now" is fixed.
 * (The long-run behaviour is checked by SelectionSimulationTest.)
 */
class SessionSelectorTest {
    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L
    private val day = 24 * hour

    private fun word(
        id: Int, unit: Int = 1, correct: Int = 0, wrong: Int = 0, known: Boolean = false,
        srs: Float = 0f, updatedAt: Long = 0L, interval: Int = 1
    ) = Word(
        id = id, track = "english", word = "w$id", definition = "d$id", unit = unit,
        srsScore = srs, correctCount = correct, wrongCount = wrong, isKnown = known,
        updatedAt = updatedAt, interval = interval
    )

    /**
     * A practised word. [lastWrong] says how its last answer went, the way SrsEngine records it:
     * a wrong answer leaves interval = 1, a correct one leaves interval >= 6.
     */
    private fun practised(
        id: Int, correct: Int, wrong: Int, hoursAgo: Double, known: Boolean = false,
        lastWrong: Boolean = false, unit: Int = 1, interval: Int = if (lastWrong) 1 else 6
    ) = word(id, unit = unit, correct = correct, wrong = wrong, known = known, srs = 1f,
        updatedAt = now - (hoursAgo * hour).toLong(), interval = interval)

    private fun frequency(pool: List<Word>, count: Int, trials: Int, seed: Int = 7, master: Boolean = false): Map<Int, Int> {
        val random = Random(seed)
        val hits = HashMap<Int, Int>()
        repeat(trials) {
            SessionSelector.select(pool, count, now, random, master).forEach { hits.merge(it.id, 1, Int::plus) }
        }
        return hits
    }

    // ---- the factors ----

    @Test
    fun exposureIsAStrongPullFromTheLastSessionAndAMildOneFromTheLastDays() {
        val c = SessionSelector.Config()
        fun e(hoursAgo: Double) = SessionSelector.exposure(practised(1, 1, 1, hoursAgo), now)
        fun expected(h: Double) = 1 + c.shortBoost * exp(-h / c.shortHours) + c.longBoost * exp(-h / c.longHours)
        for (h in listOf(0.0, 1.0, 6.0, 12.0, 48.0, 200.0)) assertEquals(expected(h), e(h), 1e-9)
        assertTrue("falls with time", e(0.0) > e(3.0) && e(3.0) > e(24.0) && e(24.0) > e(72.0))
        assertTrue("the last session beats anything older by a wide margin", e(6.0) > 10 * e(48.0))
        assertEquals("weeks later there is no pull", 1.0, e(24 * 30.0), 0.01)
        assertEquals("no timestamp, nothing to date it by", 1.0, SessionSelector.exposure(word(1, correct = 3, wrong = 1), now), 0.0)
    }

    @Test
    fun difficultyComesFromTheSuccessRateAndAMissRaisesIt() {
        val w = practised(1, correct = 2, wrong = 2, hoursAgo = 5.0)             // a = 3/6 = 0.5
        assertEquals(0.15 + 0.5, SessionSelector.difficulty(w), 1e-9)
        assertEquals(1.5 * (0.15 + 0.5), SessionSelector.difficulty(w.copy(interval = 1)), 1e-9)
        val solid = practised(2, correct = 30, wrong = 0, hoursAgo = 5.0, known = true)
        assertTrue("a very solid word still has the floor", SessionSelector.difficulty(solid) > 0.15)
        assertTrue(SessionSelector.difficulty(practised(3, 0, 3, 5.0)) > SessionSelector.difficulty(practised(4, 5, 0, 5.0)))
    }

    @Test
    fun unitImportanceIsASoftNudgeOnlyInMasterMode() {
        assertEquals(1.0, SessionSelector.unitImportance(word(1, unit = 1), masterMode = true), 1e-9)
        assertEquals(1.0 / 1.9, SessionSelector.unitImportance(word(2, unit = 10), masterMode = true), 1e-9) // ~0.53
        assertEquals(1.0, SessionSelector.unitImportance(word(3, unit = 10), masterMode = false), 1e-9)
    }

    @Test
    fun aWordAnsweredRightTwiceInARowRecentlyCoolsDown() {
        // interval 1 -> 6 after the first right answer, then x ease (>= 7) after the second
        val twice = practised(1, correct = 4, wrong = 1, hoursAgo = 2.0, interval = 14)
        val once = practised(2, correct = 4, wrong = 1, hoursAgo = 2.0, interval = 6)
        assertTrue(SessionSelector.answeredRightTwiceRecently(twice, now))
        assertTrue(!SessionSelector.answeredRightTwiceRecently(once, now))
        assertTrue("a miss is never cooled down", !SessionSelector.answeredRightTwiceRecently(twice.copy(interval = 1), now))
        assertTrue("only within 24 h", !SessionSelector.answeredRightTwiceRecently(twice.copy(updatedAt = now - 25 * hour), now))
        assertEquals(0.3, SessionSelector.weight(twice, now) / SessionSelector.weight(twice.copy(interval = 6), now), 1e-9)
    }

    // ---- the headline behaviour ----

    @Test
    fun aWordMissedInTheLastSessionBeatsAnOldMasteredOne() {
        val missed = practised(1, correct = 0, wrong = 1, hoursAgo = 1.0, lastWrong = true)
        val mastered = practised(2, correct = 10, wrong = 0, hoursAgo = 30 * 24.0, known = true)
        val fillers = (3..12).map { practised(it, correct = 2, wrong = 2, hoursAgo = 5 * 24.0) }

        assertTrue(SessionSelector.weight(missed, now) > 10 * SessionSelector.weight(mastered, now))
        val hits = frequency(listOf(missed, mastered) + fillers, count = 3, trials = 4000)
        assertTrue("missed ${hits[1]} vs mastered ${hits[2]}", hits.getValue(1) > 5 * hits.getValue(2))
    }

    @Test
    fun probabilityDropsAsAccuracyRises() {
        // same recency, all answered right last; they differ only in how many times they were right
        val ladder = listOf(0, 1, 3, 8).mapIndexed { i, c -> practised(i + 1, correct = c, wrong = 1, hoursAgo = 30.0) }
        val fillers = (10..11).map { practised(it, correct = 2, wrong = 2, hoursAgo = 30.0) }
        val w = ladder.map { SessionSelector.weight(it, now) }
        assertTrue("weights should strictly fall: $w", w.zipWithNext().all { (a, b) -> a > b })

        val hits = frequency(ladder + fillers, count = 2, trials = 6000)
        val f = (1..4).map { hits.getOrDefault(it, 0) }
        assertTrue("picked counts should fall with accuracy: $f", f.zipWithNext().all { (a, b) -> a > b })
    }

    @Test
    fun recentlyPractisedBeatsLongAgoAtTheSameAccuracy() {
        val recent = practised(1, correct = 1, wrong = 1, hoursAgo = 1.0)
        val old = practised(2, correct = 1, wrong = 1, hoursAgo = 20 * 24.0)
        assertTrue(SessionSelector.weight(recent, now) > 3 * SessionSelector.weight(old, now))
    }

    @Test
    fun masterModeFavoursLowerUnitsWithoutExcludingHigherOnes() {
        val unit1 = (1..40).map { practised(it, correct = 2, wrong = 2, hoursAgo = 100.0, unit = 1) }
        val unit10 = (101..140).map { practised(it, correct = 2, wrong = 2, hoursAgo = 100.0, unit = 10) }
        val hits = frequency(unit1 + unit10, count = 10, trials = 600, master = true)
        val share1 = (1..40).sumOf { hits.getOrDefault(it, 0) }
        val share10 = (101..140).sumOf { hits.getOrDefault(it, 0) }
        assertTrue("unit 1 $share1 vs unit 10 $share10", share1 > 1.2 * share10)
        assertTrue("a soft nudge, never a hard rule: unit 10 still appears ($share10)", share10 > 0)
        val single = frequency(unit1 + unit10, count = 10, trials = 600, master = false)
        val s1 = (1..40).sumOf { single.getOrDefault(it, 0) }
        val s10 = (101..140).sumOf { single.getOrDefault(it, 0) }
        assertTrue("no nudge outside master mode ($s1 vs $s10)", s1 < 1.15 * s10 && s10 < 1.15 * s1)
    }

    // ---- the balance: new words and the 48 h cap ----

    @Test
    fun newWordsAreTwentyToThirtyPercentOfASession() {
        val practisedPool = (1..60).map { practised(it, correct = it % 5, wrong = 1 + it % 3, hoursAgo = 100.0 + it) }
        val fresh = (101..300).map { word(it, unit = 1 + it % 3) }
        val random = Random(11)
        for ((count, lo, hi) in listOf(Triple(10, 2, 3), Triple(15, 3, 5), Triple(20, 4, 6), Triple(5, 1, 2))) {
            val seen = HashSet<Int>()
            repeat(400) {
                val picked = SessionSelector.select(practisedPool + fresh, count, now, random)
                val n = picked.count { SessionSelector.isNew(it) }
                assertEquals(count, picked.size)
                assertTrue("$n new of $count, expected $lo..$hi", n in lo..hi)
                seen.add(n)
            }
            assertEquals("the share varies across the whole $lo..$hi range", (lo..hi).toSet(), seen)
        }
    }

    @Test
    fun notEnoughPractisedWordsAreToppedUpWithNewOnes() {
        val practisedPool = (1..3).map { practised(it, correct = 1, wrong = 1, hoursAgo = 2.0) }
        val fresh = (101..200).map { word(it) }
        val picked = SessionSelector.select(practisedPool + fresh, 10, now, Random(3))
        assertEquals(10, picked.size)
        assertEquals(setOf(1, 2, 3), picked.map { it.id }.filter { it <= 3 }.toSet())
        assertEquals(7, picked.count { SessionSelector.isNew(it) })
    }

    @Test
    fun withTooFewNewWordsTheRestComesFromPractisedOnes() {
        val practisedPool = (1..40).map { practised(it, correct = 1, wrong = 1, hoursAgo = 40.0 + it) }
        val fresh = (101..101).map { word(it) } // only one unseen word left
        val picked = SessionSelector.select(practisedPool + fresh, 10, now, Random(4))
        assertEquals(10, picked.size)
        assertTrue(picked.count { SessionSelector.isNew(it) } <= 1)
    }

    @Test
    fun allNewPoolStillFillsTheSession() {
        val fresh = (1..100).map { word(it) }
        assertEquals(10, SessionSelector.select(fresh, 10, now, Random(5)).size)
    }

    @Test
    fun newWordsAreIntroducedInUnitOrder() {
        // 5 units x 30 new words, ids ascending with unit; plus plenty of practised words
        val fresh = (1..5).flatMap { u -> (1..30).map { word(1000 + u * 100 + it, unit = u) } }
        val practisedPool = (1..40).map { practised(it, correct = 2, wrong = 1, hoursAgo = 100.0) }
        val random = Random(21)
        for (master in listOf(false, true)) repeat(100) {
            val newPicked = SessionSelector.select(practisedPool + fresh, 10, now, random, master).filter { SessionSelector.isNew(it) }
            assertTrue("a session introduces some new words", newPicked.isNotEmpty())
            // 2-3 new words, drawn from the first ~9 in (unit, id) order: all of unit 1
            assertTrue("new words should come from the front of unit 1: ${newPicked.map { it.id }}", newPicked.all { it.unit == 1 })
        }
    }

    @Test
    fun atMostHalfASessionComesFromWordsAnsweredInTheLast48Hours() {
        val recent = (1..40).map { practised(it, correct = 0, wrong = 2, hoursAgo = 0.5, lastWrong = true) }
        val older = (101..160).map { practised(it, correct = 3, wrong = 1, hoursAgo = 200.0) }
        val random = Random(9)
        for ((count, cap) in listOf(10 to 5, 15 to 8, 20 to 10)) repeat(200) {
            val picked = SessionSelector.select(recent + older, count, now, random)
            val n = picked.count { SessionSelector.isRecent(it, now) }
            assertTrue("$n recent of $count (cap $cap)", n <= cap)
        }
    }

    @Test
    fun theRecentCapGivesWayRatherThanReturningAShortSession() {
        val allRecent = (1..30).map { practised(it, correct = 0, wrong = 2, hoursAgo = 1.0, lastWrong = true) }
        assertEquals(10, SessionSelector.select(allRecent, 10, now, Random(2)).size)
    }

    // ---- last answer wrong, even for known words ----

    @Test
    fun lastAnswerWrongAgreesWithWhatSrsEngineActuallyDoes() {
        // Ties the interval == 1 trick to SrsEngine.processAnswer: if that ever changes, THIS fails,
        // instead of the selector silently choosing the wrong words.
        val starts = listOf(
            word(1, updatedAt = now),                                                              // brand new
            word(2, correct = 4, wrong = 1, known = true, srs = 3f, interval = 6, updatedAt = now),
            word(3, correct = 12, wrong = 0, known = true, srs = 6f, interval = 40, updatedAt = now),
            word(4, correct = 1, wrong = 3, srs = 0.2f, interval = 1, updatedAt = now)
        )
        for (start in starts) for (isQuiz in listOf(true, false)) {
            val wrong = SrsEngine.processAnswer(start, isCorrect = false, isQuiz = isQuiz).copy(updatedAt = now)
            assertTrue("wrong answer (quiz=$isQuiz) from $start must read as last-answer-wrong", SessionSelector.lastAnswerWrong(wrong))
            val notSure = SrsEngine.processAnswer(start, isCorrect = false, isQuiz = isQuiz, isNotSure = true).copy(updatedAt = now)
            assertTrue("a no-idea answer (quiz=$isQuiz) is a wrong answer too", SessionSelector.lastAnswerWrong(notSure))
            val right = SrsEngine.processAnswer(start, isCorrect = true, isQuiz = isQuiz).copy(updatedAt = now)
            assertTrue("correct answer (quiz=$isQuiz) from $start must NOT read as wrong", !SessionSelector.lastAnswerWrong(right))
        }
        var w = word(9, updatedAt = now)
        for (correct in listOf(true, true, false, true, false, false, true, true, true)) {
            w = SrsEngine.processAnswer(w, isCorrect = correct, isQuiz = true).copy(updatedAt = now)
            assertEquals("after a ${if (correct) "right" else "wrong"} answer", !correct, SessionSelector.lastAnswerWrong(w))
        }
        assertTrue(!SessionSelector.lastAnswerWrong(word(10, updatedAt = now)))
        assertTrue(!SessionSelector.lastAnswerWrong(word(11, correct = 3, wrong = 1, updatedAt = 0L)))
    }

    @Test
    fun theCooldownRuleAgreesWithWhatSrsEngineActuallyDoes() {
        // "right twice in a row" == interval >= 7 after SrsEngine's 1 -> 6 -> x ease progression
        var w = word(1, updatedAt = now)
        val engine = SrsEngine
        w = engine.processAnswer(w, isCorrect = true, isQuiz = true).copy(updatedAt = now)
        assertTrue("one right answer is not enough", !SessionSelector.answeredRightTwiceRecently(w, now))
        w = engine.processAnswer(w, isCorrect = true, isQuiz = true).copy(updatedAt = now)
        assertTrue("two right answers in a row", SessionSelector.answeredRightTwiceRecently(w, now))
        w = engine.processAnswer(w, isCorrect = false, isQuiz = true).copy(updatedAt = now)
        assertTrue("a miss resets it", !SessionSelector.answeredRightTwiceRecently(w, now))
        w = engine.processAnswer(w, isCorrect = true, isQuiz = true).copy(updatedAt = now)
        assertTrue("one right after a miss is not enough", !SessionSelector.answeredRightTwiceRecently(w, now))
    }

    @Test
    fun theOnlyWordsIDidntKnowPoolKeepsAKnownWordJustAnsweredWrong() {
        val notKnown = word(1, correct = 1, wrong = 2, srs = 0.3f, updatedAt = now - hour, interval = 1)
        val knownMissed = word(2, correct = 8, wrong = 1, known = true, srs = 3f, updatedAt = now - hour, interval = 1)
        val knownRight = word(3, correct = 8, wrong = 1, known = true, srs = 3f, updatedAt = now - hour, interval = 14)
        val knownOld = word(4, correct = 8, wrong = 1, known = true, srs = 3f, updatedAt = 0L, interval = 1)
        val pool = SessionSelector.unknownOnlyPool(listOf(notKnown, knownMissed, knownRight, knownOld))
        assertEquals(listOf(1, 2), pool.map { it.id })
    }

    @Test
    fun theWordsAKnownWordHeavyUserGetsWrongComeBackInTheNextQuiz() {
        // The owner's Unit 1 (IT): full of KNOWN words with long histories (8 right, 1 wrong). The
        // previous quiz's 5 wrong answers are on such words; quiz mode left them isKnown = true.
        val tenMinutes = 10 * 60_000L
        var totalMissed = 0
        var totalRight = 0
        val trials = 200
        for (seed in 1..trials) {
            val knownHistory = (1..120).map {
                word(it, correct = 8, wrong = 1, known = true, srs = 3f, updatedAt = now - 20 * day, interval = 14)
            }
            val oldWrong = (201..280).map {
                word(it, correct = it % 2, wrong = 1 + it % 3, srs = 0.2f, updatedAt = now - 20 * day)
            }
            val fresh = (301..340).map { word(it) }
            val quiz1 = knownHistory.take(10).mapIndexed { i, w ->
                SrsEngine.processAnswer(w, isCorrect = i >= 5, isQuiz = true).copy(updatedAt = now - tenMinutes)
            }
            assertTrue("quiz mode keeps a missed known word known", quiz1.take(5).all { it.isKnown })
            val byId = (knownHistory + oldWrong + fresh).associateBy { it.id }.toMutableMap().also { m -> quiz1.forEach { m[it.id] = it } }
            val next = SessionSelector.select(byId.values.toList(), 10, now, Random(seed)).map { it.id }.toSet()
            totalMissed += quiz1.take(5).count { it.id in next }
            totalRight += quiz1.drop(5).count { it.id in next }
        }
        assertTrue("the missed known words must come back: $totalMissed vs right $totalRight over $trials quizzes",
            totalMissed >= 2.0 * trials && totalMissed > 1.5 * totalRight)
    }

    // ---- shape of the result ----

    @Test
    fun neverRepeatsAWord() {
        val words = (1..40).map { practised(it, correct = it % 2, wrong = it % 3, hoursAgo = it.toDouble()) }
        val random = Random(1)
        repeat(200) {
            val selected = SessionSelector.select(words, 20, now, random)
            assertEquals(20, selected.size)
            assertEquals(20, selected.map { it.id }.toSet().size)
        }
    }

    @Test
    fun smallPoolReturnsEveryWordOnce() {
        val words = (1..5).map { word(it) }
        val selected = SessionSelector.select(words, 20, now, Random(2))
        assertEquals(5, selected.size)
        assertEquals(setOf(1, 2, 3, 4, 5), selected.map { it.id }.toSet())
    }

    @Test
    fun emptyPoolAndZeroCount() {
        assertTrue(SessionSelector.select(emptyList(), 20, now, Random(2)).isEmpty())
        assertTrue(SessionSelector.select(listOf(word(1)), 0, now, Random(2)).isEmpty())
    }

    @Test
    fun prefersHardWords() {
        val hard = (1..10).map { practised(it, correct = 0, wrong = 5, hoursAgo = 2.0, lastWrong = true) }
        val easy = (11..110).map { practised(it, correct = 5, wrong = 0, hoursAgo = 24 * 20.0, known = true) }
        val hits = frequency(hard + easy, count = 10, trials = 100)
        val hardPicked = (1..10).sumOf { hits.getOrDefault(it, 0) }
        // hard words are ~9% of the pool but carry far more weight than mastered ones
        assertTrue("hard words picked $hardPicked/1000", hardPicked > 400)
    }

    @Test
    fun sameSeedGivesTheSameSession() {
        val pool = (1..60).map { practised(it, correct = it % 4, wrong = 1 + it % 2, hoursAgo = it.toDouble()) }
        val a = SessionSelector.select(pool, 15, now, Random(99)).map { it.id }
        val b = SessionSelector.select(pool, 15, now, Random(99)).map { it.id }
        assertEquals(a, b)
    }

    @Test
    fun summarizeCountsWhatTheLogLineReports() {
        val recent = (1..6).map { practised(it, 1, 1, hoursAgo = 2.0) }
        val old = (11..20).map { practised(it, 1, 1, hoursAgo = 300.0) }
        val fresh = (21..24).map { word(it) }
        val pool = recent + old + fresh
        val s = SessionSelector.summarize(pool, recent.take(3) + old.take(2) + fresh.take(1), now)
        assertEquals(SessionSelector.Summary(pool = 20, recentInPool = 6, recentTaken = 3, newTaken = 1, taken = 6), s)
    }
}
