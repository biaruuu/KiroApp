package com.biaruuu.kiro.core

import java.util.Calendar

/**
 * Bridge to the Rust library (rust-core). If the native library is missing for any
 * reason (for example a local build without the Rust step) every call falls back to
 * an equivalent Kotlin implementation, so the app never crashes.
 */
object NativeCore {

    private val nativeAvailable: Boolean = try {
        System.loadLibrary("kiro_core")
        true
    } catch (_: Throwable) {
        false
    }

    @JvmStatic
    external fun stripHtml(input: String): String?

    @JvmStatic
    external fun formatScore(score: Int): String?

    @JvmStatic
    external fun seasonForMonth(month: Int): String?

    @JvmStatic
    external fun rankStreamsNative(spec: String): String?

    /**
     * Orders video sources best first by resolution and measured speed (scored in Rust). Returns
     * the indexes into the given arrays.
     */
    @JvmStatic
    fun rankStreams(heights: IntArray, latenciesMs: IntArray, hls: BooleanArray): IntArray {
        val spec = heights.indices.joinToString(";") { i ->
            "${heights[i]},${latenciesMs[i]},${if (hls[i]) 1 else 0}"
        }
        if (nativeAvailable) {
            val r = try {
                rankStreamsNative(spec)
            } catch (_: Throwable) {
                null
            }
            if (r != null) {
                return r.split(",").filter { it.isNotEmpty() }.map { it.toInt() }.toIntArray()
            }
        }
        fun score(i: Int): Double {
            val quality = heights[i].coerceIn(0, 2160) / 2160.0 * 60.0
            val speed = 40.0 * (1.0 - latenciesMs[i].coerceIn(0, 4000) / 4000.0)
            return quality + speed + if (hls[i]) 3.0 else 0.0
        }
        return heights.indices.sortedWith(compareByDescending<Int> { score(it) }.thenBy { it }).toIntArray()
    }

    @JvmStatic
    external fun rankTitlesNative(query: String, titles: String, limit: Int): String?

