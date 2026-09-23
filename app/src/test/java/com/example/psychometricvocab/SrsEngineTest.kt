package com.example.psychometricvocab

import com.example.psychometricvocab.data.SrsEngine
import com.example.psychometricvocab.data.Word
import org.junit.Assert.*
import org.junit.Test

class SrsEngineTest {

    private fun word(
        id: Int,
        srsScore: Float = 0f,
        correct: Int = 0,
        wrong: Int = 0,
        known: Boolean = false
    ) = Word(
        id = id, track = "english", word = "w$id", definition = "d$id", unit = 1,
        srsScore = srsScore, correctCount = correct, wrongCount = wrong, isKnown = known
    )

    @Test
    fun selectWordsForSession_neverRepeatsAWord() {
        val words = (1..40).map { word(it, wrong = it % 3, correct = it % 2) }
        repeat(200) {
            val selected = SrsEngine.selectWordsForSession(words, 20)
            assertEquals(20, selected.size)
            assertEquals(20, selected.map { it.id }.toSet().size)
        }
    }

    @Test
    fun selectWordsForSession_smallPoolReturnsEachWordOnce() {
        val words = (1..5).map { word(it) }
        val selected = SrsEngine.selectWordsForSession(words, 20)
        assertEquals(5, selected.size)
        assertEquals(setOf(1, 2, 3, 4, 5), selected.map { it.id }.toSet())
    }

    @Test
    fun selectWordsForSession_emptyPool() {
        assertTrue(SrsEngine.selectWordsForSession(emptyList(), 20).isEmpty())
    }

    @Test
    fun selectWordsForSession_prefersHardWords() {
        val hard = (1..10).map { word(it, srsScore = 0.2f, wrong = 5) }
        val easy = (11..110).map { word(it, srsScore = 3f, correct = 5, known = true) }
        var hardPicked = 0
        repeat(100) {
            hardPicked += SrsEngine.selectWordsForSession(hard + easy, 10).count { it.id <= 10 }
        }
        // Hard words are ~10% of the pool but carry ~15x the weight of known words
        assertTrue("hard words picked $hardPicked/1000", hardPicked > 400)
    }

    @Test
    fun processAnswer_correctFlashcardMarksKnownAndGrowsInterval() {
        val updated = SrsEngine.processAnswer(word(1), isCorrect = true)
        assertTrue(updated.isKnown)
        assertEquals(1, updated.correctCount)
        assertEquals(6, updated.interval)
    }

    @Test
    fun processAnswer_quizNeedsThreeCorrectAnswersToBecomeKnown() {
        var w = word(1)
        w = SrsEngine.processAnswer(w, isCorrect = true, isQuiz = true)
        w = SrsEngine.processAnswer(w, isCorrect = true, isQuiz = true)
        assertFalse(w.isKnown)
        w = SrsEngine.processAnswer(w, isCorrect = true, isQuiz = true)
        assertTrue(w.isKnown)
    }

    @Test
    fun processAnswer_notSureResetsScoreAndKnown() {
        val known = word(1, srsScore = 2f, known = true)
        val updated = SrsEngine.processAnswer(known, isCorrect = false, isQuiz = true, isNotSure = true)
        assertEquals(0f, updated.srsScore)
        assertFalse(updated.isKnown)
        assertEquals(1, updated.interval)
    }

    @Test
    fun processAnswer_wrongQuizAnswerKeepsKnownButLowersEase() {
        val known = word(1, srsScore = 2f, known = true)
        val updated = SrsEngine.processAnswer(known, isCorrect = false, isQuiz = true)
        assertTrue(updated.isKnown)
        assertEquals(2.3f, updated.easeFactor, 0.0001f)
    }
}
