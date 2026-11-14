package com.biaruuu.kiro.data

import com.biaruuu.kiro.core.NativeCore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Coroutine adapter over the Java [AniListApi]. All networking and JSON parsing lives in Java;
 * this file only turns its callbacks into suspend functions for the Kotlin UI layer.
 */
object AniListRepository {

    suspend fun loadHome(): HomeContent {
        val (season, year) = NativeCore.currentSeason()
        return await<HomeContent> { callback -> AniListApi.loadHome(season, year, callback) }
    }

    suspend fun search(query: String, genre: String?, sort: SortOrder, page: Int): SearchResult =
        await<SearchResult> { callback -> AniListApi.search(query, genre, sort, page, callback) }

    // Kept for the whole session: reopening an anime shows instantly, then refreshes.
    private val detailCache = java.util.concurrent.ConcurrentHashMap<Int, AnimeDetail>()
    private val tmdbCache = java.util.concurrent.ConcurrentHashMap<Int, List<String>>()

    fun cachedDetail(id: Int): AnimeDetail? = detailCache[id]
    fun cachedTmdbTrailers(id: Int): List<String>? = tmdbCache[id]

    suspend fun loadDetail(id: Int): AnimeDetail =
        await<AnimeDetail> { callback -> AniListApi.loadDetail(id, callback) }
            .also { detailCache[id] = it }

    private val seasonCache = java.util.concurrent.ConcurrentHashMap<Int, List<SeasonEntry>>()
    private val episodeCache = java.util.concurrent.ConcurrentHashMap<Int, List<EpisodeInfo>>()

    fun cachedSeasons(id: Int): List<SeasonEntry>? = seasonCache[id]
    fun cachedEpisodes(malId: Int): List<EpisodeInfo>? = episodeCache[malId]

    suspend fun seasons(id: Int): List<SeasonEntry> =
        await<List<SeasonEntry>> { callback -> FranchiseApi.load(id, callback) }
            .also { seasonCache[id] = it }

    suspend fun episodes(malId: Int): List<EpisodeInfo> =
        await<List<EpisodeInfo>> { callback -> EpisodeApi.load(malId, callback) }
            .also { if (it.isNotEmpty()) episodeCache[malId] = it }

    private val tmdbEpisodeCache = java.util.concurrent.ConcurrentHashMap<Int, List<EpisodeInfo>>()

    fun cachedTmdbEpisodes(id: Int): List<EpisodeInfo>? = tmdbEpisodeCache[id]

    suspend fun tmdbEpisodes(anime: Anime, startDate: String): List<EpisodeInfo> =
        await<List<EpisodeInfo>> { callback -> TrailerApi.findTmdbEpisodes(anime, startDate, callback) }
            .also { if (it.isNotEmpty()) tmdbEpisodeCache[anime.id] = it }

    suspend fun tmdbTrailers(anime: Anime): List<String> =
        await<List<String>> { callback -> TrailerApi.findTmdb(anime, callback) }
            .also { if (it.isNotEmpty()) tmdbCache[anime.id] = it }

    suspend fun jikanTrailers(malId: Int): List<String> =
        await<List<String>> { callback -> TrailerApi.findJikan(malId, callback) }

    private suspend fun <T : Any> await(
        start: (AniListApi.Callback<T>) -> AniListApi.Cancelable,
    ): T = suspendCancellableCoroutine { cont ->
        val handle = start(object : AniListApi.Callback<T> {
            override fun onSuccess(result: T) {
                if (cont.isActive) cont.resume(result)
            }

            override fun onError(error: Exception) {
                if (cont.isActive) cont.resumeWithException(error)
            }
        })
        cont.invokeOnCancellation { handle.cancel() }
    }
}
