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

    suspend fun loadDetail(id: Int): AnimeDetail =
        await<AnimeDetail> { callback -> AniListApi.loadDetail(id, callback) }

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
