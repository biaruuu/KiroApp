package com.biaruuu.kiro.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Intro / outro / recap timestamps from the Aniskip v2 API.
 *
 * Accuracy comes from [durationSec]: Aniskip stores each timestamp for a specific cut of an
 * episode (identified by its length), so we ask for the length of the video that is actually
 * playing, and only fall back to other cuts that are within a few seconds of it.
 */
object SkipTimes {

    class Segment(val type: String, val start: Double, val end: Double) {
        val noun: String
            get() = when (type) {
                "op", "mixed-op" -> "Intro"
                "ed", "mixed-ed" -> "Outro"
                else -> "Recap"
            }

        val label: String
            get() = when (type) {
                "op", "mixed-op" -> "Skip Intro"
                "ed", "mixed-ed" -> "Skip Outro"
                else -> "Skip Recap"
            }
    }

    private class Raw(val segment: Segment, val episodeLength: Double)

    private const val BASE = "https://api.aniskip.com/v2/skip-times"
    private const val CUT_TOLERANCE_SEC = 12.0
    private val cache = HashMap<String, List<Segment>>()

    /**
     * Only timestamps Aniskip has stored for a cut of this episode with (almost) the same length
     * as the video that is playing are used. Entries with no length on record, or for a clearly
     * different cut, would be guesses, so they are dropped: no timestamps means no skip button.
     */
    suspend fun load(malId: Int, episode: Int, durationSec: Double): List<Segment> {
        if (malId <= 0 || episode <= 0 || durationSec < 60) return emptyList()
        val key = "$malId:$episode:${durationSec.roundToInt()}"
        cache[key]?.let { return it }
        // Ask both with this video's length and without one, so no stored cut is missed.
        val raws = query(malId, episode, durationSec) + query(malId, episode, 0.0)
        val found = pickBest(raws, durationSec)
        cache[key] = found
        return found
    }

    private fun pickBest(raws: List<Raw>, durationSec: Double): List<Segment> {
        val out = ArrayList<Segment>()
        val seen = HashSet<String>()
        // Closest cut first, so a type is taken from the cut that best matches this video.
        val verified = raws
            .filter { it.episodeLength > 0 && abs(it.episodeLength - durationSec) <= CUT_TOLERANCE_SEC }
            .sortedBy { abs(it.episodeLength - durationSec) }
        for (r in verified) {
            val s = r.segment
            val length = s.end - s.start
            if (length < 5.0 || length > 300.0 || s.end > durationSec + CUT_TOLERANCE_SEC) continue
            // An opening belongs in the first half of an episode and an ending in the second.
            val opening = s.type == "op" || s.type == "mixed-op"
            val ending = s.type == "ed" || s.type == "mixed-ed"
            if (opening && s.start > durationSec * 0.5) continue
            if (ending && s.start < durationSec * 0.5) continue
            if (seen.add(s.type)) out.add(Segment(s.type, s.start, minOf(s.end, durationSec)))
        }
        return out.sortedBy { it.start }
    }

    private suspend fun query(malId: Int, episode: Int, lengthSec: Double): List<Raw> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE/$malId/$episode".toHttpUrl().newBuilder().apply {
                for (t in listOf("op", "ed", "mixed-op", "mixed-ed", "recap")) addQueryParameter("types[]", t)
                addQueryParameter("episodeLength", if (lengthSec > 0) "%.3f".format(java.util.Locale.US, lengthSec) else "0")
            }.build()
            val request = Request.Builder().url(url).header("Accept", "application/json").build()
            HttpClientProvider.get().newCall(request).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (body.isEmpty()) return@withContext emptyList()
                val json = JSONObject(body)
                if (!json.optBoolean("found")) return@withContext emptyList()
                val results = json.optJSONArray("results") ?: return@withContext emptyList()
                (0 until results.length()).mapNotNull { i ->
                    val o = results.optJSONObject(i) ?: return@mapNotNull null
                    val interval = o.optJSONObject("interval") ?: return@mapNotNull null
                    val start = interval.optDouble("startTime", -1.0)
                    val end = interval.optDouble("endTime", -1.0)
                    if (start < 0 || end <= start) return@mapNotNull null
                    Raw(Segment(o.optString("skipType"), start, end), o.optDouble("episodeLength", 0.0))
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }
}
