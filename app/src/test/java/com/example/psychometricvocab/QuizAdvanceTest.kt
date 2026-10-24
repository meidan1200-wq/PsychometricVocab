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
 * Two reported bugs, both "a question got skipped": (1) with Auto pass, pressing Next during the
 * delay advanced twice; (2) a 15-question quiz ended with 12 + 2 = 14 answers. Cause of both: Next
 * could advance a question that had not been answered (the sliding-out button is recomposed with
 * the NEW question's index). The fix is structural: every advance names its question AND requires
 * it to be answered, so answers always equal questions.
 */
class QuizAdvanceTest {
    private fun question(i: Int, answered: Boolean) = QuizQuestion(
        word = Word(id = i, track = "english", word = "w$i", definition = "d$i", unit = 1),
        options = emptyList(),
        answered = answered
    )

    /** A quiz sitting on [index], with that question (and every earlier one) already answered. */
    private fun answeredState(questions: Int, index: Int = 0) = QuizUiState(
        questions = (0 until questions).map { question(it, answered = it <= index) },
        currentIndex = index,
        isLoading = false
    )

    private fun freshState(questions: Int) = QuizUiState(
        questions = (0 until questions).map { question(it, answered = false) },
        isLoading = false
    )

    // ---- advanceFrom ----

    @Test
    fun twoAdvancesForTheSameIndexAdvanceExactlyOnce() {
        val start = answeredState(questions = 5, index = 1)
        // e.g. the manual Next button and the auto-pass timer both fire for question 1
        val afterFirst = start.advanceFrom(1)
        val afterSecond = afterFirst.advanceFrom(1)
        assertEquals(2, afterFirst.currentIndex)
        assertEquals("the duplicate must not skip question 2", 2, afterSecond.currentIndex)
    }

    @Test
    fun staleAdvanceReturnsTheSameStateInstance() {
        val moved = answeredState(questions = 5, index = 3)
        assertSame(moved, moved.advanceFrom(2))
    }

    @Test
    fun anUnansweredQuestionCanNeverBeAdvancedPast() {
        // The 14/15 bug: the sliding-out Next button was recomposed with the NEW question's index
        // and so asked to advance a question nobody had answered yet.
        val onQuestion3 = QuizUiState(
            questions = (0 until 15).map { question(it, answered = it < 3) },
            currentIndex = 3,
            isLoading = false
        )
        assertSame(onQuestion3, onQuestion3.advanceFrom(3))
    }

    @Test
    fun advancingFromTheLastQuestionCompletesTheSession() {
        val last = answeredState(questions = 3, index = 2)
        val done = last.advanceFrom(2)
        assertTrue(done.sessionComplete)
        assertEquals(3, done.currentIndex)
        // ...and a duplicate on the last question doesn't push past the end
        assertEquals(3, done.advanceFrom(2).currentIndex)
    }

    @Test
    fun advancingMidQuizDoesNotCompleteTheSession() {
        assertFalse(answeredState(questions = 5, index = 0).advanceFrom(0).sessionComplete)
    }

    // ---- withAnswer ----

    @Test
    fun aDuplicateAnswerCountsOnce() {
        val first = freshState(5).withAnswer(0, optionId = 7, isCorrect = true)
        val second = first.withAnswer(0, optionId = 8, isCorrect = false)
        assertSame(first, second)
        assertEquals(1, first.correctCount)
        assertEquals(0, first.wrongCount)
        assertEquals(7, first.questions[0].selectedOptionId)
    }

    @Test
    fun anAnswerForAQuestionTheQuizHasLeftIsIgnored() {
        val onQuestion1 = answeredState(questions = 5, index = 1)
        assertSame(onQuestion1, onQuestion1.withAnswer(0, optionId = 1, isCorrect = true))
    }

    // ---- the whole flow, with auto pass and the taps that used to skip a question ----

    @Test
    fun fifteenAnswersWithAutoPassShowAndCountExactlyFifteenQuestions() {
        var s = freshState(15)
        val shown = ArrayList<Int>()
        var answers = 0
        var guard = 0
        while (!s.sessionComplete && guard++ < 1000) {
            val i = s.currentIndex
            shown.add(i)

            // Rogue Next taps BEFORE answering, with the old index and with the "recomposed" one
            // (this is what the sliding-out button did): must change nothing.
            assertSame(s, s.advanceFrom(i))
            assertSame(s, s.advanceFrom(i + 1))

            s = s.withAnswer(i, optionId = i, isCorrect = i % 5 != 0)
            answers++
            s = s.withAnswer(i, optionId = i, isCorrect = true) // a double tap on an option

            // The auto-pass timer fires, and a manual tap lands in the same instant (both for i).
            s = s.advanceFrom(i)
            s = s.advanceFrom(i)
            // ...and a stale tap using the next index, before that question is answered.
            s = s.advanceFrom(s.currentIndex)
        }
        assertEquals((0 until 15).toList(), shown)
        assertEquals(15, answers)
        assertEquals(15, s.correctCount + s.wrongCount)
        assertEquals(3, s.wrongCount) // i = 0, 5, 10
        assertTrue(s.sessionComplete)
    }
}
