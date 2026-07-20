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
    val cleanWord: String get() = android.text.Html.fromHtml(word, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
    val cleanDefinition: String get() = android.text.Html.fromHtml(definition, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
}
