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

    @JvmStatic external fun gpuScoreNative(renderer: String): Int
    @JvmStatic external fun enhancerCeilingNative(ramMb: Int, cores: Int, sdk: Int, gpu: Int): Int
    @JvmStatic external fun enhancerStartTierNative(ceiling: Int, videoHeight: Int, fps10: Int, batterySaver: Int, thermal: Int, userMode: Int): Int
    @JvmStatic external fun governorNextNative(cur: Int, ceiling: Int, dropPermille: Int, calmSecs: Int, downgrades: Int): Int

    @JvmStatic external fun upscaleFactorNative(tier: Int, videoW: Int, screenW: Int): Int

    /** Enlargement before sharpening, times 10 (10 = none, 15, 20). */
    @JvmStatic
    fun upscaleFactor(tier: Int, videoW: Int, screenW: Int): Int =
        nat({ upscaleFactorNative(tier, videoW, screenW) }) {
            if (tier < 2 || videoW <= 0 || screenW <= 0) return@nat 10
            val want = screenW.toLong() * 100 / videoW
            var f = if (tier >= 3 && want >= 180) 20 else if (want >= 130) 15 else 10
            while (f > 10 && videoW.toLong() * f / 10 > 2560) f -= 5
            f
        }

    private inline fun <T> nat(native: () -> T, fallback: () -> T): T {
        if (nativeAvailable) {
            try { return native() } catch (_: Throwable) { }
        }
        return fallback()
    }

    /** 0-100 power estimate of a GPU from its GL renderer name. */
    @JvmStatic
    fun gpuScore(renderer: String): Int = nat({ gpuScoreNative(renderer) }) {
        val r = renderer.lowercase()
        fun num(marker: String): Int {
            val i = r.indexOf(marker)
            if (i < 0) return 0
            return r.substring(i + marker.length).dropWhile { !it.isDigit() }.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
        when {
            "adreno" in r -> when (val n = num("adreno")) {
                0 -> 30
                in 1..399 -> 10
                in 400..499 -> 20
                in 500..509 -> 32
                in 510..529 -> 38
                in 530..549 -> 46
                in 600..609 -> 40
                in 610..619 -> 42
                in 620..629 -> 52
                in 630..639 -> 58
                in 640..649 -> 70
                in 650..659 -> 78
                in 660..699 -> 85
                in 700..739 -> 88
                else -> if (n > 0) 95 else 30
            }
            "immortalis" in r || "mali" in r -> {
                val n = num("-g").takeIf { it != 0 } ?: num(" g")
                when {
                    n == 0 -> if ("mali" in r) 8 else 30
                    n < 50 -> 25
                    n < 60 -> 38
                    n < 70 -> 55
                    n <= 72 -> 48
                    n <= 76 -> 60
                    n == 77 -> 70
                    n == 78 -> 76
                    n in 610..699 -> 60
                    n in 700..714 -> 76
                    n in 715..719 -> 82
                    n in 720..799 -> 88
                    else -> 92
                }
            }
            "xclipse" in r -> 80
            "powervr" in r -> if ("ge8" in r || "rogue" in r) 20 else 45
            else -> 30
        }
    }

    /** Strongest enhancer tier (0..3) this device can sustain. */
    @JvmStatic
    fun enhancerCeiling(ramMb: Int, cores: Int, sdk: Int, gpu: Int): Int =
        nat({ enhancerCeilingNative(ramMb, cores, sdk, gpu) }) {
            when {
                sdk < 26 || cores < 4 -> 0
                gpu >= 72 && ramMb >= 5500 && cores >= 6 -> 3
                gpu >= 50 && ramMb >= 3500 -> 2
                gpu >= 24 && ramMb >= 1800 -> 1
                else -> 0
            }
        }

    /** Tier to start with for a video. userMode 0 = auto, 1..3 = chosen tier. */
    @JvmStatic
    fun enhancerStartTier(ceiling: Int, videoHeight: Int, fps10: Int, batterySaver: Boolean, thermal: Int, userMode: Int): Int =
        nat({ enhancerStartTierNative(ceiling, videoHeight, fps10, if (batterySaver) 1 else 0, thermal, userMode) }) {
            var t = if (userMode in 1..3) userMode else ceiling.coerceIn(0, 3)
            if (videoHeight > 1440) return@nat 0
            if (videoHeight > 1080) t = minOf(t, 1)
            if (fps10 >= 500 && videoHeight > 720) t = minOf(t, 1)
            if (batterySaver) t = if (userMode == 0) 0 else minOf(t, 1)
            if (thermal >= 3) return@nat 0
            if (thermal == 2) t = minOf(t, 1)
            maxOf(t, 0)
        }

    /** Next tier after a measuring window: steps down fast on dropped frames, up slowly. */
    @JvmStatic
    fun governorNext(cur: Int, ceiling: Int, dropPermille: Int, calmSecs: Int, downgrades: Int): Int =
        nat({ governorNextNative(cur, ceiling, dropPermille, calmSecs, downgrades) }) {
            val c = ceiling.coerceIn(0, 3)
            when {
                cur > c -> c
                dropPermille >= 250 -> maxOf(cur - 2, 0)
                dropPermille >= 40 -> maxOf(cur - 1, 0)
                dropPermille <= 5 && calmSecs >= 45 && cur < c && downgrades < 2 -> cur + 1
                else -> cur
            }
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
