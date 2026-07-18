package com.example.psychometricvocab.ui.quiz

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.psychometricvocab.data.SrsEngine
import com.example.psychometricvocab.data.VocabDatabase
import com.example.psychometricvocab.data.VocabRepository
import com.example.psychometricvocab.data.Word
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class QuizOption(val text: String, val wordId: Int, val isCorrect: Boolean)

data class QuizQuestion(
    val word: Word,
    val options: List<QuizOption>,
    val answered: Boolean = false,
    val selectedOptionId: Int = -1
)

data class QuizUiState(
    val questions: List<QuizQuestion> = emptyList(),
    val currentIndex: Int = 0,
    val sessionComplete: Boolean = false,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val isLoading: Boolean = true,
    val currentTrack: String? = null,
    val currentUnit: Int? = null
) {
    val currentQuestion get() = questions.getOrNull(currentIndex)
    val total get() = questions.size
    val progress get() = currentIndex
}

class QuizViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = VocabRepository(VocabDatabase.getInstance(app).wordDao())

    private val _state = MutableStateFlow(QuizUiState())
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    fun resetQuiz(track: String, unit: Int?, unknownOnly: Boolean) {
        _state.update { QuizUiState(currentTrack = track, currentUnit = unit, isLoading = true) }
        loadQuiz(track, unit, unknownOnly)
    }

    fun loadQuiz(track: String, unit: Int?, unknownOnly: Boolean) {
        viewModelScope.launch {
            repo.getWordsForSession(track, unit).collect { allWords ->
                if (_state.value.questions.isEmpty() && allWords.isNotEmpty()) {
                    val pool = if (unknownOnly) allWords.filter { !it.isKnown } else allWords
                    val sessionWords = SrsEngine.selectWordsForSession(pool.ifEmpty { allWords }, 20)
                    val questions = sessionWords.map { word ->
                        buildQuestion(word, allWords, track)
                    }
                    _state.update { it.copy(questions = questions, isLoading = false) }
                }
            }
        }
    }

    private fun buildQuestion(word: Word, allWords: List<Word>, track: String): QuizQuestion {
        val distractors = allWords
            .filter { it.id != word.id }
            .shuffled()
            .take(3)
        val correctText = word.definition
        val correctOption = QuizOption(correctText, word.id, true)
        val distOptions = distractors.map { d ->
            QuizOption(d.definition, d.id, false)
        }
        val noIdeaOption = QuizOption(if (track == "hebrew") "וואלה, אין לי מושג" else "No idea at all", -1, false)
        val options = (distOptions + correctOption).shuffled() + noIdeaOption
        return QuizQuestion(word = word, options = options)
    }

    fun onAnswer(optionId: Int, isCorrect: Boolean) {
        val current = _state.value.currentQuestion ?: return
        if (current.answered) return
        viewModelScope.launch {
            repo.processAnswer(current.word, isCorrect)
        }
        _state.update { s ->
            val updated = s.questions.toMutableList()
            updated[s.currentIndex] = current.copy(answered = true, selectedOptionId = optionId)
            s.copy(
                questions = updated,
                correctCount = if (isCorrect) s.correctCount + 1 else s.correctCount,
                wrongCount = if (!isCorrect) s.wrongCount + 1 else s.wrongCount
            )
        }
    }

    fun onNext() {
        val s = _state.value
        val nextIndex = s.currentIndex + 1
        _state.update { it.copy(currentIndex = nextIndex, sessionComplete = nextIndex >= it.questions.size) }
    }
}
