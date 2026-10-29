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
