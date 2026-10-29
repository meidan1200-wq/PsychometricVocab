package com.example.psychometricvocab.ui.quiz

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.psychometricvocab.data.ActivityLog
import com.example.psychometricvocab.data.sync.SyncCoordinator
import com.example.psychometricvocab.data.QuizPreferences
import com.example.psychometricvocab.data.PracticeSession
import com.example.psychometricvocab.data.SessionSelector
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

    /**
     * The pure transition behind QuizViewModel.onNext(expectedIndex): moves to the next question
     * only if still on [expectedIndex] AND that question has been answered; otherwise returns
     * this same instance (a duplicate Next for a question that was already left, or a Next that
     * would skip an unanswered question).
     *
     * The "answered" half is what makes a skipped question impossible. The Next button sits in an
     * AnimatedVisibility that stays clickable while it slides out after a tap, and the content
     * lambda it keeps showing is recomposed with the NEW question's index, so a second tap in
     * that window used to pass the new (unanswered) index and advance past it: a 15-question quiz
     * ended with 14 answers. Now every advance requires an answer, so answers == questions.
     */
    fun advanceFrom(expectedIndex: Int): QuizUiState {
        if (currentIndex != expectedIndex) return this
        if (questions.getOrNull(expectedIndex)?.answered != true) return this
        val nextIndex = currentIndex + 1
        return copy(currentIndex = nextIndex, sessionComplete = nextIndex >= questions.size)
    }

    /**
     * Records an answer to the question at [expectedIndex]. Returns this same instance if the quiz
     * is no longer on that question or it was already answered, so a duplicate tap counts once.
     */
    fun withAnswer(expectedIndex: Int, optionId: Int, isCorrect: Boolean): QuizUiState {
        if (currentIndex != expectedIndex) return this
        val current = questions.getOrNull(expectedIndex) ?: return this
        if (current.answered) return this
        val updated = questions.toMutableList()
        updated[expectedIndex] = current.copy(answered = true, selectedOptionId = optionId)
        return copy(
            questions = updated,
            correctCount = if (isCorrect) correctCount + 1 else correctCount,
            wrongCount = if (!isCorrect) wrongCount + 1 else wrongCount
        )
    }
}

class QuizViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = VocabRepository(
        VocabDatabase.getInstance(app).wordDao(),
        ActivityLog(app),
        SyncCoordinator.get(app)::onWordChanged
    )
    private val quizPrefs = QuizPreferences(app)
    private val activityLog = ActivityLog(app)

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
            if (useSavedPreference) {
                val saved = quizPrefs.getUnknownOnly(track, unit) ?: false
                effectiveUnknownOnly = if (saved) {
                    val hardCount = if (unit == null) {
                        repo.getHardestWordsCount(track).first()
                    } else {
                        repo.getHardestWordsCountByUnit(track, unit).first()
                    }
                    // Not enough "missed" words: silently use all words (owner found a notice annoying)
                    hardCount >= length
                } else {
                    false
                }
            }
            val allWords = repo.getWordsForSession(track, if (isReviewMode) null else unit).first()
            val now = System.currentTimeMillis()
            // "All words" quizzes and Practice span every unit, so the unit-importance nudge
            // (lower units are likelier on the real test) applies; a single-unit quiz doesn't.
            val masterMode = isReviewMode || unit == null
            val pool: List<Word>
            val sessionWords = if (isReviewMode) {
                // Personal PRACTICE (the Home "Practice" column): the whole track, and a session
                // size that adapts to how much the user actually practises per day, not the Quiz
                // Settings length.
                val size = PracticeSession.size(PracticeSession.recentActiveCounts(activityLog.getAllCounts()))
                pool = allWords
                SessionSelector.select(pool, size, now, Random.Default, masterMode = true)
            } else {
                // "Only words I didn't know" must still contain a known word the user just got
                // wrong (quiz mode keeps isKnown = true on a miss): filtering on !isKnown alone
                // removed exactly the words the owner wants back.
                pool = (if (effectiveUnknownOnly) SessionSelector.unknownOnlyPool(allWords) else allWords)
                    .ifEmpty { allWords }
                SessionSelector.select(pool, length, now, Random.Default, masterMode)
            }
            // What the selector did, so a device run can be checked from logcat (tag SessionSelector).
            val summary = SessionSelector.summarize(pool, sessionWords, now)
            Log.i(
                "SessionSelector",
                "pool=${summary.pool} unknownOnly=$effectiveUnknownOnly master=$masterMode practice=$isReviewMode " +
                    "recent48hInPool=${summary.recentInPool} recent48hTaken=${summary.recentTaken} " +
                    "newTaken=${summary.newTaken} taken=${summary.taken}"
            )
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

    /** [expectedIndex] is the question the tap belongs to; a stale or duplicate call is ignored. */
    fun onAnswer(expectedIndex: Int, optionId: Int, isCorrect: Boolean) {
        var answeredWord: Word? = null
        _state.update { s ->
            val next = s.withAnswer(expectedIndex, optionId, isCorrect)
            answeredWord = if (next !== s) s.questions[expectedIndex].word else null
            next
        }
        // Only the call that actually flipped the question to answered updates the SRS state.
        answeredWord?.let { word ->
            viewModelScope.launch {
                repo.processAnswer(word, isCorrect, isQuiz = true, isNotSure = (optionId == -1))
            }
        }
    }

    /**
     * Advances ONLY if the quiz is still on [expectedIndex] and that question is answered (see
     * [QuizUiState.advanceFrom]). The check and the advance are one atomic `update`, so a second
     * call for the same question (a double tap, or a tap as the auto-pass timer fires) and a call
     * that would skip an unanswered question are both no-ops.
     */
    fun onNext(expectedIndex: Int) {
        _state.update { it.advanceFrom(expectedIndex) }
    }

}
