package com.example.psychometricvocab.data

import kotlinx.coroutines.flow.Flow

class VocabRepository(private val dao: WordDao) {

    fun getAllWords(track: String): Flow<List<Word>> = dao.getAllWords(track)
    fun getWordsByUnit(track: String, unit: Int): Flow<List<Word>> = dao.getWordsByUnit(track, unit)
    fun getAllUnits(track: String): Flow<List<Int>> = dao.getAllUnits(track)
    fun getTotalCount(track: String): Flow<Int> = dao.getTotalCount(track)
    fun getKnownCount(track: String): Flow<Int> = dao.getKnownCount(track)
    fun getUnknownCount(track: String): Flow<Int> = dao.getUnknownCount(track)
    fun getUpcomingReviews(track: String, limit: Int): Flow<List<Word>> = dao.getUpcomingReviews(track, limit)
    fun getCountByUnit(track: String, unit: Int): Flow<Int> = dao.getCountByUnit(track, unit)
    fun getKnownCountByUnit(track: String, unit: Int): Flow<Int> = dao.getKnownCountByUnit(track, unit)
    fun getHardestWordsCount(track: String): Flow<Int> = dao.getHardestWordsCount(track)
    fun getHardestWordsCountByUnit(track: String, unit: Int): Flow<Int> = dao.getHardestWordsCountByUnit(track, unit)
    fun getUntouchedWordsByUnit(track: String, unit: Int): Flow<List<Word>> = dao.getUntouchedWordsByUnit(track, unit)
    fun getAllUntouchedWords(track: String): Flow<List<Word>> = dao.getAllUntouchedWords(track)
    fun getUntouchedCountByUnit(track: String, unit: Int): Flow<Int> = dao.getUntouchedCountByUnit(track, unit)

    fun getWordsForSession(track: String, unit: Int?): Flow<List<Word>> =
        if (unit == null) dao.getAllWordsForReview(track) else dao.getWordsForReview(track, unit)

    suspend fun processAnswer(word: Word, isCorrect: Boolean, isQuiz: Boolean = false, isNotSure: Boolean = false) {
        val updated = SrsEngine.processAnswer(word, isCorrect, isQuiz, isNotSure)
        dao.updateWord(updated)
    }

    suspend fun getWordsToReview(track: String, limit: Int): List<Word> = dao.getWordsToReview(track, limit)
    suspend fun getHardestWordsForReview(track: String, limit: Int): List<Word> = dao.getHardestWordsForReview(track, limit)
    suspend fun countAll(): Int = dao.countAll()
}
