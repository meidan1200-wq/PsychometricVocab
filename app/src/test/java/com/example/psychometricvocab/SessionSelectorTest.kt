package com.example.psychometricvocab

import com.example.psychometricvocab.data.SessionSelector
import com.example.psychometricvocab.data.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The learning-v2 picker (replaces the old "every new word has weight 2" one). Deterministic:
 * the Random is seeded and "now" is fixed.
 */
class SessionSelectorTest {
    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L
    private val day = 24 * hour

    private fun word(
        id: Int, unit: Int = 1, correct: Int = 0, wrong: Int = 0, known: Boolean = false,
        srs: Float = 0f, updatedAt: Long = 0L, nextReview: Long = 0L
    ) = Word(
        id = id, track = "english", word = "w$id", definition = "d$id", unit = unit,
        srsScore = srs, correctCount = correct, wrongCount = wrong, isKnown = known,
        updatedAt = updatedAt, nextReviewDate = nextReview
    )

    /** A practised word that is NOT due (next review in the future), to isolate the other factors. */
    private fun practised(id: Int, correct: Int, wrong: Int, hoursAgo: Long, known: Boolean = false) =
        word(id, correct = correct, wrong = wrong, known = known, srs = 1f,
            updatedAt = now - hoursAgo * hour, nextReview = now + 10 * day)

    private fun frequency(pool: List<Word>, count: Int, trials: Int, seed: Int = 7): Map<Int, Int> {
        val random = Random(seed)
        val hits = HashMap<Int, Int>()
        repeat(trials) {
            SessionSelector.select(pool, count, now, random).forEach { hits.merge(it.id, 1, Int::plus) }
        }
        return hits
    }

    // ---- the headline behaviour ----

    @Test
    fun aWordMissedInTheLastSessionBeatsAnOldMasteredOne() {
        val missed = practised(1, correct = 0, wrong = 1, hoursAgo = 1)
        val mastered = practised(2, correct = 10, wrong = 0, hoursAgo = 30 * 24, known = true)
        val fillers = (3..12).map { practised(it, correct = 2, wrong = 2, hoursAgo = 5 * 24) }

        // the weights themselves (deterministic): ~12x apart
        assertTrue(SessionSelector.weight(missed, now) > 8 * SessionSelector.weight(mastered, now))

        val hits = frequency(listOf(missed, mastered) + fillers, count = 3, trials = 4000)
        assertTrue("missed ${hits[1]} vs mastered ${hits[2]}", hits.getValue(1) > 5 * hits.getValue(2))
    }

    @Test
    fun probabilityDropsAsAccuracyRises() {
        // same recency, all answered wrong once; they differ only in how many times they were right
        val ladder = listOf(0, 1, 3, 8).mapIndexed { i, c -> practised(i + 1, correct = c, wrong = 1, hoursAgo = 1) }
        val fillers = (10..11).map { practised(it, correct = 2, wrong = 2, hoursAgo = 24) }

        val w = ladder.map { SessionSelector.weight(it, now) }
        assertTrue("weights should strictly fall: $w", w.zipWithNext().all { (a, b) -> a > b })

        val hits = frequency(ladder + fillers, count = 2, trials = 6000)
        val f = (1..4).map { hits.getOrDefault(it, 0) }
        assertTrue("picked counts should fall with accuracy: $f", f.zipWithNext().all { (a, b) -> a > b })
    }

    @Test
    fun recentlyPractisedBeatsLongAgoAtTheSameAccuracy() {
        val recent = practised(1, correct = 1, wrong = 1, hoursAgo = 1)
        val old = practised(2, correct = 1, wrong = 1, hoursAgo = 20 * 24)
        assertTrue(SessionSelector.weight(recent, now) > SessionSelector.weight(old, now))
    }

    @Test
    fun knownWordsAreAtLeast85PercentMastered() {
        val shaky = word(1, correct = 1, wrong = 3, known = true)
        assertEquals(0.85, SessionSelector.mastery(shaky), 1e-9)
        val solid = word(2, correct = 20, wrong = 0, known = true)
        assertTrue(SessionSelector.mastery(solid) > 0.85)
    }

    @Test
    fun unseenWordsHaveNoRecency() {
        assertEquals(0.0, SessionSelector.recency(word(1), now), 0.0)
    }

    @Test
    fun dueWordsGetTheDueMultiplier() {
        val notDue = practised(1, correct = 1, wrong = 1, hoursAgo = 5)
        val due = notDue.copy(id = 2, nextReviewDate = now - hour)
        assertEquals(2.0, SessionSelector.weight(due, now) / SessionSelector.weight(notDue, now), 1e-9)
        assertEquals(
            3.0,
            SessionSelector.weight(due, now, SessionSelector.DUE_MULTIPLIER_PRACTICE) /
                SessionSelector.weight(notDue, now, SessionSelector.DUE_MULTIPLIER_PRACTICE),
            1e-9
        )
    }

