package com.example.psychometricvocab.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.psychometricvocab.LocalAppState
import com.example.psychometricvocab.theme.*
import com.example.psychometricvocab.ui.components.LanguageToggle
import com.example.psychometricvocab.ui.components.YellowButton

@Composable
fun HomeScreen(
    onGoToFlashcard: (Int?) -> Unit,
    onGoToQuiz: () -> Unit,
    onGoToProgress: () -> Unit,
    modifier: Modifier = Modifier,
    vm: HomeViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val appState = LocalAppState.current
    val isHebrew = appState.isHebrew

    LaunchedEffect(appState.track) {
        vm.loadData(appState.track)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OffWhite)
            .verticalScroll(rememberScrollState())
    ) {
        // ─── Header gradient section ───────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Yellow.copy(alpha = 0.15f), OffWhite))
                )
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isHebrew) "שלום 👋" else "Hello 👋",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                        Text(
                            text = if (isHebrew) "מוכן ללמוד?" else "Ready to learn?",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    }
                    LanguageToggle()
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        icon = Icons.Filled.CheckCircle,
                        value = state.knownWords.toString(),
                        label = if (isHebrew) "ידועות" else "Known",
                        color = CorrectGreen,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Filled.LibraryBooks,
                        value = state.totalWords.toString(),
                        label = if (isHebrew) "סה\"כ מילים" else "Total Words",
                        color = Yellow,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = Icons.Filled.Refresh,
                        value = state.upcomingReviews.toString(),
                        label = if (isHebrew) "לחזרה" else "To review",
                        color = WrongRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ─── Units section ─────────────────────────────────────────────────
        if (state.units.isNotEmpty()) {
            SectionTitle(if (isHebrew) "יחידות לימוד" else "Study Units")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                // All units card
                item {
                    UnitCard(
                        title = if (isHebrew) "כל המילים" else "All Words",
                        subtitle = "${state.totalWords} ${if (isHebrew) "מילים" else "words"}",
                        color = Yellow,
                        onClick = { onGoToFlashcard(null) }
                    )
                }
                items(state.units) { unit ->
                    UnitCard(
                        title = if (isHebrew) "יחידה $unit" else "Unit $unit",
                        subtitle = if (isHebrew) "לחץ להתחיל" else "Tap to start",
                        color = listOf(
                            Color(0xFF6C63FF),
                            Color(0xFF00BCD4),
                            Color(0xFFFF7043)
                        ).getOrElse(unit - 1) { Yellow },
                        onClick = { onGoToFlashcard(unit) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ─── Quick actions ────────────────────────────────────────────────
        SectionTitle(if (isHebrew) "פעולות מהירות" else "Quick Actions")
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionCard(
                icon = Icons.Filled.SwipeLeft,
                title = if (isHebrew) "כרטיסיות לימוד" else "Flashcards",
                subtitle = if (isHebrew) "למד עם החלקה" else "Learn with swipe",
                color = Yellow,
                onClick = { onGoToFlashcard(null) }
            )
            ActionCard(
                icon = Icons.Filled.Quiz,
                title = if (isHebrew) "חידון מילים" else "Multiple Choice Quiz",
                subtitle = if (isHebrew) "בחן את עצמך" else "Test your knowledge",
                color = Color(0xFF6C63FF),
                onClick = onGoToQuiz
            )
            ActionCard(
                icon = Icons.Filled.BarChart,
                title = if (isHebrew) "התקדמות שלי" else "My Progress",
                subtitle = if (isHebrew) "ראה כמה למדת" else "See how far you've come",
                color = CorrectGreen,
                onClick = onGoToProgress
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = TextPrimary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun UnitCard(title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .height(100.dp)
            .shadow(4.dp, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextPrimary.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextHint)
        }
    }
}
