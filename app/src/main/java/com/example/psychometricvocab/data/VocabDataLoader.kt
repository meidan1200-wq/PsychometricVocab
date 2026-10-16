package com.example.psychometricvocab.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.InputStreamReader

data class VocabJson(val flat: List<VocabFlatEntry>)
data class VocabFlatEntry(
    val language: String,
    val unit: Int,
    val word: String,
    val definition: String,
    // Stable sync id (Phase 3): generated once by tools/generate_stable_ids.py into the asset
    // itself. Never regenerated at runtime — the whole point is that it survives reinstalls.
    val id: String
)

object VocabDataLoader {
    private const val TAG = "VocabDataLoader"
    private const val ASSET_NAME = "psychometric_vocab_CLEAN.json"

    fun trackFor(language: String): String = if (language == "עברית") "hebrew" else "english"

    private fun readFlatEntries(context: Context): List<VocabFlatEntry> {
        val inputStream = context.assets.open(ASSET_NAME)
        val vocabJson = InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
            Gson().fromJson(reader, VocabJson::class.java)
        }
        return vocabJson.flat
    }

    suspend fun loadFromAssets(context: Context, dao: WordDao) {
        try {
            Log.d(TAG, "Starting to load vocabulary data from assets")
            val flat = readFlatEntries(context)

            val wordsToInsert = flat.map { entry ->
                Word(
                    track = trackFor(entry.language),
                    word = entry.word,
                    definition = entry.definition,
                    unit = entry.unit,
                    stableId = entry.id
                )
            }

            Log.d(TAG, "Parsed ${wordsToInsert.size} words. Inserting into database...")
            // A single insertAll runs in one transaction: if the app is killed mid-seed nothing is
            // committed and seeding simply runs again next launch. (Chunked inserts could leave a
            // partial database, and the "count == 0" check would then never re-seed it.)
            dao.insertAll(wordsToInsert)
            Log.d(TAG, "Vocabulary load complete!")

        } catch (e: Exception) {
            Log.e(TAG, "Error loading vocabulary data", e)
        }
    }

    /**
     * Fills in stableId for rows left over from before v5 (a fresh install already gets it
     * directly from [loadFromAssets]). Matches each DB row to the asset entry with the same
     * (track, unit, word, definition) — the same tuple `tools/generate_stable_ids.py` hashed to
     * produce that entry's id — and only ever sets stableId where it's still NULL; nothing
     * about progress is touched. Rows that don't match anything in the current asset (the asset
     * changed since the row was first inserted) are counted and logged, never deleted: the
     * owner's progress on them is still there, just not sync-eligible until matched.
     */
    suspend fun backfillStableIds(context: Context, dao: WordDao) {
        val before = dao.countMissingStableId()
        if (before == 0) return
        try {
            val flat = readFlatEntries(context)
            var attempted = 0
            flat.forEach { entry ->
                attempted += dao.backfillStableId(
                    track = trackFor(entry.language),
                    unit = entry.unit,
                    word = entry.word,
                    definition = entry.definition,
                    stableId = entry.id
                )
            }
            val after = dao.countMissingStableId()
            Log.i(TAG, "stableId backfill: $attempted row(s) matched, $after row(s) still unmatched (kept)")
        } catch (e: Exception) {
            Log.e(TAG, "stableId backfill failed", e)
        }
    }
}
