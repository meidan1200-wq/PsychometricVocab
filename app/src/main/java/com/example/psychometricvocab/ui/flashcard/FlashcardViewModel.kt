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

data class FlashcardUiState(
    val words: List<Word> = emptyList(),
    val currentIndex: Int = 0,
    val sessionComplete: Boolean = false,
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

    fun loadWords(track: String, unit: Int?, showAll: Boolean) {
        viewModelScope.launch {
            repo.getWordsForSession(track, unit).collect { allWords ->
                if (_state.value.words.isEmpty()) {
                    val sessionWords = if (showAll) allWords
                    else SrsEngine.selectWordsForSession(allWords, minOf(allWords.size, 20))
                    _state.update { it.copy(words = sessionWords, currentIndex = 0) }
                }
            }
        }
    }

    fun onSwipe(isKnown: Boolean) {
        val current = _state.value.currentWord ?: return
        viewModelScope.launch {
            repo.processAnswer(current, isCorrect = isKnown)
            advance(known = isKnown)
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

    fun resetSession(track: String, unit: Int?, showAll: Boolean) {
        _state.update { FlashcardUiState() }
        loadWords(track, unit, showAll)
    }
}
