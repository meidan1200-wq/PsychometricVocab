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

@Database(entities = [Word::class], version = 2, exportSchema = false)
abstract class VocabDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao

    companion object {
        @Volatile
        private var INSTANCE: VocabDatabase? = null

        fun getInstance(context: Context): VocabDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VocabDatabase::class.java,
                    "vocab_database"
                )
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
