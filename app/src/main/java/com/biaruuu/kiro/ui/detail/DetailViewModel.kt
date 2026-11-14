package com.biaruuu.kiro.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.biaruuu.kiro.data.AniListRepository
import com.biaruuu.kiro.data.AnimeCache
import com.biaruuu.kiro.data.AnimeDetail
import com.biaruuu.kiro.data.Anime
import com.biaruuu.kiro.data.ApiErrors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [base] is whatever we already knew from the list the user tapped on, so the page has a title and
 * artwork on the first frame. [detail] arrives once the full data has downloaded.
 */
data class DetailUiState(
    val base: Anime? = null,
    val detail: AnimeDetail? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class DetailViewModel(private val animeId: Int) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState(base = AnimeCache.get(animeId)))
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val detail = AniListRepository.loadDetail(animeId)
                _state.update { it.copy(base = detail.anime, detail = detail, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = ApiErrors.userMessage(e)) }
            }
        }
    }

    class Factory(private val animeId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DetailViewModel(animeId) as T
    }
}