    // ---- new words ----

    @Test
    fun newWordCapHolds() {
        val practisedPool = (1..50).map { practised(it, correct = it % 5, wrong = 1 + it % 3, hoursAgo = it.toLong()) }
        val fresh = (101..200).map { word(it, unit = 1 + it % 3) }
        val random = Random(11)
        for ((count, cap) in listOf(10 to 3, 15 to 5, 20 to 6, 5 to 2)) {
            repeat(300) {
                val picked = SessionSelector.select(practisedPool + fresh, count, now, random)
                assertEquals(count, picked.size)
                assertTrue("new words ${picked.count { SessionSelector.isNew(it) }} > cap $cap", picked.count { SessionSelector.isNew(it) } <= cap)
            }
        }
    }

    @Test
    fun notEnoughPractisedWordsAreToppedUpWithNewOnes() {
        val practisedPool = (1..3).map { practised(it, correct = 1, wrong = 1, hoursAgo = 2) }
        val fresh = (101..200).map { word(it) }
        val picked = SessionSelector.select(practisedPool + fresh, 10, now, Random(3))
        assertEquals(10, picked.size)
        assertEquals(setOf(1, 2, 3), picked.map { it.id }.filter { it <= 3 }.toSet())
        assertEquals(7, picked.count { SessionSelector.isNew(it) })
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
        val practisedPool = (1..40).map { practised(it, correct = 2, wrong = 1, hoursAgo = 3) }
        val random = Random(21)
        repeat(100) {
            val newPicked = SessionSelector.select(practisedPool + fresh, 10, now, random).filter { SessionSelector.isNew(it) }
            assertTrue("a session introduces some new words", newPicked.isNotEmpty())
            // the cap is 3 new words, drawn from the first ~9 in (unit, id) order: all unit 1
            assertTrue("new words should come from the front of unit 1: ${newPicked.map { it.id }}", newPicked.all { it.unit == 1 })
        }
    }

    // ---- carry-over: the owner's main complaint (words from the last quiz didn't come back) ----

    /** A big, realistic pool: 80 OLD wrong-not-known words, 60 old mastered known ones, 60 never seen. */
    private fun realisticPool(): List<Word> {
        val oldWrong = (1..80).map {
            word(it, correct = it % 2, wrong = 1 + it % 3, srs = 0.2f,
                updatedAt = now - 20 * day, nextReview = now - 5 * day)
        }
        val oldMastered = (101..160).map {
            word(it, correct = 10, known = true, srs = 3f, updatedAt = now - 30 * day, nextReview = now + 10 * day)
        }
        val fresh = (201..260).map { word(it, unit = 1 + it % 4) }
        return oldWrong + oldMastered + fresh
    }

    /** Answer a quiz like the app does: first [wrongCount] wrong, the rest right, all stamped at [at]. */
    private fun answered(quiz: List<Word>, wrongCount: Int, at: Long): List<Word> =
        quiz.mapIndexed { i, w ->
            com.example.psychometricvocab.data.SrsEngine.processAnswer(w, isCorrect = i >= wrongCount, isQuiz = true)
                .copy(updatedAt = at)
        }

    @Test
    fun wordsFromTheLastQuizComeBackInTheNextOneWrongOnesPreferred() {
        var minOverlap = Int.MAX_VALUE
        var wrongBack = 0
        var rightBack = 0
        val trials = 200
        for (seed in 1..trials) {
            val random = Random(seed)
            val pool = realisticPool()
            val quiz1 = SessionSelector.select(pool, 10, now, random)
            val after = answered(quiz1, wrongCount = 5, at = now)
            val wrongIds = after.take(5).map { it.id }.toSet()
            val rightIds = after.drop(5).map { it.id }.toSet()
            val byId = pool.associateBy { it.id }.toMutableMap().also { m -> after.forEach { m[it.id] = it } }

            val quiz2 = SessionSelector.select(byId.values.toList(), 10, now + 10 * 60_000L, random)
            val ids = quiz2.map { it.id }.toSet()
            val back = (wrongIds + rightIds).count { it in ids }
            minOverlap = minOf(minOverlap, back)
            wrongBack += wrongIds.count { it in ids }
            rightBack += rightIds.count { it in ids }
        }
        assertTrue("every follow-up quiz must reuse at least 4 of the last quiz's 10 words (min was $minOverlap)", minOverlap >= 4)
        assertTrue("the missed words should be preferred: wrong back $wrongBack vs right back $rightBack", wrongBack > rightBack)
    }

