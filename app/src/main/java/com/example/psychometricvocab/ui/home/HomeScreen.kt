package com.example.psychometricvocab.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
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

@Composable
fun HomeScreen(
    onGoToQuizShortcut: (Int?) -> Unit,
    onGoToProgress: (Int?) -> Unit,
    onGoToReview: () -> Unit,
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
                .padding(bottom = 20.dp)
        ) {
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 72.dp)) {
                // Greeting, subtitle and the Hebrew/English pill — unchanged from before the
                // redesign, same position and style (the owner's mockup showed a different
                // "HE | EN" switch here; that part of the mockup was not taken).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = if (isHebrew) "ברוך שובך! 👋" else "Welcome back! 👋",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isHebrew) "מוכן ללמוד מילים חדשות?" else "Ready to learn new words?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    LanguageToggle()
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ─── "מרכז המידע שלי" / My stats — one card, 3 columns ─────────
                // Replaces 3 separate stat cards. Deliberately just one label per column: the
                // mockup's "כרטיסיות לחזרה" title plus a second "0 לחזרה" line under it was
                // duplication the owner rejected.
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = White),
                    modifier = Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = if (isHebrew) "מרכז המידע שלי" else "My stats",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatColumn(
                                icon = Icons.Filled.CheckCircle,
                                value = state.knownWords.toString(),
                                label = if (isHebrew) "ידועות" else "Known",
                                color = CorrectGreen,
                                modifier = Modifier.weight(1f),
                                onClick = { onGoToProgress(-1) }
                            )
                            StatColumn(
                                icon = Icons.Filled.LibraryBooks,
                                value = state.totalWords.toString(),
                                label = if (isHebrew) "סה\"כ מילים" else "Total Words",
                                color = Yellow,
                                modifier = Modifier.weight(1f)
                            )
                            StatColumn(
                                icon = Icons.Filled.Refresh,
                                value = state.upcomingReviews.toString(),
                                label = if (isHebrew) "לחזרה" else "To review",
                                color = WrongRed,
                                modifier = Modifier.weight(1f),
                                onClick = onGoToReview
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ─── "מסלולי למידה" / Learning paths ────────────────────────────────
        // 2 unit cards per row, ALL units; "כל המילים" is a bigger full-width card placed
        // after the first row, with the remaining units in rows of two below it — matches the
        // mockup's layout (not its colors: yellow/white only, no salmon or purple).
        SectionTitle(if (isHebrew) "מסלולי למידה" else "Learning paths")
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val unitRows = state.units.chunked(2)
            unitRows.firstOrNull()?.let { firstRow ->
                UnitRow(
                    units = firstRow,
                    unitStats = state.unitStats,
                    isHebrew = isHebrew,
                    onClick = onGoToQuizShortcut
                )
            }
            AllWordsCard(
                total = state.totalWords,
                isHebrew = isHebrew,
                onClick = { onGoToQuizShortcut(null) }
            )
            unitRows.drop(1).forEach { row ->
                UnitRow(
                    units = row,
                    unitStats = state.unitStats,
                    isHebrew = isHebrew,
                    onClick = onGoToQuizShortcut
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun UnitRow(
    units: List<Int>,
    unitStats: Map<Int, Pair<Int, Int>>,
    isHebrew: Boolean,
    onClick: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        units.forEach { unit ->
            val (known, total) = unitStats[unit] ?: (0 to 0)
            UnitPathCard(
                unit = unit,
                known = known,
                total = total,
                isHebrew = isHebrew,
                onClick = { onClick(unit) },
                modifier = Modifier.weight(1f)
            )
        }
        // Odd unit out in this row: keep the other card at half width instead of full width.
        if (units.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
        }
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
private fun StatColumn(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Column(
        modifier = colModifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = TextPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary, textAlign = TextAlign.Center, maxLines = 2, minLines = 1, lineHeight = 14.sp)
    }
}

@Composable
private fun UnitPathCard(
    unit: Int,
    known: Int,
    total: Int,
    isHebrew: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pct = if (total > 0) known.toFloat() / total else 0f
    Card(
        modifier = modifier
            .shadow(3.dp, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Yellow.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = YellowDark, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (isHebrew) "יחידה $unit" else "Unit $unit",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = TextPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(SurfaceGray)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pct.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(Yellow)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (isHebrew) "התחל" else "Start",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = YellowDark
            )
        }
    }
}

@Composable
private fun AllWordsCard(total: Int, isHebrew: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Yellow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = if (isHebrew) "כל המילים" else "All Words",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$total ${if (isHebrew) "מילים" else "words"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary.copy(alpha = 0.75f)
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
