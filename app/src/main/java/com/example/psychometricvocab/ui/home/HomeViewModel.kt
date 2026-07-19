package com.example.psychometricvocab.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.psychometricvocab.data.VocabDatabase
import com.example.psychometricvocab.data.VocabRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val totalWords: Int = 0,
    val knownWords: Int = 0,
    val units: List<Int> = emptyList(),
    val upcomingReviews: Int = 0
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = VocabRepository(VocabDatabase.getInstance(app).wordDao())

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun loadData(track: String) {
        viewModelScope.launch {
            combine(
                repo.getTotalCount(track),
                repo.getKnownCount(track),
                repo.getAllUnits(track),
                repo.getHardestWordsCount(track)
            ) { total, known, units, hardest ->
                HomeUiState(
                    totalWords = total,
                    knownWords = known,
                    units = units,
                    upcomingReviews = hardest
                )
            }.collect { _uiState.value = it }
        }
    }
}
