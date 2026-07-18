package com.example.psychometricvocab

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class UpdateManager(private val context: Context, private val updateJsonUrl: String) {

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var downloadId: Long = -1

    fun checkForUpdates() {
        executor.execute {
            try {
                val url = URL(updateJsonUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val jsonObject = JSONObject(response)
                    
                    val latestVersionCode = jsonObject.getInt("latest_version_code")
                    val apkUrl = jsonObject.getString("apk_url")
                    val releaseNotes = jsonObject.optString("release_notes", "A new version is available.")
                    
                    val currentVersionCode = getAppVersionCode()

                    if (latestVersionCode > currentVersionCode) {
                        handler.post {
                            showUpdateDialog(apkUrl, releaseNotes)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("UpdateManager", "Failed to check for updates", e)
            }
        }
    }

    private fun getAppVersionCode(): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun showUpdateDialog(apkUrl: String, releaseNotes: String) {
        AlertDialog.Builder(context)
            .setTitle("Update Available")
            .setMessage(releaseNotes)
            .setPositiveButton("Update") { _, _ ->
                downloadUpdate(apkUrl)
            }
            .setNegativeButton("Later", null)
            .setCancelable(false)
            .show()
    }

    private fun downloadUpdate(apkUrl: String) {
        try {
            val url = Uri.parse(apkUrl)
            val request = DownloadManager.Request(url)
                .setTitle("Downloading Update")
                .setDescription("Downloading new version of the app...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                // Stores in Android/data/<your.package.name>/files/Download/
                // This bypasses scoped storage restrictions
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "update.apk")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            
            // Delete previous update.apk if it exists
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk")
            if (file.exists()) file.delete()

            downloadId = downloadManager.enqueue(request)

            Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()

            // Register receiver for when the download completes
            val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Context.RECEIVER_NOT_EXPORTED
            } else {
                0
            }
            context.registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                receiverFlags
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed to start", Toast.LENGTH_SHORT).show()
            Log.e("UpdateManager", "Download failed", e)
        }
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) {
                installApk(context)
                try {
                    context.unregisterReceiver(this)
                } catch (e: Exception) {
                    // Ignore if already unregistered
                }
            }
        }
    }

    private fun installApk(context: Context) {
        try {
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk")
            if (!file.exists()) return

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to install APK", e)
            Toast.makeText(context, "Failed to start installation", Toast.LENGTH_LONG).show()
        }
    }
}
