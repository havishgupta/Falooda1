package com.example.f1latest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class OpenF1UiState {
    object Loading : OpenF1UiState()
    data class Success(val session: Session?, val positions: List<Position>, val drivers: List<OpenF1Driver>) : OpenF1UiState()
    data class Fallback(val race: Race) : OpenF1UiState()
    data class Error(val message: String) : OpenF1UiState()
}

class MainViewModel : ViewModel() {
    private val repository = F1Repository()

    private val _uiState = MutableStateFlow<OpenF1UiState>(OpenF1UiState.Loading)
    val uiState: StateFlow<OpenF1UiState> = _uiState.asStateFlow()

    init {
        fetchLatestSessionData()
    }

    fun fetchLatestSessionData() {
        _uiState.value = OpenF1UiState.Loading
        viewModelScope.launch {
            try {
                val session = repository.getLatestSession()
                if (session != null) {
                    val positions = repository.getPositions(session.sessionKey)
                    val drivers = repository.getDrivers(session.sessionKey)
                    _uiState.value = OpenF1UiState.Success(session, positions, drivers)
                } else {
                    fetchFallbackData()
                }
            } catch (e: Exception) {
                if (e.message?.contains("401") == true || e.message?.contains("403") == true) {
                    fetchFallbackData()
                } else {
                    _uiState.value = OpenF1UiState.Error("Error fetching data: ${e.message}")
                }
            }
        }
    }

    private suspend fun fetchFallbackData() {
        try {
            val fallbackRace = repository.getLatestErgastResults()
            if (fallbackRace != null) {
                _uiState.value = OpenF1UiState.Fallback(fallbackRace)
            } else {
                _uiState.value = OpenF1UiState.Error("No active OpenF1 session found and fallback failed. Note: OpenF1 requires a paid API key during live sessions.")
            }
        } catch (e: Exception) {
            _uiState.value = OpenF1UiState.Error("Failed to fetch session. OpenF1 may be paywalled during live sessions: ${e.message}")
        }
    }
}
