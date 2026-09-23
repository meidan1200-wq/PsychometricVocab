package com.example.psychometricvocab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.psychometricvocab.theme.PsychometricVocabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
        // change, returning after the process was killed)
        if (savedInstanceState == null) {
            val updateManager = UpdateManager(this, "https://raw.githubusercontent.com/meidan1200-wq/PsychometricVocab/master/update.json")
            updateManager.checkForUpdates()
        }
    }
}
