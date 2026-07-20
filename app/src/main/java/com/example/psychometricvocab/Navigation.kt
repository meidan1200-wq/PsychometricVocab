package com.example.psychometricvocab

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.navigation3.runtime.rememberNavBackStack
import com.example.psychometricvocab.ui.components.VocabBottomNav
import com.example.psychometricvocab.ui.flashcard.FlashcardScreen
import com.example.psychometricvocab.ui.home.HomeScreen
import com.example.psychometricvocab.ui.progress.ProgressScreen
import com.example.psychometricvocab.ui.quiz.QuizScreen
import com.example.psychometricvocab.ui.quiz.QuizSettingsScreen
import androidx.compose.material3.Scaffold
import androidx.compose.ui.unit.LayoutDirection

/**
 * Main navigation host.
 *
 * Key behaviour:
 * - Wraps entire content in CompositionLocalProvider for layout direction
 *   so that switching language toggles RTL ↔ LTR for every composable.
 * - Bottom nav drives tab switching; sub-screens (flashcard, quiz) are
 *   pushed on top of the current tab via a simple back-stack state.
 */
@Composable
fun MainNavigation() {
    val appState = remember { AppState() }

    // Provide both AppState and LayoutDirection down the tree
    CompositionLocalProvider(
        LocalAppState provides appState,
        LocalLayoutDirection provides appState.layoutDirection
    ) {
        MainScaffold(appState)
    }
}

@Composable
private fun MainScaffold(appState: AppState) {
    var currentTab by remember { mutableIntStateOf(0) }   // start on Learn (Home)

    // Simple screen stack per tab
    // null = tab root; non-null = sub-screen
    var subScreen by remember { mutableStateOf<Any?>(null) }
    var progressExpandUnit by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        bottomBar = {
            VocabBottomNav(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    if (tab == 2) {
                        currentTab = 0
                    } else {
                        currentTab = tab
                    }
                    subScreen = null
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when {
                // ── Flashcard sub-screen ─────────────────────────────────
                subScreen is FlashcardKey -> {
                    val key = subScreen as FlashcardKey
                    FlashcardScreen(
                        unit = key.unit,
                        mode = key.mode,
                        onBack = { subScreen = null }
                    )
                }
                // ── Quiz settings sub-screen ─────────────────────────────
                subScreen is QuizSettingsKey -> {
                    QuizSettingsScreen(
                        onStartQuiz = { unit, unknownOnly ->
                            subScreen = QuizKey(unit, unknownOnly)
                        },
                        onBack = { subScreen = null }
                    )
                }
                // ── Quiz sub-screen ──────────────────────────────────────
                subScreen is QuizKey -> {
                    val key = subScreen as QuizKey
                    QuizScreen(
                        unit = key.unit,
                        unknownOnly = key.unknownOnly,
                        isReviewMode = key.isReviewMode,
                        onBack = { subScreen = null }
                    )
                }
                // ── Tab roots ────────────────────────────────────────────
                else -> when (currentTab) {
                    0 -> {
                        HomeScreen(
                            onGoToFlashcard = { unit -> subScreen = FlashcardKey(unit) },
                            onGoToQuiz = { subScreen = QuizSettingsKey },
                            onGoToProgress = { unit -> 
                                progressExpandUnit = unit
                                currentTab = 4 
                            },
                            onGoToReview = { subScreen = QuizKey(unit = null, unknownOnly = false, isReviewMode = true) }
                        )
                    }
                    1 -> {
                        // "Cards" tab shows Flashcard Settings to pick language/unit
                        com.example.psychometricvocab.ui.flashcard.FlashcardSettingsScreen(
                            onStartFlashcards = { unit, mode ->
                                subScreen = FlashcardKey(unit, mode)
                            },
                            onBack = { currentTab = 0 }
                        )
                    }
                    2 -> {
                        HomeScreen(
                            onGoToFlashcard = { unit -> subScreen = FlashcardKey(unit) },
                            onGoToQuiz = { subScreen = QuizSettingsKey },
                            onGoToProgress = { unit -> 
                                progressExpandUnit = unit
                                currentTab = 4 
                            },
                            onGoToReview = { subScreen = QuizKey(unit = null, unknownOnly = false, isReviewMode = true) }
                        )
                    }
                    3 -> {
                        QuizSettingsScreen(
                            onStartQuiz = { unit, unknownOnly ->
                                subScreen = QuizKey(unit, unknownOnly)
                            },
                            onBack = { currentTab = 2 }
                        )
                    }
                    4 -> {
                        ProgressScreen(autoExpandUnit = progressExpandUnit)
                    }
                    else -> {
                        HomeScreen(
                            onGoToFlashcard = { unit -> subScreen = FlashcardKey(unit) },
                            onGoToQuiz = { subScreen = QuizSettingsKey },
                            onGoToProgress = { unit -> 
                                progressExpandUnit = unit
                                currentTab = 4 
                            },
                            onGoToReview = { subScreen = QuizKey(unit = null, unknownOnly = false, isReviewMode = true) }
                        )
                    }
                }
            }
        }
    }
}
