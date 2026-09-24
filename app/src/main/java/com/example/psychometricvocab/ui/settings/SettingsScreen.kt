package com.example.psychometricvocab.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.psychometricvocab.LocalAppState
import com.example.psychometricvocab.data.AppPreferences
import com.example.psychometricvocab.theme.*
import com.example.psychometricvocab.ui.components.VocabTopBar

/**
 * The Profile tab's content (bottom nav index 0) — not a pushed sub-screen, so `onBack` is only
 * non-null on the rare other path here (there isn't one right now, but VocabTopBar supports it).
 * Account is one entry inside it, reached as a real sub-screen; back from Account clears that
 * sub-screen and lands back on this tab. Preferences (currently just "Auto pass") is meant to grow.
 */
@Composable
fun SettingsScreen(
    onGoToAccount: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val appState = LocalAppState.current
    val isHebrew = appState.isHebrew
    val context = LocalContext.current
    val appPrefs = remember { AppPreferences(context) }

    var autoPass by remember { mutableStateOf(appPrefs.isAutoPassEnabled()) }

    Scaffold(
        topBar = {
            VocabTopBar(
                title = if (isHebrew) "הגדרות" else "Settings",
                onBack = onBack
            )
        },
        containerColor = OffWhite
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            SectionLabel(if (isHebrew) "חשבון" else "Account")
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onGoToAccount)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = Yellow)
                        Text(
                            text = if (isHebrew) "חשבון" else "Account",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = TextHint,
                        modifier = Modifier.graphicsLayer { rotationZ = if (isHebrew) 180f else 0f }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            SectionLabel(if (isHebrew) "העדפות" else "Preferences")
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = if (isHebrew) "מעבר אוטומטי" else "Auto pass",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isHebrew)
                                "עבור אוטומטית לשאלה הבאה בחידון אחרי שעונים"
                            else
                                "Automatically move to the next quiz question after answering",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = autoPass,
                        onCheckedChange = {
                            autoPass = it
                            appPrefs.setAutoPassEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Yellow,
                            checkedTrackColor = Yellow.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = TextSecondary,
        modifier = Modifier.padding(start = 4.dp)
    )
}
