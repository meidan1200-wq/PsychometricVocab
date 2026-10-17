package com.example.psychometricvocab

import com.example.psychometricvocab.data.Word
import com.example.psychometricvocab.ui.quiz.QuizQuestion
import com.example.psychometricvocab.ui.quiz.QuizUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auto pass bug: pressing Next during the auto-advance delay skipped a question (two advances).
 * The fix is structural: every advance names the question it belongs to, and a second advance
 * for a question that was already left changes nothing.
 */
class QuizAdvanceTest {
    private fun question(i: Int) = QuizQuestion(
        word = Word(id = i, track = "english", word = "w$i", definition = "d$i", unit = 1),
        options = emptyList()
    )

    private fun state(questions: Int, index: Int = 0) = QuizUiState(
        questions = (0 until questions).map(::question),
        currentIndex = index,
        isLoading = false
    )

    @Test
    fun twoAdvancesForTheSameIndexAdvanceExactlyOnce() {
        val start = state(questions = 5, index = 1)
        // e.g. the manual Next button and the auto-pass timer both fire for question 1
        val afterFirst = start.advanceFrom(1)
        val afterSecond = afterFirst.advanceFrom(1)
        assertEquals(2, afterFirst.currentIndex)
        assertEquals("the duplicate must not skip question 2", 2, afterSecond.currentIndex)
    }

    @Test
    fun staleAdvanceReturnsTheSameStateInstance() {
        val moved = state(questions = 5, index = 3)
        assertSame(moved, moved.advanceFrom(2))
    }

    @Test
    fun advancingFromTheLastQuestionCompletesTheSession() {
        val last = state(questions = 3, index = 2)
        val done = last.advanceFrom(2)
        assertTrue(done.sessionComplete)
        assertEquals(3, done.currentIndex)
        // ...and a duplicate on the last question doesn't push past the end
        assertEquals(3, done.advanceFrom(2).currentIndex)
    }

    @Test
    fun advancingMidQuizDoesNotCompleteTheSession() {
        assertFalse(state(questions = 5, index = 0).advanceFrom(0).sessionComplete)
    }
}