    /**
     * Instant local search: indexes (into [titles], each entry being all the names of one anime)
     * of the best matches for [query], best first. Rust when available, same rules in Kotlin.
     */
    @JvmStatic
    fun rankTitles(query: String, titles: List<List<String>>, limit: Int): List<Int> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        if (nativeAvailable) {
            val joined = titles.joinToString("\n") { it.joinToString("\t") { n -> n.replace('\n', ' ').replace('\t', ' ') } }
            val r = try {
                rankTitlesNative(q, joined, limit)
            } catch (_: Throwable) {
                null
            }
            if (r != null) return r.split(',').mapNotNull { it.toIntOrNull() }
        }
        return titles.withIndex().mapNotNull { (i, names) ->
            val best = names.maxOfOrNull { n ->
                val l = n.lowercase()
                when {
                    l.startsWith(q) -> 100
                    words(l).any { it.startsWith(q) } -> 94
                    l.contains(q) -> 88
                    else -> similarity(q, l)
                }
            } ?: 0
            if (best >= 55) best to i else null
        }.sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenBy { it.second })
            .take(limit).map { it.second }
    }

    @JvmStatic
    external fun seasonMatchNative(a: String, b: String): Int

    private val FILLER = setOf("season", "part", "cour", "the", "no", "wa", "ga", "tv", "anime", "dub", "sub", "s")

    private fun seasonTokens(s: String): List<String> = s.lowercase()
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .mapNotNull { t ->
            if (t.isEmpty() || t in FILLER) return@mapNotNull null
            val m = Regex("^(\\d+)(st|nd|rd|th)?$").find(t)
            if (m != null) {
                val d = m.groupValues[1]
                if (d.length > 2 && d.length != 4) null else d
            } else t
        }
        .distinct()
        .sorted()

    /**
     * 0-100: do two titles name the same season? Has no "one contains the other" boost and
     * penalises a season number that is on only one side, so a season 2 request can't match
     * the season 1 page. Rust when available, same algorithm in Kotlin otherwise.
     */
    @JvmStatic
    fun seasonMatch(a: String, b: String): Int {
        if (nativeAvailable) {
            val r = try {
                seasonMatchNative(a, b)
            } catch (_: Throwable) {
                -1
            }
            if (r >= 0) return r
        }
        val ta = seasonTokens(a)
        val tb = seasonTokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0
        val inter = ta.count { it in tb }
        val union = ta.size + tb.count { it !in ta }
        val score = inter * 100 / union
        fun nums(v: List<String>) = v.filter { it.length <= 2 && it.all(Char::isDigit) }
        return if (nums(ta) != nums(tb)) score / 2 else score
    }

    @JvmStatic
    external fun formatCountdown(seconds: Long): String?

    @JvmStatic
    external fun compactCount(n: Int): String?

    /** "in 3d 4h 12m 5s" (Rust), with the same output from Kotlin if the library is missing. */
    fun countdown(secondsLeft: Long): String {
        if (nativeAvailable) {
            val r = try {
                formatCountdown(secondsLeft)
            } catch (_: Throwable) {
                null
            }
            if (r != null) return r
        }
        if (secondsLeft <= 0) return "Airing now"
        val d = secondsLeft / 86_400
        val h = (secondsLeft % 86_400) / 3_600
        val m = (secondsLeft % 3_600) / 60
        val sec = secondsLeft % 60
        return buildString {
            append("in ")
            if (d > 0) append("${d}d ")
            if (d > 0 || h > 0) append("${h}h ")
            if (d > 0 || h > 0 || m > 0) append("${m}m ")
            append("${sec}s")
        }
    }

    /** 1234 -> "1.2K" (Rust). */
    fun compact(n: Int): String {
        if (nativeAvailable) {
            val r = try {
                compactCount(n)
            } catch (_: Throwable) {
                null
            }
            if (r != null) return r
        }
        return when {
            n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000.0).replace(".0M", "M")
            n >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", n / 1_000.0).replace(".0K", "K")
            else -> n.toString()
        }
    }

    @JvmStatic
    external fun titleSimilarity(a: String, b: String): Int

    private fun words(s: String) =
        s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    /** 0-100: how alike two titles are. Rust when available, same algorithm in Kotlin otherwise. */
    @JvmStatic
    fun similarity(a: String, b: String): Int {
        if (nativeAvailable) {
            val r = try {
                titleSimilarity(a, b)
            } catch (_: Throwable) {
                -1
            }
            if (r >= 0) return r
        }
        val ta = words(a)
        val tb = words(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0
        val inter = ta.count { it in tb }
        val union = ta.size + tb.count { it !in ta }
        val contained = if (inter == minOf(ta.size, tb.size)) 85 else 0
        return maxOf(inter * 100 / union, contained)
    }

    private val tagRegex = Regex("<[^>]*>")
    private val brRegex = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)

    /** AniList description (HTML-ish) -> clean plain text. */
    fun cleanDescription(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        if (nativeAvailable) {
            val result = try {
                stripHtml(raw)
            } catch (_: Throwable) {
                null
            }
            if (result != null) return result
        }
        return raw
            .replace(brRegex, "\n")
            .replace(tagRegex, "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    /** 0..100 AniList score -> "8.7", or null when there is no score. */
    fun scoreLabel(score: Int?): String? {
        if (score == null || score <= 0) return null
        if (nativeAvailable) {
            val result = try {
                formatScore(score)
            } catch (_: Throwable) {
                null
            }
            if (result != null) return result
        }
        val clamped = score.coerceAtMost(100)
        return "${clamped / 10}.${clamped % 10}"
    }

    /** Current AniList season name and year, e.g. ("FALL", 2026). */
    fun currentSeason(): Pair<String, Int> {
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        val season = if (nativeAvailable) {
            try {
                seasonForMonth(month)
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        } ?: when (month) {
            1, 2, 3 -> "WINTER"
            4, 5, 6 -> "SPRING"
            7, 8, 9 -> "SUMMER"
            else -> "FALL"
        }
        return season to year
    }
}
