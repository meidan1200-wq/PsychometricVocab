package com.example.psychometricvocab.ui.flashcard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.psychometricvocab.data.SrsEngine
import com.example.psychometricvocab.data.VocabDatabase
import com.example.psychometricvocab.data.VocabRepository
import com.example.psychometricvocab.data.Word
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
    val unknownInSession: Int = 0
) {
    val currentWord get() = words.getOrNull(currentIndex)
    val progress get() = if (words.isEmpty()) 0 else currentIndex
    val total get() = words.size
}

class FlashcardViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = VocabRepository(VocabDatabase.getInstance(app).wordDao())

    private val _state = MutableStateFlow(FlashcardUiState())
    val state: StateFlow<FlashcardUiState> = _state.asStateFlow()

    fun loadWords(track: String, unit: Int?, mode: String) {
        viewModelScope.launch {
            if (_state.value.words.isEmpty()) {
                val wordsList = if (mode == "sort") {
                    val allUntouched = if (unit == null) {
                        repo.getAllUntouchedWords(track).first()
                    } else {
                        repo.getUntouchedWordsByUnit(track, unit).first()
                    }
                    allUntouched.shuffled().take(20) // Limit sort sessions to 20 words at a time
                } else if (mode == "memorize") {
                    val hardWords = repo.getHardestWordsForReview(track, limit = 50)
                    val filtered = if (unit != null) hardWords.filter { it.unit == unit } else hardWords
                    filtered.take(5) // Limit memorize sessions to 5 words
                } else {
                    val fallback = if (unit == null) repo.getAllUntouchedWords(track).first() 
                                   else repo.getUntouchedWordsByUnit(track, unit).first()
                    fallback.take(20)
                }
                
                _state.update { it.copy(words = wordsList, currentIndex = 0) }
            }
        }
    }

    fun onSwipe(isKnown: Boolean) {
        val current = _state.value.currentWord ?: return
        onSwipeWord(current, isKnown, isSortMode = false, track = "", unit = null)
    }

    fun onSwipeWord(word: Word, isKnown: Boolean, isSortMode: Boolean = false, track: String = "", unit: Int? = null) {
        viewModelScope.launch {
            repo.processAnswer(word, isCorrect = isKnown)
            var didFinish = false
            _state.update { s ->
                if (isSortMode) {
                    val newWords = s.words.filter { it.id != word.id }
                    if (s.words.isNotEmpty() && newWords.isEmpty()) didFinish = true
                    s.copy(
                        words = newWords,
                        sessionComplete = s.sessionComplete || didFinish,
                        knownInSession = if (isKnown) s.knownInSession + 1 else s.knownInSession,
                        unknownInSession = if (!isKnown) s.unknownInSession + 1 else s.unknownInSession
                    )
                } else {
                    val nextIndex = s.currentIndex + 1
                    didFinish = nextIndex >= s.words.size
                    s.copy(
                        currentIndex = nextIndex,
                        sessionComplete = s.sessionComplete || didFinish,
                        knownInSession = if (isKnown) s.knownInSession + 1 else s.knownInSession,
                        unknownInSession = if (!isKnown) s.unknownInSession + 1 else s.unknownInSession
                    )
                }
            }
            
            if (didFinish && isSortMode && track.isNotEmpty()) {
                val remainingInUnit = if (unit != null) repo.getUntouchedCountByUnit(track, unit).first() else repo.getAllUntouchedCount(track).first()
                val remainingInDb = repo.getAllUntouchedCount(track).first()
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
        _state.update { FlashcardUiState() }
        loadWords(track, unit, mode)
    }
}
