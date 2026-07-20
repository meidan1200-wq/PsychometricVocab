package com.example.psychometricvocab.ui.quiz

import android.speech.tts.TextToSpeech
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.psychometricvocab.LocalAppState
import com.example.psychometricvocab.theme.*
import com.example.psychometricvocab.ui.components.VocabTopBar
import com.example.psychometricvocab.ui.components.WordProgressBar
import com.example.psychometricvocab.ui.components.YellowButton
import java.util.Locale

@Composable
fun QuizScreen(
    unit: Int?,
    unknownOnly: Boolean,
    isReviewMode: Boolean = false,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: QuizViewModel = viewModel()
) {
    val state: QuizUiState by vm.state.collectAsStateWithLifecycle()
    val appState = LocalAppState.current
    val isHebrew = appState.isHebrew
    val context = LocalContext.current

    val tts = remember {
        TextToSpeech(context) {}
    }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }

    LaunchedEffect(appState.track, unit, unknownOnly, isReviewMode) {
        vm.resetQuiz(appState.track, unit, unknownOnly, isReviewMode)
    }

    val title = buildString {
        append(if (isHebrew) "מבחן" else "Quiz")
        append(" - ")
        if (isReviewMode) {
            append(if (isHebrew) "מילים קשות" else "Hardest Words")
        } else {
            append(
                if (unit != null) {
                    if (isHebrew) "יחידה $unit" else "Unit $unit"
                } else {
                    if (isHebrew) "כל היחידות" else "All Units"
                }
            )
        }
    }

    Scaffold(
        topBar = { VocabTopBar(title = title, onBack = onBack) },
        containerColor = OffWhite
    ) { padding ->
        Box(modifier = modifier.fillMaxSize().padding(padding)) {
            val isLoading = state.isLoading
            val sessionComplete = state.sessionComplete

            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Yellow)
                    }
                }
                sessionComplete -> {
                    QuizResultScreen(
                        correct = state.correctCount,
                        wrong = state.wrongCount,
                        total = state.total,
                        isHebrew = isHebrew,
                        onBack = onBack
                    )
                }
                else -> {
                    val currentQuestion = state.currentQuestion
                    if (currentQuestion != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                LanguageModeBadge(isHebrew)

                                WordProgressBar(
                                    current = state.progress + 1,
                                    total = state.total,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Word display card
                                AnimatedContent(
                                    targetState = currentQuestion,
                                    transitionSpec = {
                                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                                    },
                                    label = "quizWord"
                                ) { q ->
                                    Card(
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = White),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .shadow(6.dp, RoundedCornerShape(20.dp))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(28.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            val displayWord = q.word.cleanWord
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(Yellow.copy(alpha = 0.2f))
                                                    .clickable {
                                                        tts.language = if (appState.track == "hebrew") Locale("he", "IL") else Locale.ENGLISH
                                                        tts.speak(displayWord, TextToSpeech.QUEUE_FLUSH, null, null)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Speak", tint = YellowDark)
                                            }
                                            Spacer(Modifier.height(12.dp))
                                            Text(
                                                text = displayWord,
                                                style = MaterialTheme.typography.displayMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                textAlign = TextAlign.Center,
                                                color = TextPrimary
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 6.dp)
                                                    .width(80.dp)
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(Yellow)
                                            )
                                            Text(
                                                text = if (isHebrew) "בחר את התרגום הנכון" else "Choose the correct translation",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextSecondary,
                                                modifier = Modifier.padding(top = 8.dp),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                // Answer options — use quizOpt to avoid shadowing issues
                                currentQuestion.options.forEach { quizOpt ->
                                    val optIsSelected = currentQuestion.selectedOptionId == quizOpt.wordId
                                    val isAnswered = currentQuestion.answered
                                    val optIsCorrect = quizOpt.isCorrect

                                    val bgColor = when {
                                        isAnswered && optIsCorrect -> CorrectGreenLight
                                        isAnswered && optIsSelected && !optIsCorrect -> WrongRedLight
                                        optIsSelected -> Yellow.copy(alpha = 0.15f)
                                        else -> White
                                    }
                                    val borderColor = when {
                                        isAnswered && optIsCorrect -> CorrectGreen
                                        isAnswered && optIsSelected && !optIsCorrect -> WrongRed
                                        optIsSelected -> Yellow
                                        else -> DividerGray
                                    }

                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = bgColor),
                                        border = BorderStroke(1.5.dp, borderColor),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !isAnswered) {
                                                vm.onAnswer(quizOpt.wordId, quizOpt.isCorrect)
                                            }
                                    ) {
                                        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    RadioButton(
                                                        selected = optIsSelected,
                                                        onClick = null,
                                                        colors = RadioButtonDefaults.colors(
                                                            selectedColor = if (isAnswered && !optIsCorrect) WrongRed else Yellow,
                                                            unselectedColor = DividerGray
                                                        )
                                                    )
                                                    Text(
                                                        text = android.text.Html.fromHtml(quizOpt.text, android.text.Html.FROM_HTML_MODE_LEGACY).toString(),
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = if (optIsSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = TextPrimary
                                                    )
                                                }
                                                if (isAnswered && optIsCorrect) {
                                                    Icon(
                                                        Icons.Filled.Check, null,
                                                        tint = CorrectGreen,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                } else if (isAnswered && optIsSelected && !optIsCorrect) {
                                                    Icon(
                                                        Icons.Filled.Close, null,
                                                        tint = WrongRed,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(24.dp))
                            }

                            // Next / Finish button
                            AnimatedVisibility(
                                visible = currentQuestion.answered,
                                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                            ) {
                                val isLast = (state.currentIndex + 1) >= state.total
                                YellowButton(
                                    text = if (isLast) {
                                        if (isHebrew) "סיים מבחן" else "Finish Quiz"
                                    } else {
                                        if (isHebrew) "הבא ←" else "Next →"
                                    },
                                    onClick = vm::onNext,
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageModeBadge(isHebrew: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Yellow.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = if (isHebrew) "🇮🇱 עברית → English" else "🇺🇸 English → עברית",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = YellowDark
        )
    }
}

@Composable
private fun QuizResultScreen(
    correct: Int,
    wrong: Int,
    total: Int,
    isHebrew: Boolean,
    onBack: () -> Unit
) {
    val pct = if (total > 0) (correct * 100 / total) else 0
    val emoji = when {
        pct >= 80 -> "🏆"
        pct >= 60 -> "👍"
        else -> "💪"
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(emoji, fontSize = 72.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (isHebrew) "תוצאות המבחן" else "Quiz Results",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$pct%",
            fontSize = 56.sp,
            fontWeight = FontWeight.ExtraBold,
            color = when {
                pct >= 80 -> CorrectGreen
                pct >= 60 -> Yellow
                else -> WrongRed
            }
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CorrectGreenLight)
            ) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "✅ $correct",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = CorrectGreen
                    )
                    Text(if (isHebrew) "נכונות" else "Correct", color = CorrectGreen)
                }
            }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WrongRedLight)
            ) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "❌ $wrong",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = WrongRed
                    )
                    Text(if (isHebrew) "שגויות" else "Wrong", color = WrongRed)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        YellowButton(
            text = if (isHebrew) "חזור לתפריט" else "Back to Menu",
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
