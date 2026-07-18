package com.example.psychometricvocab.ui.progress

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.psychometricvocab.data.VocabDatabase
import com.example.psychometricvocab.data.VocabRepository
import com.example.psychometricvocab.data.Word
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    fun loadData(track: String) {
        viewModelScope.launch {
            combine(
                repo.getTotalCount(track),
                repo.getKnownCount(track),
                repo.getUnknownCount(track),
                repo.getUpcomingReviews(track, 30),
                repo.getAllWords(track)
            ) { total, known, unknown, upcoming, allWords ->
                val wordsByUnit = allWords.groupBy { it.unit }
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
            }.collect { _uiState.value = it }
        }
    }
}
