package com.example.psychometricvocab.data

/**
 * SM-2 Spaced Repetition Algorithm
 *
 * On correct answer: interval grows exponentially, ease factor increases slightly
 * On wrong answer:   interval resets to 1, ease factor decreases (min 1.3)
 */
object SrsEngine {

    private const val MIN_EASE_FACTOR = 1.3f
    private const val MAX_EASE_FACTOR = 4.0f
    private const val EASE_BONUS_CORRECT = 0.1f
    private const val EASE_PENALTY_WRONG = 0.2f
    private const val MS_PER_DAY = 86_400_000L

    fun processAnswer(word: Word, isCorrect: Boolean, isQuiz: Boolean = false, isNotSure: Boolean = false): Word {
        val now = System.currentTimeMillis()
        return if (isCorrect) {
            val newEase = (word.easeFactor + EASE_BONUS_CORRECT).coerceAtMost(MAX_EASE_FACTOR)
            val newInterval = when {
                word.interval == 0 -> 1
                word.interval == 1 -> 6
                else -> (word.interval * newEase).toInt().coerceAtLeast(1)
            }
            val bump = if (isQuiz) 0.34f else 1.0f
            val newScore = word.srsScore + bump
            word.copy(
                easeFactor = newEase,
                interval = newInterval,
                nextReviewDate = now + newInterval * MS_PER_DAY,
                correctCount = word.correctCount + 1,
                srsScore = newScore,
                isKnown = newScore >= 1f
            )
        } else {
            val newEase = (word.easeFactor - EASE_PENALTY_WRONG).coerceAtLeast(MIN_EASE_FACTOR)
            val newScore = if (isNotSure) 0f else (word.srsScore - 0.33f).coerceAtLeast(0f)
            word.copy(
                easeFactor = newEase,
                interval = 1,
                nextReviewDate = now + MS_PER_DAY,
                wrongCount = word.wrongCount + 1,
                srsScore = newScore,
                // Strip 'isKnown' status if they explicitly pressed "Not sure", or if it's a flashcard failure
                isKnown = if (isNotSure) false else if (isQuiz) word.isKnown else false
            )
        }
    }
}
