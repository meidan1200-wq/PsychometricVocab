package com.example.psychometricvocab.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

// exportSchema = true from v5 on (Phase 3): a Migration test needs the exported schema JSONs to
// validate against, and v4's own JSON (app/schemas/.../4.json) was generated once, after the
// fact, specifically so that test has a "before" schema to start from — exportSchema was off
// for every version up to and including the real, released v4.
@Database(entities = [Word::class], version = 5, exportSchema = true)
abstract class VocabDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao

    companion object {
        private const val TAG = "VocabDatabase"

        @Volatile
        private var INSTANCE: VocabDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Merge duplicate definitions for "אֲלוּמָּה"
                val mergedDef = "1. חבילת שיבולים קצורות\n2. גלים המשודרים בטווח צר, קרן (אלומת אור - קרן אור)"
                database.execSQL("UPDATE words SET definition = '$mergedDef' WHERE word = 'אֲלוּמָּה' AND id = (SELECT MIN(id) FROM words WHERE word = 'אֲלוּמָּה')")
                database.execSQL("DELETE FROM words WHERE word = 'אֲלוּמָּה' AND id != (SELECT MIN(id) FROM words WHERE word = 'אֲלוּמָּה')")
            }
        }

        // Phase 3 (cloud sync): adds updatedAt (last-write-wins merge clock) and stableId (the
        // per-word sync identity, backfilled separately — see VocabDataLoader.backfillStableIds).
        // Purely additive (ADD COLUMN, CREATE INDEX): it never rewrites or drops a row, so it
        // carries none of the risk fallbackToDestructiveMigration did.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE words ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE words ADD COLUMN stableId TEXT")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_words_stableId ON words(stableId)")
            }
        }

        fun getInstance(context: Context): VocabDatabase {
            return INSTANCE ?: synchronized(this) {
                val backupOk = BackupManager.backupBeforeMigrationIfNeeded(context)
                if (!backupOk) {
                    // Backup failed: log it loudly rather than fail silently. We still proceed —
                    // MIGRATION_4_5 only adds a column and an index, never rewrites or drops a
                    // row, so it doesn't itself put existing progress at risk the way a
                    // destructive migration would. A true non-migrating fallback path (a second,
                    // v4-only database + its own DAO, wired through every screen) would be a
                    // large, untested subsystem for a rare I/O failure; flagged in the HANDOFF
                    // for the Manager/owner to weigh in on rather than shipped unverified.
                    Log.e(TAG, "Pre-migration backup failed (see BackupManager log above); proceeding with migration anyway.")
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VocabDatabase::class.java,
                    "vocab_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                .build()
                INSTANCE = instance

                // Seed database on first run; backfill stable sync ids on every other run (cheap
                // no-op once every row has one).
                CoroutineScope(Dispatchers.IO).launch {
                    if (instance.wordDao().countAll() == 0) {
                        VocabDataLoader.loadFromAssets(context, instance.wordDao())
                    } else {
                        VocabDataLoader.backfillStableIds(context, instance.wordDao())
                    }
                }
                instance
            }
        }
    }
}