    @Test
    fun theLastQuizDominatesEvenWhenADayOfOtherUnmasteredWordsIsStillInTheWindow() {
        // The heavy-user case IT measured on real data: a 208-word unit where 60 words were practised
        // ~20 h ago (still unmastered, still inside the 48 h window), then a quiz 10 minutes ago.
        val tenMinutes = 10 * 60_000L
        var minOverlap = Int.MAX_VALUE
        var totalOverlap = 0
        var wrongBack = 0
        var rightBack = 0
        val trials = 200
        for (seed in 1..trials) {
            val earlierToday = (1..60).map {
                word(it, correct = it % 3, wrong = 1 + it % 2, srs = 0.3f,
                    updatedAt = now - 20 * hour, nextReview = now + day)
            }
            val quiz1Wrong = (101..105).map {
                word(it, correct = 0, wrong = 1, srs = 0.1f, updatedAt = now - tenMinutes, nextReview = now + day)
            }
            val quiz1Right = (106..110).map {
                word(it, correct = 1, wrong = 0, srs = 0.34f, updatedAt = now - tenMinutes, nextReview = now + day)
            }
            val oldWrong = (201..280).map {
                word(it, correct = it % 2, wrong = 1 + it % 3, srs = 0.2f,
                    updatedAt = now - 20 * day, nextReview = now - 5 * day)
            }
            val fresh = (301..358).map { word(it) }
            val unit = earlierToday + quiz1Wrong + quiz1Right + oldWrong + fresh
            assertEquals(208, unit.size)

            val ids = SessionSelector.select(unit, 10, now, Random(seed)).map { it.id }.toSet()
            val back = (101..110).count { it in ids }
            minOverlap = minOf(minOverlap, back)
            totalOverlap += back
            wrongBack += (101..105).count { it in ids }
            rightBack += (106..110).count { it in ids }
        }
        val average = totalOverlap.toDouble() / trials
        assertTrue("on average at least 4 of the last quiz's 10 words come back (was $average)", average >= 4.0)
        assertTrue("at least 3 in every single session (min was $minOverlap)", minOverlap >= 3)
        assertTrue("the missed ones are preferred: wrong back $wrongBack vs right back $rightBack", wrongBack > rightBack)
    }

    @Test
    fun carryOverWeightFallsFastWithTime() {
        fun weightAt(minutesAgo: Long) = SessionSelector.carryOverWeight(
            word(1, correct = 0, wrong = 1, srs = 0.1f, updatedAt = now - minutesAgo * 60_000L), now
        )
        val oneMinusM = 1 - SessionSelector.mastery(word(1, correct = 0, wrong = 1))
        assertEquals(oneMinusM * 0.95, weightAt(10), 0.01)       // 10 minutes ago ~ 0.95
        assertEquals(oneMinusM * 0.37, weightAt(3 * 60), 0.01)   // 3 h ~ 0.37
        assertEquals(oneMinusM * 0.14, weightAt(6 * 60), 0.01)   // 6 h ~ 0.14
        assertEquals(0.0, weightAt(24 * 60), 0.001)              // 24 h ~ 0.0003: gone, almost
        assertTrue(weightAt(48 * 60) > 0.0)                      // still eligible at the edge of the window
    }

    @Test
    fun aWordLeavesCarryOverAtNinetyPercentAccuracyOrWhenKnown() {
        fun recent(correct: Int, wrong: Int, known: Boolean = false) =
            word(1, correct = correct, wrong = wrong, known = known, srs = 1f, updatedAt = now - hour)
        assertTrue(SessionSelector.isCarryOver(recent(correct = 0, wrong = 1), now))
        assertTrue("accuracy 8/9 = 0.889 is still carry-over", SessionSelector.isCarryOver(recent(correct = 7, wrong = 0), now))
        assertTrue("accuracy 0.9 exactly: out", !SessionSelector.isCarryOver(recent(correct = 8, wrong = 0), now))
        assertTrue("accuracy above 0.9: out", !SessionSelector.isCarryOver(recent(correct = 9, wrong = 0), now))
        assertTrue("known: out, whatever the accuracy", !SessionSelector.isCarryOver(recent(correct = 0, wrong = 1, known = true), now))
        assertTrue("practised more than 48 h ago: out", !SessionSelector.isCarryOver(recent(correct = 0, wrong = 1).copy(updatedAt = now - 49 * hour), now))
        assertTrue("never practised: out", !SessionSelector.isCarryOver(word(2), now))
    }

