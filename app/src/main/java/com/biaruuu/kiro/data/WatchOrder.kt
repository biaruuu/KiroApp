package com.biaruuu.kiro.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** One step of a franchise's watch order. [main] = the core story line (TV, ONA, movies). */
data class OrderEntry(
    val id: Int,
    val title: String,
    val format: String,
    val date: Int, // yyyymmdd, 0 = unknown
    val episodes: Int,
    val poster: String,
    val main: Boolean,
    val upcoming: Boolean,
) {
    val year: Int get() = date / 10000

    val formatLabel: String
        get() = when (format) {
            "TV" -> "TV"
            "TV_SHORT" -> "TV Short"
            "MOVIE" -> "Movie"
            "OVA" -> "OVA"
            "ONA" -> "ONA"
            "SPECIAL" -> "Special"
            else -> format.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        }
}

/**
 * Builds the watch order of a franchise: walks PREQUEL / SEQUEL links (the main story) and one step
 * of SIDE_STORY / SPIN_OFF / PARENT links (extras), then sorts everything by release date.
 */
object WatchOrder {
    private const val ENDPOINT = "https://graphql.anilist.co"
    private const val MAX_ENTRIES = 60
    private const val MAX_ROUNDS = 14
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private val MAIN_FORMATS = setOf("TV", "TV_SHORT", "ONA", "MOVIE")
    private val cache = java.util.concurrent.ConcurrentHashMap<Int, List<OrderEntry>>()

    fun cached(id: Int): List<OrderEntry>? = cache[id]

    suspend fun load(startId: Int): List<OrderEntry> = withContext(Dispatchers.IO) {
        val out = walk(startId)
        // Every member of the franchise shares the same order, so cache it for each of them.
        if (out.size > 1) out.forEach { cache[it.id] = out }
        cache[startId] = out
        out
    }

    private fun walk(startId: Int): List<OrderEntry> {
        // id -> true when it belongs to the main story line
        val mainOf = HashMap<Int, Boolean>()
        mainOf[startId] = true
        val seen = LinkedHashSet<Int>().apply { add(startId) }
        val found = HashMap<Int, OrderEntry>()
        var frontier = listOf(startId)

        var round = 0
        while (round++ < MAX_ROUNDS && frontier.isNotEmpty()) {
            val q = StringBuilder("query {")
            frontier.forEachIndexed { i, id ->
                q.append(" a$i: Media(id: $id, type: ANIME) { id format status episodes title { romaji english }")
                    .append(" startDate { year month day } coverImage { large }")
                    .append(" relations { edges { relationType(version: 2) node { id type } } } }")
            }
            q.append(" }")
            val data = post(q.toString()).optJSONObject("data")
            val next = ArrayList<Int>()
            for (i in frontier.indices) {
                val media = data?.optJSONObject("a$i") ?: continue
                val id = media.optInt("id")
                val isMain = mainOf[id] == true
                val format = media.optString("format", "")
                if (format.isNotEmpty() && format != "MUSIC") {
                    found[id] = OrderEntry(
                        id = id,
                        title = titleOf(media),
                        format = format,
                        date = dateOf(media),
                        episodes = if (media.isNull("episodes")) 0 else media.optInt("episodes"),
                        poster = media.optJSONObject("coverImage")?.optString("large").orEmpty(),
                        main = isMain && format in MAIN_FORMATS,
                        upcoming = media.optString("status") == "NOT_YET_RELEASED",
                    )
                }
                val edges = media.optJSONObject("relations")?.optJSONArray("edges") ?: continue
                for (e in 0 until edges.length()) {
                    val edge = edges.optJSONObject(e) ?: continue
                    val node = edge.optJSONObject("node") ?: continue
                    if (node.optString("type") != "ANIME") continue
                    val nodeId = node.optInt("id")
                    val childMain = when (edge.optString("relationType")) {
                        "PREQUEL", "SEQUEL" -> isMain
                        "SIDE_STORY", "SPIN_OFF", "PARENT" -> false
                        else -> continue
                    }
                    if (seen.size < MAX_ENTRIES && seen.add(nodeId)) {
                        mainOf[nodeId] = childMain
                        next.add(nodeId)
                    }
                }
            }
            frontier = next
        }
        return found.values.sortedWith(compareBy({ if (it.date == 0) Int.MAX_VALUE else it.date }, { it.id }))
    }

    private fun titleOf(m: JSONObject): String {
        val t = m.optJSONObject("title")
        val en = if (t == null || t.isNull("english")) "" else t.optString("english")
        val ro = if (t == null || t.isNull("romaji")) "" else t.optString("romaji")
        return en.ifEmpty { ro }.ifEmpty { "Untitled" }
    }

    private fun dateOf(m: JSONObject): Int {
        val d = m.optJSONObject("startDate") ?: return 0
        if (d.isNull("year")) return 0
        // A missing month or day sorts to the start of that period.
        return d.optInt("year") * 10000 + d.optInt("month") * 100 + d.optInt("day")
    }

    private fun post(query: String): JSONObject {
        val body = JSONObject().put("query", query).toString().toRequestBody(JSON_TYPE)
        val request = Request.Builder().url(ENDPOINT).header("Accept", "application/json").post(body).build()
        HttpClientProvider.get().newCall(request).execute().use { r ->
            val text = r.body?.string() ?: "{}"
            if (!r.isSuccessful && !text.contains("\"data\"")) throw java.io.IOException("AniList error (HTTP ${r.code})")
            return JSONObject(text)
        }
    }
}
