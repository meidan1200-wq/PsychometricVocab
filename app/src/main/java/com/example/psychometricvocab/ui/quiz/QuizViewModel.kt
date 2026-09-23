package com.example.psychometricvocab.ui.quiz

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.psychometricvocab.data.SrsEngine
import com.example.psychometricvocab.data.VocabDatabase
import com.example.psychometricvocab.data.VocabRepository
import com.example.psychometricvocab.data.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

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

    private var loadJob: Job? = null

    fun resetQuiz(track: String, unit: Int?, unknownOnly: Boolean, isReviewMode: Boolean = false) {
        _state.update { QuizUiState(currentTrack = track, currentUnit = unit, isLoading = true) }
        loadQuiz(track, unit, unknownOnly, isReviewMode)
    }

    fun loadQuiz(track: String, unit: Int?, unknownOnly: Boolean, isReviewMode: Boolean = false) {
        // Cancel any previous load: before, every quiz left a collector on the whole word
        // table running forever, re-querying thousands of rows after every answer, and an
        // old collector could fill a new quiz with questions from the previous unit.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val allWords = repo.getWordsForSession(track, if (isReviewMode) null else unit).first()
            val sessionWords = if (isReviewMode) {
                repo.getHardestWordsForReview(track, 20)
            } else {
                val pool = if (unknownOnly) allWords.filter { !it.isKnown } else allWords
                SrsEngine.selectWordsForSession(pool.ifEmpty { allWords }, 20)
            }
            val questions = withContext(Dispatchers.Default) {
                sessionWords.map { word -> buildQuestion(word, allWords, track) }
            }
            _state.update {
                it.copy(
                    questions = questions,
                    isLoading = false,
                    // Nothing to ask (e.g. no hard words yet): show the result screen, not a blank page
                    sessionComplete = questions.isEmpty()
                )
            }
        }
    }

    private fun buildQuestion(word: Word, allWords: List<Word>, track: String): QuizQuestion {
        // Random sampling instead of shuffling the whole word list for every question,
        // and never offer a distractor whose definition equals the correct one.
        val distractors = LinkedHashMap<String, Word>()
        var attempts = 0
        while (distractors.size < 3 && attempts < 60 && allWords.size > 1) {
            attempts++
            val candidate = allWords[Random.nextInt(allWords.size)]
            if (candidate.id != word.id && candidate.definition != word.definition) {
                distractors.putIfAbsent(candidate.definition, candidate)
            }
        }
        val correctText = word.definition
        val correctOption = QuizOption(correctText, word.id, true)
        val distOptions = distractors.values.map { d ->
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
            repo.processAnswer(current.word, isCorrect, isQuiz = true, isNotSure = (optionId == -1))
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
