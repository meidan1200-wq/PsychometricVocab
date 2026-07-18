package com.example.psychometricvocab

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.LayoutDirection

/** Represents which language the UI is currently in */
enum class AppLanguage(val layoutDirection: LayoutDirection, val displayName: String) {
    HEBREW(LayoutDirection.Rtl, "עברית"),
    ENGLISH(LayoutDirection.Ltr, "English")
}

/** Global app state — language determines RTL/LTR layout direction throughout the app */
class AppState {
    var language by mutableStateOf(AppLanguage.HEBREW)

    fun toggleLanguage() {
        language = if (language == AppLanguage.HEBREW) AppLanguage.ENGLISH else AppLanguage.HEBREW
    }

    val isHebrew get() = language == AppLanguage.HEBREW
    val layoutDirection get() = language.layoutDirection

    /** The database track corresponding to the current language */
    val track: String get() = if (isHebrew) "hebrew" else "english"
}

val LocalAppState = compositionLocalOf { AppState() }
