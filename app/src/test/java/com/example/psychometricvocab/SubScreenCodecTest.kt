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
            QuizKey(unit = null, unknownOnly = false, isReviewMode = true)
        ).forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun unknownValueRestoresToHome() {
        assertEquals(null, SubScreenCodec.decode("something-else"))
    }
}
