package com.example.psychometricvocab

import androidx.activity.compose.BackHandler
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
import com.example.psychometricvocab.ui.account.AccountScreen
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.psychometricvocab.theme.TextSecondary
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
private val SubScreenSaver = Saver<Any?, String>(
    save = { SubScreenCodec.encode(it) },
    restore = { SubScreenCodec.decode(it) }
)

private const val HOME_TAB = 2

@Composable
fun MainNavigation() {
    val appState = rememberSaveable(saver = AppState.StateSaver) { AppState() }
    
    // Provide both AppState and LayoutDirection down the tree
    CompositionLocalProvider(
        LocalAppState provides appState,
        LocalLayoutDirection provides appState.layoutDirection
    ) {
        MainScaffold(appState)
    }
}

@Composable
fun MainScaffold(appState: AppState, accountVm: com.example.psychometricvocab.ui.account.AccountViewModel = viewModel()) {
    // Tab indices match VocabBottomNav: 2 is the centre Home button. Starting on 0 highlighted
    // the "Learn" tab while Home was showing.
    var currentTab by rememberSaveable { mutableIntStateOf(HOME_TAB) }
    var subScreen by rememberSaveable(stateSaver = SubScreenSaver) { mutableStateOf<Any?>(null) }
    var progressExpandUnit by rememberSaveable { mutableStateOf<Int?>(null) }

    val profile by accountVm.profile.collectAsStateWithLifecycle()
    var showRegistration by remember { mutableStateOf(false) }

    LaunchedEffect(profile) {
        if (profile.fullName.isEmpty() && !profile.isGuest) {
            showRegistration = true
        } else {
            showRegistration = false
        }
    }

    if (showRegistration) {
        com.example.psychometricvocab.ui.account.AuthScreen(
            onCreateAccount = { name, email ->
                val profile = com.example.psychometricvocab.data.AccountProfile(fullName = name, email = email)
                accountVm.saveProfile(name, email, "")
                showRegistration = false
            },
            onContinueAsGuest = {
                accountVm.setGuestMode(true)
                showRegistration = false
            }
        )
    } else {
        // System Back mirrors the in-app ← arrow: close the sub-screen, else return to Home.
        // Without this, Back on any sub-screen finished the Activity and lost the session.
        BackHandler(enabled = subScreen != null || currentTab != HOME_TAB) {
            if (subScreen != null) subScreen = null else currentTab = HOME_TAB
        }
        Scaffold(
            bottomBar = {
            VocabBottomNav(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    currentTab = tab
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
                        useSavedPreference = key.useSavedPreference,
                        onBack = { subScreen = null }
                    )
                }
                // ── Account sub-screen ───────────────────────────────────
                subScreen is AccountKey -> {
                    AccountScreen(onBack = { subScreen = null })
                }
                // ── Tab roots ────────────────────────────────────────────
                else -> when (currentTab) {
                    0 -> {
                        HomeScreen(
                            onGoToFlashcard = { unit -> subScreen = FlashcardKey(unit) },
                            onGoToQuiz = { subScreen = QuizSettingsKey },
                            onGoToQuizShortcut = { unit -> subScreen = QuizKey(unit = unit, unknownOnly = false, useSavedPreference = true) },
                            onGoToProgress = { unit -> 
                                progressExpandUnit = unit
                                currentTab = 4 
                            },
                            onGoToReview = { subScreen = QuizKey(unit = null, unknownOnly = false, isReviewMode = true) },
                            onAvatarClick = { subScreen = AccountKey }
                        )
                    }
                    1 -> {
                        // "Cards" tab shows Flashcard Settings to pick language/unit
                        com.example.psychometricvocab.ui.flashcard.FlashcardSettingsScreen(
                            onStartFlashcards = { unit, mode ->
                                subScreen = FlashcardKey(unit, mode)
                            },
                            onBack = { currentTab = HOME_TAB }
                        )
                    }
                    2 -> {
                        HomeScreen(
                            onGoToFlashcard = { unit -> subScreen = FlashcardKey(unit) },
                            onGoToQuiz = { subScreen = QuizSettingsKey },
                            onGoToQuizShortcut = { unit -> subScreen = QuizKey(unit = unit, unknownOnly = false, useSavedPreference = true) },
                            onGoToProgress = { unit -> 
                                progressExpandUnit = unit
                                currentTab = 4 
                            },
                            onGoToReview = { subScreen = QuizKey(unit = null, unknownOnly = false, isReviewMode = true) },
                            onAvatarClick = { subScreen = AccountKey }
                        )
                    }
                    3 -> {
                        QuizSettingsScreen(
                            onStartQuiz = { unit, unknownOnly ->
                                subScreen = QuizKey(unit, unknownOnly)
                            },
                            onBack = { currentTab = HOME_TAB }
                        )
                    }
                    4 -> {
                        ProgressScreen(
                            autoExpandUnit = progressExpandUnit,
                            onBack = null,
                            onAvatarClick = { subScreen = AccountKey }
                        )
                    }
                    else -> {
                        HomeScreen(
                            onGoToFlashcard = { unit -> subScreen = FlashcardKey(unit) },
                            onGoToQuiz = { subScreen = QuizSettingsKey },
                            onGoToQuizShortcut = { unit -> subScreen = QuizKey(unit = unit, unknownOnly = false, useSavedPreference = true) },
                            onGoToProgress = { unit -> 
                                progressExpandUnit = unit
                                currentTab = 4 
                            },
                            onGoToReview = { subScreen = QuizKey(unit = null, unknownOnly = false, isReviewMode = true) },
                            onAvatarClick = { subScreen = AccountKey }
                        )
                    }
                }
            }
        }
    }
    }
}
