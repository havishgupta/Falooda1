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
                    _uiState.value = OpenF1UiState.Error("No latest session found.")
                }
            } catch (e: Exception) {
                _uiState.value = OpenF1UiState.Error("Error fetching data: ${e.message}")
            }
        }
    }
}
