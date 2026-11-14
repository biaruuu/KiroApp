package com.biaruuu.kiro.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.biaruuu.kiro.data.AniListRepository
import com.biaruuu.kiro.data.Anime
import com.biaruuu.kiro.data.AnimeCache
import com.biaruuu.kiro.data.AnimeDetail
import com.biaruuu.kiro.data.ApiErrors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [base] is whatever we already knew from the list the user tapped on, so the page has a title and
 * artwork on the first frame. [detail] arrives once the full data has downloaded, and [trailers]
 * (YouTube ids, best first) fill in as each source answers.
 */
data class DetailUiState(
    val base: Anime? = null,
    val detail: AnimeDetail? = null,
    val trailers: List<String> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class DetailViewModel(private val animeId: Int) : ViewModel() {

    private val cachedDetail = AniListRepository.cachedDetail(animeId)
    private val _state = MutableStateFlow(
        DetailUiState(
            base = cachedDetail?.anime ?: AnimeCache.get(animeId),
            detail = cachedDetail,
            loading = cachedDetail == null,
        ),
    )
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var tmdb: List<String> = AniListRepository.cachedTmdbTrailers(animeId).orEmpty()
    private var anilist: List<String> = cachedDetail?.trailerIds.orEmpty()
    private var jikan: List<String> = emptyList()

    init {
        publishTrailers()
        load()
    }

    private fun publishTrailers() {
        val ids = (tmdb + anilist + jikan).distinct()
        _state.update { it.copy(trailers = ids) }
    }

    /** Page data and trailer lookups run at the same time, never one after the other. */
    fun load() {
        _state.update { it.copy(loading = it.detail == null, error = null) }
        val baseReady = CompletableDeferred<Anime?>()
        _state.value.base?.let { baseReady.complete(it) }

        viewModelScope.launch {
            try {
                val detail = AniListRepository.loadDetail(animeId)
                anilist = detail.trailerIds
                _state.update { it.copy(base = detail.anime, detail = detail, loading = false) }
                baseReady.complete(detail.anime)
                publishTrailers()
                if (tmdb.isEmpty() && anilist.isEmpty() && jikan.isEmpty()) {
                    jikan = try {
                        AniListRepository.jikanTrailers(detail.malId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                    publishTrailers()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                baseReady.complete(null)
                // Keep showing what we already have (cached page) instead of an error screen.
                _state.update {
                    it.copy(loading = false, error = if (it.base == null) ApiErrors.userMessage(e) else null)
                }
            }
        }

        if (tmdb.isEmpty()) {
            viewModelScope.launch {
                val anime = baseReady.await() ?: return@launch
                try {
                    tmdb = AniListRepository.tmdbTrailers(anime)
                    publishTrailers()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // TMDB unavailable: the other sources still apply.
                }
            }
        }
    }

    class Factory(private val animeId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DetailViewModel(animeId) as T
    }
}
