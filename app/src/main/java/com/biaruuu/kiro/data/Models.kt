package com.biaruuu.kiro.data

import kotlinx.serialization.Serializable

@Serializable
data class AnimeTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
)

@Serializable
data class CoverImage(
    val extraLarge: String? = null,
    val large: String? = null,
    val color: String? = null,
)

@Serializable
data class Anime(
    val id: Int,
    val title: AnimeTitle = AnimeTitle(),
    val coverImage: CoverImage = CoverImage(),
    val bannerImage: String? = null,
    val description: String? = null,
    val averageScore: Int? = null,
    val episodes: Int? = null,
    val format: String? = null,
    val seasonYear: Int? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
) {
    val displayTitle: String
        get() = title.english?.takeIf { it.isNotBlank() }
            ?: title.romaji?.takeIf { it.isNotBlank() }
            ?: title.native
            ?: "Untitled"

    val posterUrl: String?
        get() = coverImage.large ?: coverImage.extraLarge

    val heroUrl: String?
        get() = coverImage.extraLarge ?: coverImage.large
}

@Serializable
data class PageInfo(
    val hasNextPage: Boolean = false,
)

@Serializable
data class MediaPage(
    val pageInfo: PageInfo? = null,
    val media: List<Anime> = emptyList(),
)

@Serializable
data class GraphQLError(
    val message: String? = null,
)

@Serializable
data class GqlResponse<T>(
    val data: T? = null,
    val errors: List<GraphQLError>? = null,
)

@Serializable
data class HomeData(
    val trending: MediaPage? = null,
    val season: MediaPage? = null,
    val popular: MediaPage? = null,
    val topRated: MediaPage? = null,
    val sports: MediaPage? = null,
    val romance: MediaPage? = null,
    val scifi: MediaPage? = null,
    val fantasy: MediaPage? = null,
    val upcoming: MediaPage? = null,
)

@Serializable
data class SearchData(
    // AniList names this root field "Page" (capitalised), so map it explicitly.
    @kotlinx.serialization.SerialName("Page")
    val page: MediaPage? = null,
)

/** A titled row of anime shown on the Home screen. */
data class HomeSection(
    val title: String,
    val items: List<Anime>,
)

data class HomeContent(
    val hero: List<Anime>,
    val sections: List<HomeSection>,
)

data class SearchResult(
    val items: List<Anime>,
    val hasNext: Boolean,
)
