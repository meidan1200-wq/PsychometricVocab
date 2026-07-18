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

    fun processAnswer(word: Word, isCorrect: Boolean): Word {
        val now = System.currentTimeMillis()
        return if (isCorrect) {
            val newEase = (word.easeFactor + EASE_BONUS_CORRECT).coerceAtMost(MAX_EASE_FACTOR)
            val newInterval = when {
                word.interval <= 1 -> 1
                word.interval == 1 -> 6
                else -> (word.interval * newEase).toInt().coerceAtLeast(1)
            }
            val newScore = word.srsScore + 1f
            word.copy(
                easeFactor = newEase,
                interval = newInterval,
                nextReviewDate = now + newInterval * MS_PER_DAY,
                correctCount = word.correctCount + 1,
                srsScore = newScore,
                isKnown = newScore >= 3f
            )
        } else {
            val newEase = (word.easeFactor - EASE_PENALTY_WRONG).coerceAtLeast(MIN_EASE_FACTOR)
            val newScore = (word.srsScore - 0.5f).coerceAtLeast(0f)
            word.copy(
                easeFactor = newEase,
                interval = 1,
                nextReviewDate = now + MS_PER_DAY,
                wrongCount = word.wrongCount + 1,
                srsScore = newScore,
                isKnown = false
            )
        }
    }

    /** Returns words weighted by SRS priority — unknown words appear more often */
    fun selectWordsForSession(words: List<Word>, count: Int): List<Word> {
        if (words.isEmpty()) return emptyList()
        val weighted = mutableListOf<Word>()
        words.forEach { word ->
            val weight = when {
                word.isKnown -> 1
                word.srsScore < 1f -> 5
                word.srsScore < 2f -> 4
                word.srsScore < 3f -> 3
                else -> 2
            }
            repeat(weight) { weighted.add(word) }
        }
        weighted.shuffle()
        return weighted.take(count).distinctBy { it.id }
            .let { if (it.size < count) it + weighted.take(count - it.size) else it }
            .take(count)
    }
}
