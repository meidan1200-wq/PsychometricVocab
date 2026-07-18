package com.example.psychometricvocab.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query("SELECT * FROM words WHERE track = :track ORDER BY unit ASC, id ASC")
    fun getAllWords(track: String): Flow<List<Word>>

    @Query("SELECT * FROM words WHERE track = :track AND unit = :unit ORDER BY id ASC")
    fun getWordsByUnit(track: String, unit: Int): Flow<List<Word>>

    @Query("SELECT * FROM words WHERE track = :track AND unit = :unit ORDER BY srsScore ASC, nextReviewDate ASC")
    fun getWordsForReview(track: String, unit: Int): Flow<List<Word>>

    @Query("SELECT * FROM words WHERE track = :track ORDER BY srsScore ASC, nextReviewDate ASC")
    fun getAllWordsForReview(track: String): Flow<List<Word>>

    @Query("SELECT DISTINCT unit FROM words WHERE track = :track ORDER BY unit ASC")
    fun getAllUnits(track: String): Flow<List<Int>>

    @Query("SELECT COUNT(*) FROM words WHERE track = :track")
    fun getTotalCount(track: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM words WHERE track = :track AND isKnown = 1")
    fun getKnownCount(track: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM words WHERE track = :track AND isKnown = 0")
    fun getUnknownCount(track: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM words WHERE track = :track AND unit = :unit")
    fun getCountByUnit(track: String, unit: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM words WHERE track = :track AND unit = :unit AND isKnown = 1")
    fun getKnownCountByUnit(track: String, unit: Int): Flow<Int>

    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getWordById(id: Int): Word?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(words: List<Word>)

    @Update
    suspend fun updateWord(word: Word)

    @Query("SELECT COUNT(*) FROM words")
    suspend fun countAll(): Int

    @Query("SELECT * FROM words WHERE track = :track AND isKnown = 0 ORDER BY srsScore ASC LIMIT :limit")
    suspend fun getWordsToReview(track: String, limit: Int): List<Word>

    @Query("SELECT * FROM words WHERE track = :track ORDER BY nextReviewDate ASC LIMIT :limit")
    fun getUpcomingReviews(track: String, limit: Int): Flow<List<Word>>
}
