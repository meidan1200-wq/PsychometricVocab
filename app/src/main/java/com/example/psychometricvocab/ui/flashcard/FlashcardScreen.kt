package com.example.psychometricvocab.ui.flashcard

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
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
    mode: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: FlashcardViewModel = viewModel()
) {
    val state: FlashcardUiState by vm.state.collectAsStateWithLifecycle()
    val appState = LocalAppState.current
    val isHebrew = appState.isHebrew

    var memorizePhase by remember { mutableStateOf(if (mode == "memorize") "loop" else "test") }
    var playbackSpeed by remember { mutableStateOf(1f) } // 1x, 2x, 4x

    LaunchedEffect(appState.track, unit, mode) {
        vm.resetSession(appState.track, unit, mode)
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
                sessionComplete -> {
                    SessionCompleteScreen(
                        known = state.knownInSession,
                        unknown = state.unknownInSession,
                        total = state.total,
                        isHebrew = isHebrew,
                        mode = mode,
                        endState = state.sessionEndState,
                        onRestart = { 
                            memorizePhase = if (mode == "memorize") "loop" else "test"
                            vm.resetSession(appState.track, unit, mode) 
                        },
                        onNextSection = { 
                            val nextUnit = (unit ?: 0) + 1
                            vm.resetSession(appState.track, nextUnit, mode)
                        },
                        onBack = onBack
                    )
                }
                words.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Yellow)
                    }
                }

                memorizePhase == "loop" -> {
                    MemorizeLoopScreen(
                        words = state.words,
                        isHebrew = isHebrew,
                        playbackSpeed = playbackSpeed,
                        onSpeedChange = { playbackSpeed = it },
                        onReadyToTest = { memorizePhase = "test" }
                    )
                }
                mode == "sort" -> {
                    SortModeListScreen(
                        words = state.words,
                        isHebrew = isHebrew,
                        onSwipeWord = { word, known -> vm.onSwipeWord(word, known, isSortMode = true, track = appState.track, unit = unit) }
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
    mode: String,
    endState: SessionEndState,
    onRestart: () -> Unit,
    onNextSection: () -> Unit,
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
        
        val titleText = if (mode == "sort" && endState == SessionEndState.FINISHED_ALL) {
            if (isHebrew) "סיימת את כל המילים במאגר!" else "Finished all words in database!"
        } else if (mode == "sort" && endState == SessionEndState.HAS_MORE_IN_DB) {
            if (isHebrew) "סיימת לקטלג את כל המילים ביחידה זו!" else "Finished categorizing all words in this section!"
        } else {
            if (isHebrew) "סיימת את הסשן!" else "Session Complete!"
        }

        Text(
            text = titleText,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        
        if (mode != "sort") {
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
        }

        if (mode == "sort") {
            when (endState) {
                SessionEndState.HAS_MORE_IN_UNIT, SessionEndState.NONE -> {
                    YellowButton(
                        text = if (isHebrew) "עשה עוד סיבוב" else "Make another round",
                        onClick = onRestart,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                SessionEndState.HAS_MORE_IN_DB -> {
                    Text(
                        text = if (isHebrew) "רוצה לעבור ליחידה הבאה?" else "wanna move to next one?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    YellowButton(
                        text = if (isHebrew) "יחידה הבאה" else "Next section",
                        onClick = onNextSection,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                SessionEndState.FINISHED_ALL -> {
                    // Just show back button
                }
            }
        } else {
            YellowButton(
                text = if (isHebrew) "עוד סיבוב" else "Another One",
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text(
                if (isHebrew) "חזור לתפריט הראשי" else "Back to Home",
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

@Composable
private fun MemorizeLoopScreen(
    words: List<com.example.psychometricvocab.data.Word>,
    isHebrew: Boolean,
    playbackSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onReadyToTest: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    
    // Auto-advance logic
    LaunchedEffect(currentIndex, playbackSpeed) {
        val delayMillis = (4000L / playbackSpeed).toLong()
        kotlinx.coroutines.delay(delayMillis)
        currentIndex = (currentIndex + 1) % words.size
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Speed selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (isHebrew) "מהירות:" else "Speed:", fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            listOf(1f, 2f, 4f).forEach { speed ->
                FilterChip(
                    selected = playbackSpeed == speed,
                    onClick = { onSpeedChange(speed) },
                    label = { Text("${speed.toInt()}x") },
                    modifier = Modifier.padding(horizontal = 4.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Yellow.copy(alpha = 0.3f),
                        selectedLabelColor = TextPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Progress indicators (dots)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            words.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (index == currentIndex) Yellow else DividerGray)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val currentWord = words[currentIndex]
        AnimatedContent(
            targetState = currentWord,
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
            },
            label = "memorizeLoop"
        ) { displayWord ->
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(300.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayWord.cleanWord,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = DividerGray, modifier = Modifier.fillMaxWidth(0.5f))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = displayWord.cleanDefinition,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        YellowButton(
            text = if (isHebrew) "מוכן להיבחן!" else "Ready to Test!",
            onClick = onReadyToTest,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortModeListScreen(
    words: List<com.example.psychometricvocab.data.Word>,
    isHebrew: Boolean,
    onSwipeWord: (com.example.psychometricvocab.data.Word, Boolean) -> Unit
) {
    var expandedWordId by remember { mutableStateOf<Int?>(null) }
    val LightRedBg = Color(0xFFFCE8E8)
    val LightGreenBg = Color(0xFFE8F5E9)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(words, key = { _, word -> word.id }) { index, word ->
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { dismissValue ->
                        when (dismissValue) {
                            SwipeToDismissBoxValue.StartToEnd -> {
                                onSwipeWord(word, true)
                                true
                            }
                            SwipeToDismissBoxValue.EndToStart -> {
                                onSwipeWord(word, false)
                                true
                            }
                            else -> false
                        }
                    }
                )

                // Auto-collapse when swipe starts by observing offset changes
                LaunchedEffect(dismissState, expandedWordId) {
                    if (expandedWordId == word.id) {
                        snapshotFlow { 
                            try { dismissState.requireOffset() } catch (e: Exception) { 0f }
                        }.collect { offset ->
                            if (Math.abs(offset) > 10f) {
                                expandedWordId = null
                            }
                        }
                    }
                }

                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = { /* No background content, row itself changes color */ },
                    content = {
                        val isExpanded = expandedWordId == word.id
                        val cardBgColor = when (dismissState.dismissDirection) {
                            SwipeToDismissBoxValue.StartToEnd -> LightGreenBg
                            SwipeToDismissBoxValue.EndToStart -> LightRedBg
                            else -> Color.Transparent
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(cardBgColor)
                                .animateContentSize()
                                .clickable { 
                                    expandedWordId = if (isExpanded) null else word.id 
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp, horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = word.cleanWord,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.DarkGray,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                            
                            if (isExpanded) {
                                Text(
                                    text = word.cleanDefinition,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, start = 24.dp, end = 24.dp),
                                    textAlign = TextAlign.Center
                                )
                            }

                            if (index < words.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                    thickness = 1.dp,
                                    color = Color.LightGray.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}
