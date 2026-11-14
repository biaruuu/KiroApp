package com.biaruuu.kiro.ui.detail

import com.biaruuu.kiro.core.NativeCore
import com.biaruuu.kiro.data.Anime
import com.biaruuu.kiro.data.AnimeDetail
import java.util.Locale

/** 1234 -> "1.2K", 3_400_000 -> "3.4M". */
fun compactCount(n: Int): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000f).replace(".0M", "M")
    n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000f).replace(".0K", "K")
    else -> n.toString()
}

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

/** "in 3d 4h 12m", "in 4h 12m", "in 12m"; "Airing now" once the time has come. */
fun countdownLabel(secondsLeft: Long): String {
    if (secondsLeft <= 0) return "Airing now"
    val days = secondsLeft / 86_400
    val hours = (secondsLeft % 86_400) / 3_600
    val minutes = (secondsLeft % 3_600) / 60
    return buildString {
        append("in ")
        if (days > 0) append("${days}d ")
        if (days > 0 || hours > 0) append("${hours}h ")
        append("${maxOf(minutes, if (days == 0L && hours == 0L) 1 else 0)}m")
    }
}
