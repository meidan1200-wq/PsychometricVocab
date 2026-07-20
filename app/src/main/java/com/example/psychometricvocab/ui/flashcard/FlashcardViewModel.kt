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

    fun loadWords(track: String, unit: Int?, mode: String) {
        viewModelScope.launch {
            if (_state.value.words.isEmpty()) {
                val wordsList = if (mode == "sort") {
                    val allUntouched = if (unit == null) {
                        repo.getAllUntouchedWords(track).first()
                    } else {
                        repo.getUntouchedWordsByUnit(track, unit).first()
                    }
                    allUntouched.take(20) // Limit sort sessions to 20 words at a time
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

    fun resetSession(track: String, unit: Int?, mode: String) {
        _state.update { FlashcardUiState() }
        loadWords(track, unit, mode)
    }
}
