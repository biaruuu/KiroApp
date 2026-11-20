package com.biaruuu.kiro.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Extra AniList calls (blocking: run them off the main thread): the weekly airing schedule, which
 * episodes of given anime aired in a time window (for alerts) and "people also liked" lists.
 */
object AniListExtra {
    private const val ENDPOINT = "https://graphql.anilist.co"
    private const val MEDIA =
        "id isAdult title{romaji english native} coverImage{extraLarge large color} bannerImage description " +
            "averageScore episodes format seasonYear genres status"

    /** One episode airing at [airingAt] (epoch seconds). */
    class Airing(val anime: Anime, val episode: Int, val airingAt: Long)

    private fun post(query: String, vars: JSONObject): JSONObject? = try {
        val body = JSONObject().put("query", query).put("variables", vars).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url(ENDPOINT).header("Accept", "application/json").post(body).build()
        HttpClientProvider.get().newCall(req).execute().use { r ->
            if (!r.isSuccessful) null else JSONObject(r.body?.string().orEmpty()).optJSONObject("data")
        }
    } catch (_: Exception) {
        null
    }

    private fun str(o: JSONObject?, k: String): String? = if (o == null || o.isNull(k)) null else o.optString(k)

    fun parse(o: JSONObject): Anime {
        val t = o.optJSONObject("title")
        val c = o.optJSONObject("coverImage")
        val g = o.optJSONArray("genres")
        val genres = if (g == null) emptyList() else (0 until g.length()).map { g.optString(it) }.filter { it.isNotEmpty() }
        return Anime(
            o.optInt("id"), str(t, "romaji"), str(t, "english"), str(t, "native"),
            str(c, "large"), str(c, "extraLarge"), str(c, "color"), str(o, "bannerImage"),
            str(o, "description"), o.optInt("averageScore"), o.optInt("episodes"), str(o, "format"),
            o.optInt("seasonYear"), genres, str(o, "status"),
        ).also { AnimeCache.put(it) }
    }

    private fun airings(arr: JSONArray?): List<Airing> {
        if (arr == null) return emptyList()
        val out = ArrayList<Airing>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val m = o.optJSONObject("media") ?: continue
            if (m.optBoolean("isAdult")) continue
            out.add(Airing(parse(m), o.optInt("episode"), o.optLong("airingAt")))
        }
        return out
    }

    private val scheduleCache = ConcurrentHashMap<Long, Pair<Long, List<Airing>>>()

    /** Everything airing between [startSec] and [endSec], earliest first; null if AniList can't be reached. */
    fun schedule(startSec: Long, endSec: Long): List<Airing>? {
        val key = startSec / 3600 * 100000 + endSec / 3600
        scheduleCache[key]?.let { if (System.currentTimeMillis() - it.first < 10 * 60_000L) return it.second }
        val q = "query(\$s:Int,\$e:Int,\$p:Int){Page(page:\$p,perPage:50){pageInfo{hasNextPage} " +
            "airingSchedules(airingAt_greater:\$s,airingAt_lesser:\$e,sort:TIME){episode airingAt media{$MEDIA}}}}"
        val all = ArrayList<Airing>()
        var page = 1
        while (page <= 8) {
            val data = post(q, JSONObject().put("s", startSec).put("e", endSec).put("p", page))?.optJSONObject("Page")
                ?: return if (all.isEmpty()) null else all.sortedBy { it.airingAt }
            all.addAll(airings(data.optJSONArray("airingSchedules")))
            if (!(data.optJSONObject("pageInfo")?.optBoolean("hasNextPage") ?: false)) break
            page++
        }
        val sorted = all.sortedBy { it.airingAt }
        scheduleCache[key] = System.currentTimeMillis() to sorted
        return sorted
    }

    /** Episodes of the given anime that aired in the window, or null if AniList can't be reached. */
    fun aired(ids: List<Int>, fromSec: Long, toSec: Long): List<Airing>? {
        val out = ArrayList<Airing>()
        val q = "query(\$ids:[Int],\$s:Int,\$e:Int){Page(perPage:50){airingSchedules(mediaId_in:\$ids,airingAt_greater:\$s," +
            "airingAt_lesser:\$e,sort:TIME){episode airingAt media{$MEDIA}}}}"
        for (chunk in ids.chunked(40)) {
            val data = post(q, JSONObject().put("ids", JSONArray(chunk)).put("s", fromSec).put("e", toSec))?.optJSONObject("Page")
                ?: return null
            out.addAll(airings(data.optJSONArray("airingSchedules")))
        }
        return out
    }

    /**
     * Premiere day (as an epoch day in the phone's time zone) of each not-yet-released anime in [ids].
     * Shows that have already started, or have no date yet, are left out. Null when offline.
     */
    fun premieres(ids: List<Int>): Map<Int, Long>? {
        val out = HashMap<Int, Long>()
        val q = "query(\$ids:[Int]){Page(perPage:50){media(id_in:\$ids,type:ANIME){id status startDate{year month day} nextAiringEpisode{airingAt}}}}"
        for (chunk in ids.chunked(40)) {
            val page = post(q, JSONObject().put("ids", JSONArray(chunk)))?.optJSONObject("Page") ?: return null
            val arr = page.optJSONArray("media") ?: continue
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                if (m.optString("status") != "NOT_YET_RELEASED") continue
                val zone = java.time.ZoneId.systemDefault()
                val next = m.optJSONObject("nextAiringEpisode")
                val day: Long? = if (next != null && next.optLong("airingAt", 0L) > 0L) {
                    java.time.Instant.ofEpochSecond(next.getLong("airingAt")).atZone(zone).toLocalDate().toEpochDay()
                } else {
                    val d = m.optJSONObject("startDate")
                    if (d != null && !d.isNull("year") && !d.isNull("month") && !d.isNull("day")) {
                        try { java.time.LocalDate.of(d.getInt("year"), d.getInt("month"), d.getInt("day")).toEpochDay() } catch (_: Exception) { null }
                    } else null
                }
                if (day != null) out[m.getInt("id")] = day
            }
        }
        return out
    }

    private val recCache = ConcurrentHashMap<Int, Pair<Long, List<Anime>>>()

    /** Anime people who liked [id] also liked, best first; empty if none or offline. */
    fun recommendations(id: Int): List<Anime> {
        recCache[id]?.let { if (System.currentTimeMillis() - it.first < 30 * 60_000L) return it.second }
        val q = "query(\$id:Int){Media(id:\$id){recommendations(sort:RATING_DESC,perPage:16){nodes{rating mediaRecommendation{$MEDIA}}}}}"
        val nodes = post(q, JSONObject().put("id", id))?.optJSONObject("Media")?.optJSONObject("recommendations")?.optJSONArray("nodes")
            ?: return emptyList()
        val out = ArrayList<Anime>()
        for (i in 0 until nodes.length()) {
            val m = nodes.optJSONObject(i)?.optJSONObject("mediaRecommendation") ?: continue
            if (m.optBoolean("isAdult")) continue
            out.add(parse(m))
        }
        recCache[id] = System.currentTimeMillis() to out
        return out
    }
}
