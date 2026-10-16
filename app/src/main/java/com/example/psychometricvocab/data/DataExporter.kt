package com.example.psychometricvocab.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// One-time tool for the v1.1.6 cloud-sync migration test: lets the owner pull his own real
// progress data off his phone without handing the device to anyone else. Remove after the
// migration is verified and shipped.
object DataExporter {

    private val DB_FILE_NAMES = listOf("vocab_database", "vocab_database-wal", "vocab_database-shm")
    private val PREFS_FILE_NAMES = listOf("app_prefs.xml", "quiz_prefs.xml", "activity_log.xml")

    fun exportToZip(context: Context): Uri {
        // Flush the WAL into the main db file first so the copy is a consistent snapshot,
        // not a database file that's missing its most recent writes.
        try {
            VocabDatabase.getInstance(context).openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(FULL)", emptyArray()).close()
        } catch (e: Exception) {
            // Best-effort: if this fails we still export whatever is on disk.
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("No external files dir available")
        if (!outDir.exists()) outDir.mkdirs()
        val zipFile = File(outDir, "psychvocab_export_$timestamp.zip")
        if (zipFile.exists()) zipFile.delete()

        val dbDir = context.getDatabasePath(DB_FILE_NAMES[0]).parentFile
        val prefsDir = File(context.filesDir.parentFile, "shared_prefs")

        ZipOutputStream(zipFile.outputStream()).use { zip ->
            for (name in DB_FILE_NAMES) {
                val f = File(dbDir, name)
                if (f.exists()) addFileToZip(zip, f, "db/$name")
            }
            for (name in PREFS_FILE_NAMES) {
                val f = File(prefsDir, name)
                if (f.exists()) addFileToZip(zip, f, "shared_prefs/$name")
            }
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
    }

    private fun addFileToZip(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }
}
