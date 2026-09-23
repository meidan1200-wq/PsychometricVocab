package com.example.psychometricvocab.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.psychometricvocab.data.VocabDatabase
import com.example.psychometricvocab.data.VocabRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

data class HomeUiState(
    val totalWords: Int = 0,
    val knownWords: Int = 0,
    val units: List<Int> = emptyList(),
    val upcomingReviews: Int = 0
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
                repo.getHardestWordsCount(t)
            ) { total, known, units, hardest ->
                HomeUiState(
                    totalWords = total,
                    knownWords = known,
                    units = units,
                    upcomingReviews = hardest
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun loadData(track: String) {
        this.track.value = track
    }
}
