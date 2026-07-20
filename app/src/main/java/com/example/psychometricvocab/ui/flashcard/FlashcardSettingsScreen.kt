package com.example.psychometricvocab.ui.flashcard

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.psychometricvocab.LocalAppState
import com.example.psychometricvocab.data.VocabDatabase
import com.example.psychometricvocab.data.VocabRepository
import com.example.psychometricvocab.theme.*
import com.example.psychometricvocab.ui.components.LanguageToggle
import com.example.psychometricvocab.ui.components.VocabTopBar
import com.example.psychometricvocab.ui.components.YellowButton

@Composable
fun FlashcardSettingsScreen(
    onStartFlashcards: (unit: Int?, mode: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appState = LocalAppState.current
    val isHebrew = appState.isHebrew
    val context = LocalContext.current

    var selectedUnit by remember { mutableStateOf<Int?>(null) }
    var flashcardMode by remember { mutableStateOf("sort") } // "sort" or "memorize"
    var units by remember { mutableStateOf(listOf<Int>()) }

    var untouchedCount by remember { mutableStateOf(0) }
    var hardCount by remember { mutableStateOf(0) }

    // Load units and counts
    LaunchedEffect(appState.track, selectedUnit) {
        val repo = VocabRepository(VocabDatabase.getInstance(context).wordDao())
        repo.getAllUnits(appState.track).collect { unitList -> units = unitList }
    }

    LaunchedEffect(appState.track, selectedUnit) {
        val repo = VocabRepository(VocabDatabase.getInstance(context).wordDao())
        if (selectedUnit == null) {
            repo.getAllUntouchedWords(appState.track).collect { untouchedCount = it.size }
        } else {
            repo.getUntouchedCountByUnit(appState.track, selectedUnit!!).collect { untouchedCount = it }
        }
    }

    LaunchedEffect(appState.track, selectedUnit) {
        val repo = VocabRepository(VocabDatabase.getInstance(context).wordDao())
        if (selectedUnit == null) {
            repo.getHardestWordsCount(appState.track).collect { hardCount = it }
        } else {
            repo.getHardestWordsCountByUnit(appState.track, selectedUnit!!).collect { hardCount = it }
        }
    }

    Scaffold(
        topBar = {
            VocabTopBar(
                title = if (isHebrew) "הגדרות כרטיסיות" else "Flashcard Settings",
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
                        text = if (isHebrew) "שפת לימוד" else "Study Language",
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

            // Session size filter
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isHebrew) "מצב למידה" else "Study Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))

                    FilterOptionRow(
                        label = if (isHebrew) "מיון מילים חדשות ($untouchedCount)" else "Sort New Words ($untouchedCount)",
                        subtitle = if (isHebrew) "מיין מילים שעוד לא נחשפת אליהן" else "Sort words you haven't seen yet",
                        selected = flashcardMode == "sort",
                        enabled = untouchedCount > 0,
                        onClick = { if (untouchedCount > 0) flashcardMode = "sort" }
                    )
                    HorizontalDivider(color = DividerGray)
                    FilterOptionRow(
                        label = if (isHebrew) "שינון מילים קשות ($hardCount)" else "Memorize Hard Words ($hardCount)",
                        subtitle = if (isHebrew) "משחק שינון למילים שהתקשית בהן" else "Memorization game for words you struggled with",
                        selected = flashcardMode == "memorize",
                        enabled = hardCount > 0,
                        onClick = { if (hardCount > 0) flashcardMode = "memorize" }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Start button
            YellowButton(
                text = if (isHebrew) "התחל ללמוד!" else "Start Learning!",
                onClick = { onStartFlashcards(selectedUnit, flashcardMode) },
                enabled = (flashcardMode == "sort" && untouchedCount > 0) || (flashcardMode == "memorize" && hardCount > 0),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (enabled) TextPrimary else TextHint)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = if (enabled) TextSecondary else TextHint)
        }
        RadioButton(
            selected = selected,
            enabled = enabled,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Yellow, unselectedColor = DividerGray)
        )
    }
}
