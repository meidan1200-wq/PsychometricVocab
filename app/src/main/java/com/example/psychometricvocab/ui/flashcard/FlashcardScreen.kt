package com.example.psychometricvocab.ui.flashcard

import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.psychometricvocab.LocalAppState
import com.example.psychometricvocab.theme.*
import com.example.psychometricvocab.ui.components.*

@Composable
fun FlashcardScreen(
    unit: Int?,
    showAll: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: FlashcardViewModel = viewModel()
) {
    val state: FlashcardUiState by vm.state.collectAsStateWithLifecycle()
    val appState = LocalAppState.current
    val isHebrew = appState.isHebrew

    LaunchedEffect(appState.track, unit, showAll) {
        vm.resetSession(appState.track, unit, showAll)
    }

    val title = buildString {
        append(if (isHebrew) "לימוד מילים" else "Vocabulary Learning")
        append(" - ")
        append(
            if (unit != null) {
                if (isHebrew) "יחידה $unit" else "Unit $unit"
            } else {
                if (isHebrew) "כל המילים" else "All Words"
            }
        )
    }

    Scaffold(
        topBar = { VocabTopBar(title = title, onBack = onBack) },
        containerColor = OffWhite
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val words = state.words
            val sessionComplete = state.sessionComplete

            when {
                words.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Yellow)
                    }
                }
                sessionComplete -> {
                    SessionCompleteScreen(
                        known = state.knownInSession,
                        unknown = state.unknownInSession,
                        total = state.total,
                        isHebrew = isHebrew,
                        onRestart = { vm.resetSession(appState.track, unit, showAll) },
                        onBack = onBack
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        WordProgressBar(
                            current = state.progress + 1,
                            total = state.total,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val currentWord = state.currentWord
                        if (currentWord != null) {
                            AnimatedContent(
                                targetState = currentWord,
                                transitionSpec = {
                                    slideInHorizontally { it } + fadeIn() togetherWith
                                            slideOutHorizontally { -it } + fadeOut()
                                },
                                label = "cardTransition"
                            ) { displayWord ->
                                SwipeableFlashCard(
                                    word = displayWord,
                                    cardIndex = state.currentIndex,
                                    totalCards = state.total,
                                    onSwipeKnown = { vm.onSwipe(true) },
                                    onSwipeUnknown = { vm.onSwipe(false) }
                                )
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { vm.onSwipe(false) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.5.dp, WrongRed),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WrongRed)
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (isHebrew) "לא יודע" else "Don't know",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Button(
                                    onClick = { vm.onSwipe(true) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CorrectGreen,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (isHebrew) "יודע!" else "Know it!",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCompleteScreen(
    known: Int,
    unknown: Int,
    total: Int,
    isHebrew: Boolean,
    onRestart: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎉", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (isHebrew) "סיימת את הסשן!" else "Session Complete!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ResultBadge(
                value = "✅ $known",
                label = if (isHebrew) "ידעת" else "Knew",
                bg = CorrectGreenLight,
                accent = CorrectGreen
            )
            ResultBadge(
                value = "❌ $unknown",
                label = if (isHebrew) "לא ידעת" else "Missed",
                bg = WrongRedLight,
                accent = WrongRed
            )
        }
        Spacer(Modifier.height(32.dp))
        YellowButton(
            text = if (isHebrew) "חזור על הסשן" else "Restart Session",
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text(
                if (isHebrew) "חזור לתפריט" else "Back to Menu",
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ResultBadge(value: String, label: String, bg: Color, accent: Color) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = accent)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = accent.copy(alpha = 0.8f))
        }
    }
}
