package com.example.psychometricvocab

import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import android.os.StrictMode
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.psychometricvocab.data.StartupTrace
import com.example.psychometricvocab.data.VocabDatabase
import com.example.psychometricvocab.data.sync.SyncCoordinator
import com.example.psychometricvocab.theme.PsychometricVocabTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTrace.mark("MainActivity.onCreate begin")
        // Debug builds only: log (don't crash on) any disk or network access on the main thread, so
        // the next startup stall names its cause in logcat (tag StrictMode).
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build()
            )
        }
        super.onCreate(savedInstanceState)

        // Everything slow that the first screen doesn't need to wait for runs off the main thread,
        // started before the UI so it is done (or well under way) when the screens ask for it: the
        // database open (a raw-SQLite version check plus Room), and Firebase Auth/Firestore set-up
        // inside SyncCoordinator. On a fresh install the app code is still un-optimised, which is
        // when doing this on the main thread stalled the first launch long enough to ANR.
        val app = applicationContext
        val freshStart = savedInstanceState == null
        CoroutineScope(Dispatchers.Default).launch {
            StartupTrace.time("VocabDatabase.getInstance (warm-up)") { VocabDatabase.getInstance(app) }
            val sync = StartupTrace.time("SyncCoordinator.get (warm-up)") { SyncCoordinator.get(app) }
            // Quiet catch-up with the cloud backup if already signed in (no-op otherwise).
            if (freshStart) sync.onAppStart()
        }

        // The app only has a light theme, but the default enableEdgeToEdge() picks light/dark
        // system-bar icons from the PHONE's night mode: on a device in dark mode (the owner's
        // Samsung) that gave white clock/battery icons on our light yellow header. Force dark
        // icons on transparent bars, always, since every screen's top is light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            PsychometricVocabTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }

        // Check for updates on a fresh start only, not on every recreation (rotation, theme
        // change, returning after the process was killed). The network call runs on its own thread.
        if (savedInstanceState == null) {
            val updateManager = UpdateManager(this, "https://raw.githubusercontent.com/meidan1200-wq/PsychometricVocab/master/update.json")
            updateManager.checkForUpdates()
        }
        StartupTrace.mark("MainActivity.onCreate end")
    }
}
