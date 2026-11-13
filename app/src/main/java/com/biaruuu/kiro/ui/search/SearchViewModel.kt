package com.biaruuu.kiro.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biaruuu.kiro.data.AniListRepository
import com.biaruuu.kiro.data.Anime
import com.biaruuu.kiro.data.ApiErrors
import com.biaruuu.kiro.data.SortOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val genre: String? = null,
    val sort: SortOrder = SortOrder.POPULARITY,
    val total: Int = 0,
    val results: List<Anime> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val hasNext: Boolean = false,
    val page: Int = 1,
    val loaded: Boolean = false,
)

class SearchViewModel : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        // Empty query shows what's trending so the screen is never blank.
        schedule(debounce = false)
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        schedule(debounce = true)
    }

    fun onGenreToggle(genre: String) {
        _state.update { it.copy(genre = if (it.genre == genre) null else genre) }
        schedule(debounce = false)
    }

    fun onSortChange(sort: SortOrder) {
        if (_state.value.sort == sort) return
        _state.update { it.copy(sort = sort) }
        schedule(debounce = false)
    }

    fun clearQuery() {
        _state.update { it.copy(query = "") }
        schedule(debounce = false)
    }

    fun submit() = schedule(debounce = false)

    fun retry() = schedule(debounce = false)

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.hasNext || current.results.isEmpty()) return
        job?.cancel()
        job = viewModelScope.launch { fetch(current.page + 1) }
    }

    private fun schedule(debounce: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            if (debounce) delay(400)
            fetch(1)
        }
    }

    private suspend fun fetch(page: Int) {
        val snapshot = _state.value
        _state.update {
            if (page == 1) it.copy(loading = true, loadingMore = false, error = null)
            else it.copy(loadingMore = true, error = null)
        }
        try {
            val result = AniListRepository.search(snapshot.query.trim(), snapshot.genre, snapshot.sort, page)
            _state.update { cur ->
                val merged = if (page == 1) {
                    result.items
                } else {
                    val seen = cur.results.mapTo(HashSet()) { it.id }
                    cur.results + result.items.filter { it.id !in seen }
                }
                cur.copy(
                    results = merged,
                    hasNext = result.hasNext(),
                    total = if (page == 1) result.total else cur.total,
                    page = page,
                    loading = false,
                    loadingMore = false,
                    loaded = true,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    loading = false,
                    loadingMore = false,
                    error = ApiErrors.userMessage(e),
                    loaded = true,
                )
            }
        }
    }
}
