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

    fun getWordsForSession(track: String, unit: Int?): Flow<List<Word>> =
        if (unit == null) dao.getAllWordsForReview(track) else dao.getWordsForReview(track, unit)

    suspend fun processAnswer(word: Word, isCorrect: Boolean) {
        val updated = SrsEngine.processAnswer(word, isCorrect)
        dao.updateWord(updated)
    }

    suspend fun getWordsToReview(track: String, limit: Int): List<Word> = dao.getWordsToReview(track, limit)
    suspend fun countAll(): Int = dao.countAll()
}
