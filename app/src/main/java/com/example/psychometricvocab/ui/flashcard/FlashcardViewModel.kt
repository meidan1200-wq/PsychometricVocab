package com.example.psychometricvocab.ui.flashcard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.psychometricvocab.data.SrsEngine
import com.example.psychometricvocab.data.VocabDatabase
import com.example.psychometricvocab.data.VocabRepository
import com.example.psychometricvocab.data.Word
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SessionEndState {
    NONE,
    HAS_MORE_IN_UNIT,
    HAS_MORE_IN_DB,
    FINISHED_ALL
}

data class FlashcardUiState(
    val words: List<Word> = emptyList(),
    val currentIndex: Int = 0,
    val sessionComplete: Boolean = false,
    val sessionEndState: SessionEndState = SessionEndState.NONE,
    val knownInSession: Int = 0,
    val unknownInSession: Int = 0,
    val isLoading: Boolean = true
) {
    val currentWord get() = words.getOrNull(currentIndex)
    val progress get() = if (words.isEmpty()) 0 else currentIndex
    val total get() = words.size
}

class FlashcardViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = VocabRepository(VocabDatabase.getInstance(app).wordDao())

    private val _state = MutableStateFlow(FlashcardUiState())
    val state: StateFlow<FlashcardUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun loadWords(track: String, unit: Int?, mode: String) {
        if (_state.value.words.isNotEmpty()) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val wordsList = if (mode == "sort") {
                val allUntouched = if (unit == null) {
                    repo.getAllUntouchedWords(track).first()
                } else {
                    repo.getUntouchedWordsByUnit(track, unit).first()
                }
                allUntouched.shuffled().take(20) // Limit sort sessions to 20 words at a time
            } else if (mode == "memorize") {
                // Filter by unit in SQL: filtering the global top 50 often left a unit with 0 words
                val hardWords = if (unit != null) {
                    repo.getHardestWordsForReviewByUnit(track, unit, limit = 5)
                } else {
                    repo.getHardestWordsForReview(track, limit = 5)
                }
                hardWords // Limit memorize sessions to 5 words
            } else {
                val fallback = if (unit == null) repo.getAllUntouchedWords(track).first()
                               else repo.getUntouchedWordsByUnit(track, unit).first()
                fallback.take(20)
            }

            _state.update { it.copy(words = wordsList, currentIndex = 0, isLoading = false) }
        }
    }

    /** Test-mode card answered. State advances immediately so a double tap cannot skip a card. */
    fun onSwipe(isKnown: Boolean) {
        val s = _state.value
        val current = s.currentWord ?: return
        if (s.sessionComplete) return
        advance(isKnown)
        viewModelScope.launch { repo.processAnswer(current, isCorrect = isKnown) }
    }

    /**
     * A test-mode card reported its own swipe. Ignored unless [word] is still the current card,
     * so a late callback from a card that is animating out can't answer the next card.
     */
    fun onCardSwiped(word: Word, isKnown: Boolean) {
        if (_state.value.currentWord?.id != word.id) return
        onSwipe(isKnown)
    }

    /** Sort-mode row swiped. Ignores repeated callbacks for a word that was already sorted. */
    fun onSwipeWord(word: Word, isKnown: Boolean, isSortMode: Boolean = false, track: String = "", unit: Int? = null) {
        if (!isSortMode) {
            onSwipe(isKnown)
            return
        }
        val s = _state.value
        if (s.words.none { it.id == word.id }) return
        val newWords = s.words.filter { it.id != word.id }
        val didFinish = newWords.isEmpty()
        _state.value = s.copy(
            words = newWords,
            sessionComplete = s.sessionComplete || didFinish,
            knownInSession = if (isKnown) s.knownInSession + 1 else s.knownInSession,
            unknownInSession = if (!isKnown) s.unknownInSession + 1 else s.unknownInSession
        )

        viewModelScope.launch {
            repo.processAnswer(word, isCorrect = isKnown)
            if (didFinish && track.isNotEmpty()) {
                val remainingInDb = repo.getAllUntouchedCount(track).first()
                val remainingInUnit = if (unit != null) repo.getUntouchedCountByUnit(track, unit).first() else remainingInDb
                val endState = when {
                    remainingInUnit > 0 -> SessionEndState.HAS_MORE_IN_UNIT
                    remainingInDb > 0 -> SessionEndState.HAS_MORE_IN_DB
                    else -> SessionEndState.FINISHED_ALL
                }
                _state.update { it.copy(sessionEndState = endState) }
            }
        }
    }

    private fun advance(known: Boolean) {
        _state.update { s ->
            val nextIndex = s.currentIndex + 1
            s.copy(
                currentIndex = nextIndex,
                sessionComplete = nextIndex >= s.words.size,
                knownInSession = if (known) s.knownInSession + 1 else s.knownInSession,
                unknownInSession = if (!known) s.unknownInSession + 1 else s.unknownInSession
            )
        }
    }

    fun resetSession(track: String, unit: Int?, mode: String) {
        loadJob?.cancel()
        _state.update { FlashcardUiState() }
        loadWords(track, unit, mode)
    }
}
