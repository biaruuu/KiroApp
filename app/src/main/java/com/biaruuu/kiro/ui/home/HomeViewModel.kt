package com.biaruuu.kiro.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biaruuu.kiro.data.AniListRepository
import com.biaruuu.kiro.data.HomeContent
import com.biaruuu.kiro.data.ApiErrors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val content: HomeContent) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
        // Keep the Top 10 live: quietly reload every few minutes while the app is open.
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(REFRESH_MS)
                if (_state.value is HomeUiState.Success) {
                    try {
                        _state.value = HomeUiState.Success(AniListRepository.loadHome())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // keep showing what we have
                    }
                }
            }
        }
    }

    private companion object {
        const val REFRESH_MS = 5 * 60 * 1000L
    }

    fun load() {
        _state.value = HomeUiState.Loading
        viewModelScope.launch {
            try {
                _state.value = HomeUiState.Success(AniListRepository.loadHome())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = HomeUiState.Error(ApiErrors.userMessage(e))
            }
        }
    }
}
