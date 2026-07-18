package com.example.psychometricvocab

import kotlinx.serialization.Serializable

// Navigation keys - used as sub-screen markers in Navigation.kt
@Serializable data object QuizSettingsKey
@Serializable data class FlashcardKey(val unit: Int?, val showAll: Boolean = false)
@Serializable data class QuizKey(val unit: Int?, val unknownOnly: Boolean)
