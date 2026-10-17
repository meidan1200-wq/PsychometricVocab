package com.example.psychometricvocab.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "words", indices = [Index(value = ["stableId"])])
data class Word(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val track: String,           // "hebrew" or "english"
    val word: String,            // Front of flashcard (Hebrew word OR English word)
    val definition: String,      // Back of flashcard (always Hebrew definition)
    val unit: Int,
    // spaced repetition fields
    val srsScore: Float = 0f,
    val easeFactor: Float = 2.5f,
    val interval: Int = 1,           // days until next review
    val nextReviewDate: Long = 0L,   // epoch millis
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val isKnown: Boolean = false,
    // Added in v5 for cloud sync (Phase 3). updatedAt is the wall-clock time of the last real
    // change to this row's progress, used for last-write-wins merging — processAnswer sets it,
    // resetAllProgress sets it back to 0 so a reset can never beat real progress from another
    // device. stableId is this word's permanent sync identity: a hash of
    // track|unit|word|definition generated once into the bundled asset, because the Room `id`
    // is per-install and not stable across devices/reinstalls.
    val updatedAt: Long = 0L,
    val stableId: String? = null
) {
    val cleanWord: String get() = stripHtml(word)
    val cleanDefinition: String get() = stripHtml(definition)
}

/** Html.fromHtml is expensive and runs on every recomposition; skip it for plain text. */
private fun stripHtml(text: String): String =
    if (text.indexOf('<') < 0 && text.indexOf('&') < 0) text
    else android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
