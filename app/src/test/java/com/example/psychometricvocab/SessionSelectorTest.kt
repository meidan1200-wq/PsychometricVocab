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
