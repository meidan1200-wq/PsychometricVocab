package com.example.psychometricvocab

import org.junit.Assert.assertEquals
import org.junit.Test

class SubScreenCodecTest {
    private fun roundTrip(screen: Any?) = SubScreenCodec.decode(SubScreenCodec.encode(screen))

    @Test
    fun allScreensSurviveSaveAndRestore() {
        listOf(
            null,
            QuizSettingsKey,
            AccountKey,
            FlashcardKey(unit = null),
            FlashcardKey(unit = 7, mode = "memorize"),
            QuizKey(unit = null, unknownOnly = false),
            QuizKey(unit = 3, unknownOnly = true),
            QuizKey(unit = null, unknownOnly = false, isReviewMode = true),
            QuizKey(unit = 4, unknownOnly = false, useSavedPreference = true),
            QuizKey(unit = null, unknownOnly = false, useSavedPreference = true)
        ).forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun quizKeySavedBeforeUseSavedPreferenceExistedStillRestores() {
        assertEquals(QuizKey(unit = 2, unknownOnly = true), SubScreenCodec.decode("quiz|2|true|false"))
    }

    @Test
    fun unknownValueRestoresToHome() {
        assertEquals(null, SubScreenCodec.decode("something-else"))
    }

    @Test
    fun settingsKeySavedBeforeItBecameTheProfileTabRestoresToHome() {
        // "settings" was SettingsKey's encoding before Settings became the Profile tab's own
        // root content; a process-death restore of that old value must not crash.
        assertEquals(null, SubScreenCodec.decode("settings"))
    }
}
