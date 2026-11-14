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
