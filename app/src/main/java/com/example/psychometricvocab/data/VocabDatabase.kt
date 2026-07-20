package com.example.psychometricvocab.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

@Database(entities = [Word::class], version = 4, exportSchema = false)
abstract class VocabDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao

    companion object {
        @Volatile
        private var INSTANCE: VocabDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Merge duplicate definitions for "אֲלוּמָּה"
                val mergedDef = "1. חבילת שיבולים קצורות\n2. גלים המשודרים בטווח צר, קרן (אלומת אור - קרן אור)"
                database.execSQL("UPDATE words SET definition = '$mergedDef' WHERE word = 'אֲלוּמָּה' AND id = (SELECT MIN(id) FROM words WHERE word = 'אֲלוּמָּה')")
                database.execSQL("DELETE FROM words WHERE word = 'אֲלוּמָּה' AND id != (SELECT MIN(id) FROM words WHERE word = 'אֲלוּמָּה')")
            }
        }

        fun getInstance(context: Context): VocabDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VocabDatabase::class.java,
                    "vocab_database"
                )
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                
                // Seed database on first run or migration wipe
                CoroutineScope(Dispatchers.IO).launch {
                    if (instance.wordDao().countAll() == 0) {
                        VocabDataLoader.loadFromAssets(context, instance.wordDao())
                    }
                }
                instance
            }
        }
    }
}
