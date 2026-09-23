package com.example.psychometricvocab.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "words")
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
    val isKnown: Boolean = false
) {
    val cleanWord: String get() = stripHtml(word)
    val cleanDefinition: String get() = stripHtml(definition)
}

/** Html.fromHtml is expensive and runs on every recomposition; skip it for plain text. */
private fun stripHtml(text: String): String =
    if (text.indexOf('<') < 0 && text.indexOf('&') < 0) text
    else android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
