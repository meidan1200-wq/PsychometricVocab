package com.example.psychometricvocab.ui.progress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.psychometricvocab.LocalAppState
import com.example.psychometricvocab.data.Word
import com.example.psychometricvocab.theme.*
import com.example.psychometricvocab.ui.components.VocabTopBar

@Composable
fun ProgressScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    vm: ProgressViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val appState = LocalAppState.current
    val isHebrew = appState.isHebrew

    LaunchedEffect(appState.track) {
        vm.loadData(appState.track)
    }

    Scaffold(
        topBar = {
            VocabTopBar(
                title = if (isHebrew) "התקדמות שלי" else "My Progress",
                onBack = onBack
            )
        },
        containerColor = OffWhite
    ) { padding ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Circular Progress ──────────────────────────────────────────
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = White),
                    modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isHebrew) "סה\"כ התקדמות" else "Overall Progress",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(16.dp))

                        // Circular progress indicator
                        val pct = if (state.total > 0) state.known.toFloat() / state.total else 0f
                        Box(
                            modifier = Modifier.size(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.fillMaxSize(),
                                color = SurfaceGray,
                                strokeWidth = 16.dp,
                                strokeCap = StrokeCap.Round
                            )
                            CircularProgressIndicator(
                                progress = { pct },
                                modifier = Modifier.fillMaxSize(),
                                color = Yellow,
                                strokeWidth = 16.dp,
                                strokeCap = StrokeCap.Round
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "${(pct * 100).toInt()}%",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 36.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    if (isHebrew) "ידועות" else "known",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Stats row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ProgressStat(
                                value = state.known.toString(),
                                label = if (isHebrew) "ידועות" else "Known",
                                color = CorrectGreen
                            )
                            ProgressStat(
                                value = state.unknown.toString(),
                                label = if (isHebrew) "לא ידועות" else "Unknown",
                                color = WrongRed
                            )
                            ProgressStat(
                                value = state.total.toString(),
                                label = if (isHebrew) "סה\"כ" else "Total",
                                color = Yellow
                            )
                        }
                    }
                }
            }

            // ── Per-unit stats ─────────────────────────────────────────────
            if (state.unitStats.isNotEmpty()) {
                item {
                    Text(
                        text = if (isHebrew) "פירוט לפי יחידה" else "Unit Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(state.unitStats.entries.sortedBy { it.key }) { (unit, stats) ->
                    var expanded by remember { mutableStateOf(false) }
                    val (known, total) = stats
                    val pct = if (total > 0) known.toFloat() / total else 0f
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        modifier = Modifier.fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(16.dp))
                            .clickable { expanded = !expanded }
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Yellow.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        unit.toString(),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        color = YellowDark
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isHebrew) "יחידה $unit" else "Unit $unit",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { pct },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = Yellow,
                                        trackColor = SurfaceGray
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "$known / $total ${if (isHebrew) "מילים" else "words"}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "${(pct * 100).toInt()}%",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (pct >= 0.8f) CorrectGreen else TextPrimary
                                    )
                                    Icon(
                                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = "Expand",
                                        tint = TextSecondary
                                    )
                                }
                            }
                            AnimatedVisibility(
                                visible = expanded,
                                enter = expandVertically(animationSpec = tween(300)),
                                exit = shrinkVertically(animationSpec = tween(300))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    state.wordsByUnit[unit]?.forEach { word ->
                                        ReviewWordRow(word = word, isHebrew = isHebrew, backgroundColor = OffWhite)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ── Upcoming reviews ───────────────────────────────────────────
            if (state.upcomingReviews.isNotEmpty()) {
                item {
                    Text(
                        text = if (isHebrew) "חזרה קרובה" else "Upcoming Reviews",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(state.upcomingReviews.take(15)) { word ->
                    ReviewWordRow(word = word, isHebrew = isHebrew)
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ProgressStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}

@Composable
private fun ReviewWordRow(word: Word, isHebrew: Boolean, backgroundColor: Color = White) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(word.word, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(word.definition, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (isHebrew) "יחידה ${word.unit}" else "Unit ${word.unit}",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (word.isKnown) CorrectGreenLight else WrongRedLight)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (word.isKnown) {
                            if (isHebrew) "ידוע" else "Known"
                        } else {
                            if (isHebrew) "ללמוד" else "Learn"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (word.isKnown) CorrectGreen else WrongRed
                    )
                }
            }
        }
    }
}
