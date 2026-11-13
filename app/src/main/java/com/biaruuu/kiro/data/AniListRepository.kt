package com.biaruuu.kiro.data

import com.biaruuu.kiro.core.NativeCore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Talks to the public AniList GraphQL API (https://graphql.anilist.co).
 */
object AniListRepository {

    private const val ENDPOINT = "https://graphql.anilist.co"
    private const val SEARCH_PAGE_SIZE = 24

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private const val MEDIA_FIELDS = """
        fragment M on Media {
          id
          title { romaji english native }
          coverImage { extraLarge large color }
          bannerImage
          description
          averageScore
          episodes
          format
          seasonYear
          genres
          status
        }
    """

    private val HOME_QUERY = """
        query Home(${'$'}season: MediaSeason, ${'$'}seasonYear: Int) {
          trending: Page(page: 1, perPage: 8) {
            media(type: ANIME, sort: TRENDING_DESC, isAdult: false) { ...M }
          }
          season: Page(page: 1, perPage: 16) {
            media(type: ANIME, season: ${'$'}season, seasonYear: ${'$'}seasonYear, sort: POPULARITY_DESC, isAdult: false) { ...M }
          }
          popular: Page(page: 1, perPage: 16) {
            media(type: ANIME, sort: POPULARITY_DESC, isAdult: false) { ...M }
          }
          topRated: Page(page: 1, perPage: 16) {
            media(type: ANIME, sort: SCORE_DESC, isAdult: false) { ...M }
          }
          sports: Page(page: 1, perPage: 16) {
            media(type: ANIME, genre: "Sports", sort: POPULARITY_DESC, isAdult: false) { ...M }
          }
          romance: Page(page: 1, perPage: 16) {
            media(type: ANIME, genre: "Romance", sort: TRENDING_DESC, isAdult: false) { ...M }
          }
          scifi: Page(page: 1, perPage: 16) {
            media(type: ANIME, genre: "Sci-Fi", sort: POPULARITY_DESC, isAdult: false) { ...M }
          }
          fantasy: Page(page: 1, perPage: 16) {
            media(type: ANIME, genre: "Fantasy", sort: POPULARITY_DESC, isAdult: false) { ...M }
          }
          upcoming: Page(page: 1, perPage: 16) {
            media(type: ANIME, status: NOT_YET_RELEASED, sort: POPULARITY_DESC, isAdult: false) { ...M }
          }
        }
        $MEDIA_FIELDS
    """.trimIndent()

    private val SEARCH_QUERY = """
        query Search(${'$'}search: String, ${'$'}genre: String, ${'$'}page: Int, ${'$'}perPage: Int, ${'$'}sort: [MediaSort]) {
          Page(page: ${'$'}page, perPage: ${'$'}perPage) {
            pageInfo { hasNextPage }
            media(type: ANIME, search: ${'$'}search, genre: ${'$'}genre, sort: ${'$'}sort, isAdult: false) { ...M }
          }
        }
        $MEDIA_FIELDS
    """.trimIndent()

    suspend fun loadHome(): HomeContent {
        val (season, year) = NativeCore.currentSeason()
        val variables = buildJsonObject {
            put("season", season)
            put("seasonYear", year)
        }
        val data = execute<HomeData>(HOME_QUERY, variables)

        fun items(page: MediaPage?): List<Anime> = page?.media.orEmpty()

        val hero = items(data.trending).filter { it.heroUrl != null }.take(8)

        val sections = listOf(
            HomeSection("Airing This Season", items(data.season)),
            HomeSection("Popular Right Now", items(data.popular)),
            HomeSection("Can't Hide My Feelings for You!", items(data.romance)),
            HomeSection("Fierce Competition in a Match", items(data.sports)),
            HomeSection("It's Beyond Imagination", items(data.scifi)),
            HomeSection("Magical Worlds You Can't Resist", items(data.fantasy)),
            HomeSection("Top Rated of All Time", items(data.topRated)),
            HomeSection("Coming Soon", items(data.upcoming)),
        ).filter { it.items.isNotEmpty() }

        if (hero.isEmpty() && sections.isEmpty()) {
            throw IOException("AniList returned no anime. Please try again.")
        }
        return HomeContent(hero = hero, sections = sections)
    }

    suspend fun search(query: String, genre: String?, page: Int): SearchResult {
        val variables = buildJsonObject {
            if (query.isNotBlank()) put("search", query)
            if (!genre.isNullOrBlank()) put("genre", genre)
            put("page", page)
            put("perPage", SEARCH_PAGE_SIZE)
            val sort = if (query.isNotBlank()) "SEARCH_MATCH" else "TRENDING_DESC"
            put("sort", JsonArray(listOf(JsonPrimitive(sort))))
        }
        val data = execute<SearchData>(SEARCH_QUERY, variables)
        val result = data.page
        return SearchResult(
            items = result?.media.orEmpty(),
            hasNext = result?.pageInfo?.hasNextPage ?: false,
        )
    }

    private suspend inline fun <reified T> execute(query: String, variables: JsonObject): T {
        val payload = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }.toString()

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Accept", "application/json")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        val body = HttpClientProvider.get().newCall(request).await().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 429) {
                throw IOException("AniList is rate limiting requests. Wait a moment and retry.")
            }
            if (!response.isSuccessful) {
                val message = runCatching {
                    json.decodeFromString<GqlResponse<JsonObject>>(text).errors?.firstOrNull()?.message
                }.getOrNull()
                throw IOException(message ?: "AniList error (HTTP ${response.code})")
            }
            text
        }

        val parsed = json.decodeFromString<GqlResponse<T>>(body)
        val data = parsed.data
        if (data == null) {
            throw IOException(parsed.errors?.firstOrNull()?.message ?: "Empty response from AniList")
        }
        return data
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
    }
}
