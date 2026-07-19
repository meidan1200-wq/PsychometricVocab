package com.example.psychometricvocab.ui.quiz

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.psychometricvocab.LocalAppState
import com.example.psychometricvocab.data.VocabDatabase
import com.example.psychometricvocab.data.VocabRepository
import com.example.psychometricvocab.theme.*
import com.example.psychometricvocab.ui.components.LanguageToggle
import com.example.psychometricvocab.ui.components.VocabTopBar
import com.example.psychometricvocab.ui.components.YellowButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext

@Composable
fun QuizSettingsScreen(
    onStartQuiz: (unit: Int?, unknownOnly: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appState = LocalAppState.current
    val isHebrew = appState.isHebrew
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUnit by remember { mutableStateOf<Int?>(null) }
    var unknownOnly by remember { mutableStateOf(false) }
    var units by remember { mutableStateOf(listOf<Int>()) }
    var hardestWordsCount by remember { mutableStateOf(0) }

    // Load units
    LaunchedEffect(appState.track) {
        val repo = VocabRepository(VocabDatabase.getInstance(context).wordDao())
        repo.getAllUnits(appState.track).collect { unitList -> units = unitList }
    }

    // Load hardest words count for selected unit
    LaunchedEffect(appState.track, selectedUnit) {
        val repo = VocabRepository(VocabDatabase.getInstance(context).wordDao())
        if (selectedUnit == null) {
            repo.getHardestWordsCount(appState.track).collect { count -> hardestWordsCount = count }
        } else {
            repo.getHardestWordsCountByUnit(appState.track, selectedUnit!!).collect { count -> hardestWordsCount = count }
        }
    }

    Scaffold(
        topBar = {
            VocabTopBar(
                title = if (isHebrew) "הגדרות מבחן" else "Quiz Settings",
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Language toggle
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isHebrew) "שפת המבחן" else "Quiz Language",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    LanguageToggle(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (isHebrew)
                            "עברית: מילים בעברית (פירוש בעברית)"
                        else
                            "English: English words (Hebrew definition)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // Unit selector
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isHebrew) "בחר יחידה" else "Select Unit",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))

                    // All option
                    UnitOptionRow(
                        label = if (isHebrew) "כל היחידות" else "All Units",
                        selected = selectedUnit == null,
                        onClick = { selectedUnit = null }
                    )
                    HorizontalDivider(color = DividerGray)

                    units.forEach { unit ->
                        UnitOptionRow(
                            label = if (isHebrew) "יחידה $unit" else "Unit $unit",
                            selected = selectedUnit == unit,
                            onClick = { selectedUnit = unit }
                        )
                        if (unit != units.last()) HorizontalDivider(color = DividerGray)
                    }
                }
            }

            // Word filter
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isHebrew) "סוג מילים" else "Word Filter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))

                    val hasEnoughHardWords = hardestWordsCount >= 20
                    if (!hasEnoughHardWords && unknownOnly) {
                        unknownOnly = false
                    }

                    FilterOptionRow(
                        label = if (isHebrew) "כל המילים" else "All words",
                        subtitle = if (isHebrew) "מבחן על כל המילים" else "Quiz on all words",
                        selected = !unknownOnly,
                        enabled = true,
                        onClick = { unknownOnly = false }
                    )
                    HorizontalDivider(color = DividerGray)
                    FilterOptionRow(
                        label = if (isHebrew) "רק מילים שלא ידעתי" else "Words I missed",
                        subtitle = if (!hasEnoughHardWords) {
                            if (isHebrew) "אין מספיק מילים קשות (צריך לפחות 20)" else "Not enough hard words (need 20+)"
                        } else {
                            if (isHebrew) "תרגול מילים קשות" else "Practice difficult words"
                        },
                        selected = unknownOnly,
                        enabled = hasEnoughHardWords,
                        onClick = { if (hasEnoughHardWords) unknownOnly = true }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Start button
            YellowButton(
                text = if (isHebrew) "התחל מבחן!" else "Start Quiz!",
                onClick = { onStartQuiz(selectedUnit, unknownOnly) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun UnitOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Yellow, unselectedColor = DividerGray)
        )
    }
}

@Composable
private fun FilterOptionRow(label: String, subtitle: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = TextPrimary.copy(alpha = alpha))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary.copy(alpha = alpha))
        }
        RadioButton(
            selected = selected,
            onClick = if (enabled) onClick else null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = Yellow, unselectedColor = DividerGray)
        )
    }
}
