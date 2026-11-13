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
