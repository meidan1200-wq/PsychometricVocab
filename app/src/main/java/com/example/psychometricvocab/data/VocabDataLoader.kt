package com.example.psychometricvocab.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.InputStreamReader

data class VocabJson(val flat: List<VocabFlatEntry>)
data class VocabFlatEntry(val language: String, val unit: Int, val word: String, val definition: String)

object VocabDataLoader {
    private const val TAG = "VocabDataLoader"

    suspend fun loadFromAssets(context: Context, dao: WordDao) {
        try {
            Log.d(TAG, "Starting to load vocabulary data from assets")
            val inputStream = context.assets.open("psychometric_vocab_CLEAN.json")
            val vocabJson = InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
                Gson().fromJson(reader, VocabJson::class.java)
            }

            val wordsToInsert = vocabJson.flat.map { entry ->
                val track = if (entry.language == "עברית") "hebrew" else "english"
                Word(
                    track = track,
                    word = entry.word,
                    definition = entry.definition,
                    unit = entry.unit
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
}
