package com.example.psychometricvocab.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.psychometricvocab.data.ActivityLog
import com.example.psychometricvocab.data.VocabDatabase
import com.example.psychometricvocab.data.VocabRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate

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
    private val activityLog = ActivityLog(app)

    private val track = MutableStateFlow<String?>(null)

    // Not part of `uiState`: it's language-independent and a plain synchronous SharedPreferences
    // read, not a DB query, so it doesn't need to ride the per-track combine/reload below (that
    // would blank the chart to HomeUiState()'s default every time the language toggle is hit).
    // Cheap by design: read once whenever Home shows (loadData runs fresh each time HomeScreen
    // re-enters composition), not on every answer.
    private val _activityDays = MutableStateFlow<List<Pair<LocalDate, Int>>>(emptyList())
    val activityDays: StateFlow<List<Pair<LocalDate, Int>>> = _activityDays.asStateFlow()

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
                // Aggregated in SQL: loading every word of the track (~4,000 rows) just to count
                // known/total per unit re-ran on every answer while Home was visible.
                repo.getUnitProgress(t)
            ) { total, known, units, hardest, unitProgress ->
                val unitStats = unitProgress.associate { it.unit to (it.known to it.total) }
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
        _activityDays.value = activityLog.getLast7Days()
    }
}
