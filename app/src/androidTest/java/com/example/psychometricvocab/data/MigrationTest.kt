package com.example.psychometricvocab.data

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 3 safety requirement: an upgrade from released v1.1.5 (schema v4) must preserve every
 * row and every progress field exactly — this is the owner's #1 concern (two months of real
 * progress) made concrete as a test. Instrumented (needs a device/emulator): run via
 * `gradlew.bat connectedDebugAndroidTest`, not the JVM unit test task.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test-4-5"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VocabDatabase::class.java
    )

    @Test
    fun migrate4To5_preservesEveryProgressFieldAndRowCount() {
        // Two rows with real, non-default progress (not just empty defaults) — this is what
        // "two months of real progress must survive" actually means for a test.
        helper.createDatabase(dbName, 4).apply {
            execSQL(
                "INSERT INTO words (id, track, word, definition, unit, srsScore, easeFactor, " +
                    "interval, nextReviewDate, correctCount, wrongCount, isKnown) VALUES " +
                    "(1, 'hebrew', 'מילה', 'פירוש', 1, 2.67, 2.3, 6, 1700000000000, 4, 1, 1)"
            )
            execSQL(
                "INSERT INTO words (id, track, word, definition, unit, srsScore, easeFactor, " +
                    "interval, nextReviewDate, correctCount, wrongCount, isKnown) VALUES " +
                    "(2, 'english', 'word', 'meaning', 2, 0.34, 1.3, 1, 1700000001000, 0, 1, 0)"
            )
            close()
        }

        // Re-open at version 5: runs MIGRATION_4_5 and validates the resulting schema (columns,
        // types, the stableId index) against the exported 5.json.
        val db = helper.runMigrationsAndValidate(dbName, 5, true, VocabDatabase.MIGRATION_4_5)

        val cursor = db.query("SELECT * FROM words ORDER BY id ASC")
        assertEquals("row count must be unchanged by a purely additive migration", 2, cursor.count)

        cursor.moveToFirst()
        assertRow(
            cursor, id = 1, track = "hebrew", word = "מילה", definition = "פירוש", unit = 1,
            srsScore = 2.67f, easeFactor = 2.3f, interval = 6, nextReviewDate = 1700000000000L,
            correctCount = 4, wrongCount = 1, isKnown = true
        )

        cursor.moveToNext()
        assertRow(
            cursor, id = 2, track = "english", word = "word", definition = "meaning", unit = 2,
            srsScore = 0.34f, easeFactor = 1.3f, interval = 1, nextReviewDate = 1700000001000L,
            correctCount = 0, wrongCount = 1, isKnown = false
        )

        cursor.close()
        db.close()
    }

    private fun assertRow(
        cursor: Cursor,
        id: Int, track: String, word: String, definition: String, unit: Int,
        srsScore: Float, easeFactor: Float, interval: Int, nextReviewDate: Long,
        correctCount: Int, wrongCount: Int, isKnown: Boolean
    ) {
        assertEquals(id, cursor.getInt(cursor.getColumnIndexOrThrow("id")))
        assertEquals(track, cursor.getString(cursor.getColumnIndexOrThrow("track")))
        assertEquals(word, cursor.getString(cursor.getColumnIndexOrThrow("word")))
        assertEquals(definition, cursor.getString(cursor.getColumnIndexOrThrow("definition")))
        assertEquals(unit, cursor.getInt(cursor.getColumnIndexOrThrow("unit")))
        assertEquals(srsScore, cursor.getFloat(cursor.getColumnIndexOrThrow("srsScore")), 0.001f)
        assertEquals(easeFactor, cursor.getFloat(cursor.getColumnIndexOrThrow("easeFactor")), 0.001f)
        assertEquals(interval, cursor.getInt(cursor.getColumnIndexOrThrow("interval")))
        assertEquals(nextReviewDate, cursor.getLong(cursor.getColumnIndexOrThrow("nextReviewDate")))
        assertEquals(correctCount, cursor.getInt(cursor.getColumnIndexOrThrow("correctCount")))
        assertEquals(wrongCount, cursor.getInt(cursor.getColumnIndexOrThrow("wrongCount")))
        assertEquals(if (isKnown) 1 else 0, cursor.getInt(cursor.getColumnIndexOrThrow("isKnown")))
        // New, additive columns: untouched old rows must get the safe defaults, not garbage.
        assertEquals(0L, cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")))
        assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("stableId")))
    }
}
