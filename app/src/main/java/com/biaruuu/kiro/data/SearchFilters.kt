package com.biaruuu.kiro.data

/** Everything the Search filter sheet can narrow results by. Empty/null means "any". */
data class SearchFilters(
    val genres: Set<String> = emptySet(),
    val year: Int? = null,
    val season: String? = null, // WINTER, SPRING, SUMMER, FALL
    val formats: Set<String> = emptySet(), // TV, MOVIE, OVA, ONA, SPECIAL, TV_SHORT
    val status: String? = null, // RELEASING, FINISHED, NOT_YET_RELEASED
) {
    /** How many filters are on; the genre tabs count as one when a single genre is picked there. */
    val count: Int
        get() = (if (genres.isNotEmpty()) 1 else 0) + (if (year != null) 1 else 0) +
            (if (season != null) 1 else 0) + (if (formats.isNotEmpty()) 1 else 0) + (if (status != null) 1 else 0)

    companion object {
        val SEASONS = listOf("WINTER" to "Winter", "SPRING" to "Spring", "SUMMER" to "Summer", "FALL" to "Fall")
        val FORMATS = listOf("TV" to "TV", "MOVIE" to "Movie", "OVA" to "OVA", "ONA" to "ONA", "SPECIAL" to "Special", "TV_SHORT" to "TV Short")
        val STATUSES = listOf("RELEASING" to "Airing", "FINISHED" to "Finished", "NOT_YET_RELEASED" to "Upcoming")
    }
}
