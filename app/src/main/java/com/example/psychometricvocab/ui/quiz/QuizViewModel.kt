package com.example.psychometricvocab.ui.quiz

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.psychometricvocab.data.ActivityLog
import com.example.psychometricvocab.data.QuizPreferences
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
    val currentUnit: Int? = null,
    // One-shot: set when a Home shortcut asked for "words I missed" but there weren't enough,
    // so the quiz silently fell back to all words. QuizScreen shows a Toast then consumes it.
    val fellBackToAllWords: Boolean = false
) {
    val currentQuestion get() = questions.getOrNull(currentIndex)
    val total get() = questions.size
    val progress get() = currentIndex
}

class QuizViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = VocabRepository(VocabDatabase.getInstance(app).wordDao(), ActivityLog(app))
    private val quizPrefs = QuizPreferences(app)

    private val _state = MutableStateFlow(QuizUiState())
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun resetQuiz(
        track: String,
        unit: Int?,
        unknownOnly: Boolean,
        isReviewMode: Boolean = false,
        useSavedPreference: Boolean = false
    ) {
        _state.update { QuizUiState(currentTrack = track, currentUnit = unit, isLoading = true) }
        loadQuiz(track, unit, unknownOnly, isReviewMode, useSavedPreference)
    }

    fun loadQuiz(
        track: String,
        unit: Int?,
        unknownOnly: Boolean,
        isReviewMode: Boolean = false,
        // Home unit-card shortcuts don't pick a type themselves: they replay whatever the owner
        // last chose in Quiz Settings for that unit, falling back to "all words" if unset or if
        // "words I missed" no longer has enough words.
        useSavedPreference: Boolean = false
    ) {
        // Cancel any previous load: before, every quiz left a collector on the whole word
        // table running forever, re-querying thousands of rows after every answer, and an
        // old collector could fill a new quiz with questions from the previous unit.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val length = quizPrefs.getQuizLength()
            var effectiveUnknownOnly = unknownOnly
            var fellBack = false
            if (useSavedPreference) {
                val saved = quizPrefs.getUnknownOnly(track, unit) ?: false
                effectiveUnknownOnly = if (saved) {
                    val hardCount = if (unit == null) {
                        repo.getHardestWordsCount(track).first()
                    } else {
                        repo.getHardestWordsCountByUnit(track, unit).first()
                    }
                    if (hardCount >= length) true else { fellBack = true; false }
                } else {
                    false
                }
            }
            val allWords = repo.getWordsForSession(track, if (isReviewMode) null else unit).first()
            val sessionWords = if (isReviewMode) {
                // Fixed length regardless of the Quiz Settings length slider: the red review
                // card always drills the same-size, hand-picked set of hardest words.
                repo.getHardestWordsForReview(track, REVIEW_WORD_COUNT)
            } else {
                val pool = if (effectiveUnknownOnly) allWords.filter { !it.isKnown } else allWords
                SrsEngine.selectWordsForSession(pool.ifEmpty { allWords }, length)
            }
            val questions = withContext(Dispatchers.Default) {
                sessionWords.map { word -> buildQuestion(word, allWords, track) }
            }
            _state.update {
                it.copy(
                    questions = questions,
                    isLoading = false,
                    // Nothing to ask (e.g. no hard words yet): show the result screen, not a blank page
                    sessionComplete = questions.isEmpty(),
                    fellBackToAllWords = fellBack
                )
            }
        }
    }

    fun consumeFallbackNotice() {
        _state.update { it.copy(fellBackToAllWords = false) }
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

    companion object {
        private const val REVIEW_WORD_COUNT = 10
    }
}
