package com.example.psychometricvocab.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.psychometricvocab.data.VocabDatabase
import com.example.psychometricvocab.data.VocabRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

data class HomeUiState(
    val totalWords: Int = 0,
    val knownWords: Int = 0,
    val units: List<Int> = emptyList(),
    val upcomingReviews: Int = 0,
    // unit -> (known, total), for each unit card's progress bar on the redesigned Home.
    val unitStats: Map<Int, Pair<Int, Int>> = emptyMap()
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = VocabRepository(VocabDatabase.getInstance(app).wordDao())

    private val track = MutableStateFlow<String?>(null)

    // One upstream per track (flatMapLatest cancels the previous track's queries) and it only
    // runs while the screen is visible (WhileSubscribed), instead of piling up a new
    // never-ending collector every time HomeScreen is shown.
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = track
        .filterNotNull()
        .flatMapLatest { t ->
            combine(
                repo.getTotalCount(t),
                repo.getKnownCount(t),
                repo.getAllUnits(t),
                repo.getHardestWordsCount(t),
                repo.getAllWords(t)
            ) { total, known, units, hardest, allWords ->
                val unitStats = allWords.groupBy { it.unit }
                    .mapValues { (_, words) -> words.count { it.isKnown } to words.size }
                HomeUiState(
                    totalWords = total,
                    knownWords = known,
                    units = units,
                    upcomingReviews = hardest,
                    unitStats = unitStats
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun loadData(track: String) {
        this.track.value = track
    }
}
