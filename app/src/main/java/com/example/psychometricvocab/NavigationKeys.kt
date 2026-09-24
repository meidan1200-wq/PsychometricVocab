package com.example.psychometricvocab

import kotlinx.serialization.Serializable

// Navigation keys - used as sub-screen markers in Navigation.kt
@Serializable data object QuizSettingsKey
@Serializable data class FlashcardKey(val unit: Int?, val mode: String = "sort")
@Serializable data class QuizKey(
    val unit: Int?,
    val unknownOnly: Boolean,
    val isReviewMode: Boolean = false,
    // Set by Home's unit-card shortcuts: resolve the type from the owner's saved per-unit
    // preference (falling back to "all words") instead of trusting `unknownOnly` above.
    val useSavedPreference: Boolean = false
)
@Serializable data object AccountKey
@Serializable data object SettingsKey

/**
 * Encodes the current sub-screen as a plain String so it can be stored in the
 * saved-instance-state Bundle. The key classes themselves are not Bundle-compatible,
 * and holding them in rememberSaveable without a Saver crashes the app as soon as
 * it is backgrounded (onSaveInstanceState) while a sub-screen is open.
 */
object SubScreenCodec {
    private const val NONE = "none"

    fun encode(screen: Any?): String = when (screen) {
        is FlashcardKey -> "flashcard|${screen.unit ?: ""}|${screen.mode}"
        is QuizKey -> "quiz|${screen.unit ?: ""}|${screen.unknownOnly}|${screen.isReviewMode}|${screen.useSavedPreference}"
        QuizSettingsKey -> "quizSettings"
        AccountKey -> "account"
        SettingsKey -> "settings"
        else -> NONE
    }

    fun decode(value: String): Any? {
        val parts = value.split('|')
        return when (parts[0]) {
            "flashcard" -> FlashcardKey(
                unit = parts.getOrNull(1)?.toIntOrNull(),
                mode = parts.getOrNull(2)?.takeIf { it.isNotEmpty() } ?: "sort"
            )
            "quiz" -> QuizKey(
                unit = parts.getOrNull(1)?.toIntOrNull(),
                unknownOnly = parts.getOrNull(2) == "true",
                isReviewMode = parts.getOrNull(3) == "true",
                useSavedPreference = parts.getOrNull(4) == "true"
            )
            "quizSettings" -> QuizSettingsKey
            "account" -> AccountKey
            "settings" -> SettingsKey
            else -> null
        }
    }
}