    @Test
    fun masteredWordsNoLongerGetTheCarryOverQuota() {
        // 10 words practised minutes ago that have reached >= 0.9 accuracy, among 80 old wrong ones.
        val mastered = (301..310).map {
            word(it, correct = 9, wrong = 0, srs = 2f, updatedAt = now - 10 * 60_000L, nextReview = now + 10 * day)
        }
        val pool = realisticPool().filter { it.id <= 80 || it.id >= 201 } + mastered
        val random = Random(77)
        var picked = 0
        val trials = 300
        repeat(trials) {
            picked += SessionSelector.select(pool, 10, now, random).count { it.id in 301..310 }
        }
        // They get no reserved slots and weigh little in the open draw: well under one per session.
        assertTrue("mastered recent words picked $picked times in $trials sessions", picked < trials / 2)
    }

    @Test
    fun theCarryOverQuotaIsHalfTheSession() {
        val carry = (401..440).map { word(it, correct = 0, wrong = 1, srs = 0.2f, updatedAt = now - hour, nextReview = now + day) }
        val pool = realisticPool().filter { it.id <= 80 } + carry
        val random = Random(5)
        for ((count, quota) in listOf(10 to 5, 15 to 8, 20 to 10)) {
            repeat(100) {
                val picked = SessionSelector.select(pool, count, now, random)
                assertEquals(count, picked.size)
                assertEquals(count, picked.map { it.id }.toSet().size)
                assertTrue("carry-over words ${picked.count { it.id in 401..440 }} < quota $quota", picked.count { it.id in 401..440 } >= quota)
            }
        }
    }

    @Test
    fun withFewerCarryOverWordsThanTheQuotaAllOfThemAreTaken() {
        val carry = (401..403).map { word(it, correct = 0, wrong = 1, srs = 0.2f, updatedAt = now - hour, nextReview = now + day) }
        val pool = realisticPool() + carry
        val random = Random(8)
        repeat(100) {
            val ids = SessionSelector.select(pool, 10, now, random).map { it.id }.toSet()
            assertTrue("all 3 carry-over words must be in the session: $ids", ids.containsAll(setOf(401, 402, 403)))
        }
    }

    @Test
    fun theMostRecentAndLeastMasteredCarryOverWordsComeFirst() {
        // 12 candidates, quota 5 of 10: the freshly missed ones beat the older, better-known ones.
        val freshlyMissed = (501..506).map { word(it, correct = 0, wrong = 2, srs = 0.1f, updatedAt = now - 10 * 60_000L, nextReview = now + day) }
        val olderBetter = (511..516).map { word(it, correct = 6, wrong = 1, srs = 0.9f, updatedAt = now - 40 * hour, nextReview = now + day) }
        val pool = freshlyMissed + olderBetter + (601..640).map { word(it) }
        val random = Random(13)
        var missed = 0
        var better = 0
        repeat(300) {
            val picked = SessionSelector.select(pool, 10, now, random).map { it.id }
            missed += picked.count { it in 501..506 }
            better += picked.count { it in 511..516 }
        }
        assertTrue("freshly missed $missed vs older/better $better", missed > 2 * better)
    }

    @Test
    fun practiceSessionsGetTheSameCarryOver() {
        val carry = (701..720).map { word(it, correct = 1, wrong = 2, srs = 0.2f, updatedAt = now - 2 * hour, nextReview = now + day) }
        val pool = realisticPool() + carry
        val random = Random(31)
        repeat(100) {
            val picked = SessionSelector.select(pool, 30, now, random, SessionSelector.DUE_MULTIPLIER_PRACTICE)
            assertEquals(30, picked.size)
            assertTrue("practice carry-over ${picked.count { it.id in 701..720 }}", picked.count { it.id in 701..720 } >= 15)
        }
    }

    // ---- shape of the result (ported from the old picker's tests) ----

    @Test
    fun neverRepeatsAWord() {
        val words = (1..40).map { word(it, wrong = it % 3, correct = it % 2, srs = 1f, nextReview = now - hour, updatedAt = now - hour) }
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
        val hard = (1..10).map { practised(it, correct = 0, wrong = 5, hoursAgo = 2) }
        val easy = (11..110).map { practised(it, correct = 5, wrong = 0, hoursAgo = 24 * 20, known = true) }
        val hits = frequency(hard + easy, count = 10, trials = 100)
        val hardPicked = (1..10).sumOf { hits.getOrDefault(it, 0) }
        // hard words are ~9% of the pool but carry far more weight than mastered ones
        assertTrue("hard words picked $hardPicked/1000", hardPicked > 400)
    }

    @Test
    fun sameSeedGivesTheSameSession() {
        val pool = (1..60).map { practised(it, correct = it % 4, wrong = 1 + it % 2, hoursAgo = it.toLong()) }
        val a = SessionSelector.select(pool, 15, now, Random(99)).map { it.id }
        val b = SessionSelector.select(pool, 15, now, Random(99)).map { it.id }
        assertEquals(a, b)
    }
}
