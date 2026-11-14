package com.biaruuu.kiro.ui.detail

import com.biaruuu.kiro.core.NativeCore
import com.biaruuu.kiro.data.Anime
import com.biaruuu.kiro.data.AnimeDetail
import java.util.Locale

/** 1234 -> "1.2K", computed by the Rust core. */
fun compactCount(n: Int): String = NativeCore.compact(n)

/** AniList scores are 0-100; the design shows stars out of 5, e.g. "4.3 (1.2K)". */
fun ratingLabel(averageScore: Int, ratingCount: Int): String? {
    if (averageScore <= 0) return null
    val stars = String.format(Locale.US, "%.1f", averageScore / 20f)
    return if (ratingCount > 0) "$stars (${compactCount(ratingCount)})" else stars
}

fun formatLabel(format: String?): String? = when (format) {
    null -> null
    "TV" -> "TV"
    "TV_SHORT" -> "TV Short"
    "MOVIE" -> "Movie"
    "SPECIAL" -> "Special"
    "MUSIC" -> "Music"
    else -> format // OVA, ONA
}

fun statusLabel(status: String?): String? = when (status) {
    "FINISHED" -> "Complete"
    "RELEASING" -> "Airing"
    "NOT_YET_RELEASED" -> "Upcoming"
    "CANCELLED" -> "Cancelled"
    "HIATUS" -> "Hiatus"
    else -> null
}

/** "TV | 2012.07.08 | Complete", skipping whatever is unknown. */
fun metaLine(anime: Anime, detail: AnimeDetail?): String {
    val date = detail?.startDate?.takeIf { it.isNotEmpty() }
        ?: anime.seasonYear.takeIf { it > 0 }?.toString()
    return listOfNotNull(formatLabel(anime.format), date, statusLabel(anime.status))
        .joinToString(" | ")
}

fun genreLine(anime: Anime): String = anime.genres.take(3).joinToString(" · ")

fun synopsis(anime: Anime): String = NativeCore.cleanDescription(anime.description)

/**
 * The credit rows shown on the full details page, in a fixed order, for roles that exist.
 * Returns (LABEL, names) pairs.
 */
fun creditRows(detail: AnimeDetail): List<Pair<String, String>> {
    fun names(match: (String) -> Boolean): String =
        detail.credits.filter { match(it.role) }.map { it.name }.distinct().joinToString(", ")

    val rows = listOf(
        "DIRECTOR" to names { it.equals("Director", true) },
        "ORIGINAL CREATOR" to names { it.contains("Original Creator", true) },
        "SERIES COMPOSITION" to names { it.startsWith("Series Composition", true) },
        "CHARACTER DESIGN" to names { it.startsWith("Character Design", true) },
        "MUSIC" to names { it.equals("Music", true) },
        "STUDIO" to detail.studios.joinToString(", "),
    )
    return rows.filter { it.second.isNotBlank() }
}

fun shareText(anime: Anime): String =
    "${anime.displayTitle} - https://anilist.co/anime/${anime.id}"

/** "in 3d 4h 12m 5s"; "Airing now" once due. Formatted by the Rust core. */
fun countdownLabel(secondsLeft: Long): String = NativeCore.countdown(secondsLeft)
