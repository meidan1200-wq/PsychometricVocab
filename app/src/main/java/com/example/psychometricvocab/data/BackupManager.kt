package com.example.psychometricvocab.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

/**
 * Pre-migration safety net (the owner's #1 concern for Phase 3: two months of real progress
 * must never be lost). Before Room ever opens a database file older than [TARGET_VERSION] —
 * which is the moment it would run a Migration — this copies the raw DB files and every
 * SharedPreferences file to external storage, reachable over USB, so there's a manual recovery
 * path even if a migration bug or a mid-write power loss corrupts the live file.
 *
 * This runs with raw file I/O, not through Room: Room checks the on-disk schema version and
 * runs migrations as soon as anything touches the database (as early as the seed-check that
 * follows `Room.databaseBuilder(...).build()`), so by the time Room is involved it's too late
 * to back up the pre-migration bytes.
 */
object BackupManager {
    private const val TAG = "BackupManager"
    private const val DB_NAME = "vocab_database"
    const val TARGET_VERSION = 5

    /**
     * Copies the DB + prefs if (and only if) a migration to [TARGET_VERSION] is about to run
     * and no backup for it exists yet. Returns true when it's safe to proceed with Room's
     * normal (migrating) open: nothing needed backing up, a backup already exists from an
     * earlier attempt, or the backup just succeeded. Returns false only when a backup was
     * required and failed.
     */
    fun backupBeforeMigrationIfNeeded(context: Context): Boolean {
        return try {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) return true // fresh install: nothing to protect yet

            val oldVersion = readUserVersion(dbFile)
            // Already current (or newer — shouldn't happen), or the version couldn't be read at
            // all: either way there's nothing this function can safely decide, so let Room's own
            // open proceed rather than block the app on a version check that itself failed.
            if (oldVersion <= 0 || oldVersion >= TARGET_VERSION) return true

            val backupsRoot = File(context.getExternalFilesDir(null), "backups")
            val alreadyBackedUp = backupsRoot.listFiles { f ->
                f.isDirectory && f.name.startsWith("pre-v$TARGET_VERSION-")
            }?.isNotEmpty() == true
            if (alreadyBackedUp) {
                Log.d(TAG, "pre-v$TARGET_VERSION backup already exists, skipping")
                return true
            }

            val dest = File(backupsRoot, "pre-v$TARGET_VERSION-${System.currentTimeMillis()}")
            if (!dest.mkdirs()) {
                Log.e(TAG, "Could not create backup directory $dest")
                return false
            }

            if (!backupDatabaseFiles(dbFile, dest)) return false
            if (!backupSharedPrefs(context, dest)) return false

            Log.i(TAG, "Pre-v$TARGET_VERSION backup (from v$oldVersion) written to $dest")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Backup before migration failed", e)
            false
        }
    }

    private fun backupDatabaseFiles(dbFile: File, dest: File): Boolean {
        val dbDir = dbFile.parentFile ?: return false
        val databasesDest = File(dest, "databases")
        if (!databasesDest.mkdirs()) return false
        // The main file plus its WAL/SHM siblings: in write-ahead-logging mode, recently
        // committed pages can live only in -wal until the next checkpoint.
        listOf(DB_NAME, "$DB_NAME-wal", "$DB_NAME-shm").forEach { name ->
            val src = File(dbDir, name)
            if (src.exists() && !copyFile(src, File(databasesDest, name))) return false
        }
        return true
    }

    private fun backupSharedPrefs(context: Context, dest: File): Boolean {
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        if (!prefsDir.exists()) return true // nothing to copy, not a failure
        val prefsDest = File(dest, "shared_prefs")
        if (!prefsDest.mkdirs()) return false
        prefsDir.listFiles()?.forEach { f ->
            if (!copyFile(f, File(prefsDest, f.name))) return false
        }
        return true
    }

    private fun readUserVersion(dbFile: File): Int = try {
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
    } catch (e: Exception) {
        Log.e(TAG, "Could not read DB version from ${dbFile.path}", e)
        -1
    }

    private fun copyFile(src: File, dst: File): Boolean = try {
        src.inputStream().use { input -> dst.outputStream().use { output -> input.copyTo(output) } }
        true
    } catch (e: Exception) {
        Log.e(TAG, "Copy failed: ${src.path} -> ${dst.path}", e)
        false
    }
}
