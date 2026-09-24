package com.example.psychometricvocab.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(
    onGoToQuizShortcut: (Int?) -> Unit,
    onGoToProgress: (Int?) -> Unit,
    onGoToReview: () -> Unit,
    modifier: Modifier = Modifier,
    vm: HomeViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val activityDays by vm.activityDays.collectAsStateWithLifecycle()
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
                .padding(bottom = 14.dp)
        ) {
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 56.dp)) {
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

                Spacer(modifier = Modifier.height(14.dp))

                // ─── "מרכז המידע שלי" / My stats — one card, 3 columns ─────────
                // Replaces 3 separate stat cards. Deliberately just one label per column: the
                // mockup's "כרטיסיות לחזרה" title plus a second "0 לחזרה" line under it was
                // duplication the owner rejected.
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = White),
                    modifier = Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isHebrew) "מרכז המידע שלי" else "My stats",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(10.dp))
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

        Spacer(modifier = Modifier.height(6.dp))

        // ─── "מסלולי למידה" / Learning paths ────────────────────────────────
        // One horizontal scrolling row, ALL units — back from the vertical grid: the owner
        // wants Home to fit on one screen with no vertical scrolling, so units scroll
        // sideways instead of stacking. In Hebrew the row scrolls right-to-left (unit 1 at
        // the right) the same way the old pre-redesign Home did — LazyRow mirrors for RTL
        // automatically, same mechanism as the stat columns and the activity chart below.
        // "כל המילים" is its own bigger full-width card, below the row (not spliced into it).
        SectionTitle(if (isHebrew) "מסלולי למידה" else "Learning paths")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.units) { unit ->
                val (known, total) = state.unitStats[unit] ?: (0 to 0)
                UnitPathCard(
                    unit = unit,
                    known = known,
                    total = total,
                    isHebrew = isHebrew,
                    onClick = { onGoToQuizShortcut(unit) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        AllWordsCard(
            total = state.totalWords,
            isHebrew = isHebrew,
            onClick = { onGoToQuizShortcut(null) },
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // ─── "הפעילות היומית" / Daily activity ─────────────────────────────
        ActivityChartCard(
            days = activityDays,
            isHebrew = isHebrew,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
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

// Wider and shorter than the vertical grid's cards, so ~2 fit on screen with a peek of the
// third — the visual cue that the row scrolls. Icon sits beside the title on one line now
// instead of stacked above it.
@Composable
private fun UnitPathCard(
    unit: Int,
    known: Int,
    total: Int,
    isHebrew: Boolean,
    onClick: () -> Unit
) {
    val pct = if (total > 0) known.toFloat() / total else 0f
    Card(
        modifier = Modifier
            .width(172.dp)
            .shadow(3.dp, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Yellow.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = YellowDark, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = if (isHebrew) "יחידה $unit" else "Unit $unit",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
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
            Spacer(Modifier.height(8.dp))
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
private fun AllWordsCard(total: Int, isHebrew: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Yellow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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

// Oldest-to-newest in code order. The Row this renders inherits the screen's own
// LayoutDirection (RTL for Hebrew, same mechanism the stat columns above already rely on), so
// "today" lands at the reading-direction end automatically — no manual reversing needed.
@Composable
private fun ActivityChartCard(days: List<Pair<LocalDate, Int>>, isHebrew: Boolean, modifier: Modifier = Modifier) {
    val hasActivity = days.any { it.second > 0 }
    val maxCount = (days.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        modifier = modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = if (isHebrew) "הפעילות היומית" else "Daily activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            if (!hasActivity) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (isHebrew) "תרגל היום כדי לראות את הפעילות שלך" else "Practice today to see your activity",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                days.forEach { (date, count) ->
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            // A day with no activity still gets a thin flat sliver rather than
                            // vanishing entirely, so the row reads as a baseline, not a gap.
                            val fraction = (count.toFloat() / maxCount).coerceIn(0.08f, 1f)
                            Box(
                                modifier = Modifier
                                    .width(14.dp)
                                    .fillMaxHeight(fraction)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(if (count > 0) Yellow else SurfaceGray)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = dayLabel(date, isHebrew),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

private fun dayLabel(date: LocalDate, isHebrew: Boolean): String = if (isHebrew) {
    when (date.dayOfWeek) {
        DayOfWeek.SUNDAY -> "א׳"
        DayOfWeek.MONDAY -> "ב׳"
        DayOfWeek.TUESDAY -> "ג׳"
        DayOfWeek.WEDNESDAY -> "ד׳"
        DayOfWeek.THURSDAY -> "ה׳"
        DayOfWeek.FRIDAY -> "ו׳"
        DayOfWeek.SATURDAY -> "ש׳"
    }
} else {
    date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
}
