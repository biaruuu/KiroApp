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
