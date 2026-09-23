package com.example.psychometricvocab.ui.progress

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.psychometricvocab.data.VocabDatabase
import com.example.psychometricvocab.data.VocabRepository
import com.example.psychometricvocab.data.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

data class ProgressUiState(
    val total: Int = 0,
    val known: Int = 0,
    val unknown: Int = 0,
    val upcomingReviews: List<Word> = emptyList(),
    val unitStats: Map<Int, Pair<Int, Int>> = emptyMap(), // unit -> (known, total)
    val wordsByUnit: Map<Int, List<Word>> = emptyMap()
)

class ProgressViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = VocabRepository(VocabDatabase.getInstance(app).wordDao())

    private val track = MutableStateFlow<String?>(null)

    // This loads every word of the track, so it must not keep running in the background:
    // WhileSubscribed stops it 5s after the Progress screen is left, flatMapLatest drops the
    // old track on language switch, and the grouping runs off the main thread.
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProgressUiState> = track
        .filterNotNull()
        .flatMapLatest { t ->
            combine(
                repo.getTotalCount(t),
                repo.getKnownCount(t),
                repo.getUnknownCount(t),
                repo.getUpcomingReviews(t, 30),
                repo.getAllWords(t)
            ) { total, known, unknown, upcoming, allWords ->
                val wordsByUnit = allWords.groupBy { it.unit }.toMutableMap()
                val allKnownWords = allWords.filter { it.isKnown }
                if (allKnownWords.isNotEmpty()) {
                    wordsByUnit[-1] = allKnownWords
                }
                val unitStats = wordsByUnit.mapValues { (_, words) ->
                    words.count { it.isKnown } to words.size
                }
                ProgressUiState(
                    total = total,
                    known = known,
                    unknown = unknown,
                    upcomingReviews = upcoming,
                    unitStats = unitStats,
                    wordsByUnit = wordsByUnit
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    fun loadData(track: String) {
        this.track.value = track
    }
}
