package com.biaruuu.kiro.data.stream

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap

/*
 * Providers that find the anime by name (the animestream app's AnimePahe and AniZone, plus Gojo).
 * Because a name can match the wrong season, every candidate is scored against all of the
 * anime's titles, its year and its episode count, and the best one is only used when it is a
 * confident match. The episode is then picked by its real number, never by list position.
 */

private fun norm(s: String): String =
    s.lowercase().replace(Regex("\\(.*?\\)"), " ").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

private val SEASON_WORDS = Regex("\\b(season|part|cour|2nd|3rd|4th|5th|ii|iii|iv)\\b")

private fun tokens(s: String) = norm(s).split(' ').filter { it.isNotEmpty() }.toSet()

internal fun titlesOf(r: StreamRequest): List<String> =
    listOfNotNull(r.titleEnglish, r.titleRomaji, r.title).filter { it.isNotBlank() }.distinctBy { norm(it) }

private fun trailingNumber(s: String): Int =
    Regex("(\\d+)(?:st|nd|rd|th)?\\s*(?:season)?\\s*$").find(norm(s))?.groupValues?.get(1)?.toIntOrNull() ?: 0

/** 0..1: how well a candidate title matches the wanted titles. */
internal fun titleScore(candidate: String, wanted: List<String>): Double {
    val c = tokens(candidate)
    if (c.isEmpty()) return 0.0
    var best = 0.0
    for (w in wanted) {
        val t = tokens(w)
        if (t.isEmpty()) continue
        val inter = c.intersect(t).size.toDouble()
        var score = inter / (c.size + t.size - inter)
        if (norm(candidate) == norm(w)) score = 1.0
        // "Season 2" must not match "Season 3", or a base series.
        val a = trailingNumber(candidate)
        val b = trailingNumber(w)
        val seasonA = SEASON_WORDS.containsMatchIn(norm(candidate)) || a in 2..9
        val seasonB = SEASON_WORDS.containsMatchIn(norm(w)) || b in 2..9
        if (a != b && (seasonA || seasonB)) score *= 0.5
        best = maxOf(best, score)
    }
    return best
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

/** Search queries to try, most specific first. */
internal fun queriesOf(r: StreamRequest): List<String> =
    titlesOf(r).map { it.replace("-", " ").replace(Regex("\\s+"), " ").trim() }.distinct().take(3)

// ----------------------------------------------------------------------------------- AnimePahe

class AnimePaheProvider : StreamProvider {
    override fun getName() = "AnimePahe"

    private val domains = listOf("animepahe.pw", "animepahe.ru", "animepahe.si", "animepahe.com")
    @Volatile private var domain: String? = null
    private val sessions = ConcurrentHashMap<Int, String>()

    private fun api(path: String): JSONObject? {
        val order = listOfNotNull(domain) + domains.filter { it != domain }
        for (d in order) {
            val h = mapOf("User-Agent" to CHROME, "Cookie" to "__ddg1=;__ddg2_=", "Referer" to "https://$d/", "Accept" to "application/json")
            val j = getJson("https://$d/api?$path", h)
            if (j != null) {
                domain = d
                return j
            }
        }
        return null
    }

    private fun site() = "https://${domain ?: domains[0]}/"

    private fun findSession(req: StreamRequest): String? {
        sessions[req.animeId]?.let { return it }
        val wanted = titlesOf(req)
        var best: JSONObject? = null
        var bestScore = 0.0
        for (q in queriesOf(req)) {
            val list = api("m=search&q=${enc(q)}")?.optJSONArray("data") ?: continue
            for (i in 0 until list.length()) {
                val o = list.optJSONObject(i) ?: continue
                var s = titleScore(o.optString("title"), wanted)
                val year = o.optInt("year", 0)
                if (req.year > 0 && year > 0) s += if (year == req.year) 0.1 else -0.15
                val eps = o.optInt("episodes", 0)
                if (req.totalEpisodes > 0 && eps > 0 && eps == req.totalEpisodes) s += 0.05
                if (s > bestScore) {
                    bestScore = s
                    best = o
                }
            }
            if (bestScore >= 0.95) break
        }
        if (best == null || bestScore < 0.6) return null
        val session = best.optString("session", "")
        if (session.isEmpty()) return null
        sessions[req.animeId] = session
        return session
    }

    /** The release entry for exactly this episode number. */
    private fun episodeSession(anime: String, ep: Int): JSONObject? {
        val first = (ep - 1) / 30 + 1
        for (page in listOf(first, first - 1, first + 1)) {
            if (page < 1) continue
            val data = api("m=release&id=$anime&sort=episode_asc&page=$page")?.optJSONArray("data") ?: continue
            for (i in 0 until data.length()) {
                val o = data.optJSONObject(i) ?: continue
                if (o.optDouble("episode", -1.0).toInt() == ep && o.optDouble("episode", -1.0) % 1.0 == 0.0) return o
            }
        }
        return null
    }

    override fun resolve(request: StreamRequest): List<StreamSource> {
        val anime = findSession(request) ?: return emptyList()
        val entry = episodeSession(anime, request.episode) ?: return emptyList()
        val play = "${site()}play/$anime/${entry.optString("session")}"
        val html = getBytes(play, mapOf("User-Agent" to CHROME, "Cookie" to "__ddg1=;__ddg2_=", "Referer" to site()))
            ?.let { String(it, Charsets.UTF_8) } ?: return emptyList()
        val jobs = ArrayList<Callable<List<StreamSource>>>()
        val seen = HashSet<String>()
        for (m in Regex("<button[^>]*data-src=\"([^\"]+)\"[^>]*>", RegexOption.DOT_MATCHES_ALL).findAll(html)) {
            val tag = m.value
            val embed = m.groupValues[1]
            if (!seen.add(embed)) continue
            val res = Regex("data-resolution=\"(\\d+)\"").find(tag)?.groupValues?.get(1)?.toInt() ?: 0
            val dub = Regex("data-audio=\"([^\"]*)\"").find(tag)?.groupValues?.get(1).orEmpty().lowercase().startsWith("eng")
            val fansub = Regex("data-fansub=\"([^\"]*)\"").find(tag)?.groupValues?.get(1).orEmpty()
            jobs.add(Callable {
                val m3u8 = Kwik.extract(embed, site()) ?: return@Callable emptyList<StreamSource>()
                listOfNotNull(
                    source(getName(), listOf(fansub, if (res > 0) "${res}p" else "").filter { it.isNotBlank() }.joinToString(" ").ifEmpty { "Kwik" },
                        m3u8, streamHeaders("https://kwik.cx/"), if (dub) "dub" else "sub", emptyList(), res),
                )
            })
        }
        // Only the best few, so the list stays quick.
        return gather(jobs.take(6), 25)
    }
}

/** Unpacks the Kwik embed page (a packed JavaScript snippet) to find its HLS playlist. */
internal object Kwik {
    private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    fun extract(embed: String, referer: String): String? {
        val html = getBytes(embed, mapOf("User-Agent" to CHROME, "Referer" to referer))?.let { String(it, Charsets.UTF_8) } ?: return null
        val pack = Regex("\\}\\s*\\('(.*)',\\s*(\\d+),\\s*(\\d+),\\s*'(.*?)'\\.split\\('\\|'\\)", RegexOption.DOT_MATCHES_ALL).find(html)
        val text = if (pack != null) {
            val payload = pack.groupValues[1].replace("\\\\", "\\").replace("\\'", "'")
            val radix = pack.groupValues[2].toInt()
            val words = pack.groupValues[4].split('|')
            Regex("\\b\\w+\\b").replace(payload) { t ->
                var n = 0L
                for (ch in t.value) {
                    val d = ALPHABET.indexOf(ch)
                    if (d < 0 || d >= radix) return@replace t.value
                    n = n * radix + d
                }
                words.getOrNull(n.toInt())?.takeIf { it.isNotEmpty() } ?: t.value
            }
        } else html
        return Regex("source\\s*=\\s*['\"]([^'\"]+\\.m3u8[^'\"]*)['\"]").find(text)?.groupValues?.get(1)
    }
}

// -------------------------------------------------------------------------------------- AniZone

class AniZoneProvider : StreamProvider {
    override fun getName() = "AniZone"

    private val base = "https://anizone.to"
    private val pages = ConcurrentHashMap<Int, String>()
    private val headers = mapOf("User-Agent" to CHROME, "Referer" to "$base/")

    private fun html(url: String) = getBytes(url, headers)?.let { String(it, Charsets.UTF_8) }

    /** The JSON a page hands to its Alpine x-data attribute, as a parsed value. */
    private fun xdata(page: String, key: String? = null): Any? {
        val pattern = if (key == null) "JSON\\.parse\\('(.+?)'\\)" else "$key:\\s*JSON\\.parse\\('(.+?)'\\)"
        val raw = Regex(pattern, RegexOption.DOT_MATCHES_ALL).find(
            page.replace("&quot;", "\"").replace("&#039;", "'").replace("&amp;", "&"),
        )?.groupValues?.get(1) ?: return null
        val fixed = raw.replace("\\u0022", "\"")
        return try {
            if (fixed.trimStart().startsWith("[")) JSONArray(fixed) else JSONObject(fixed)
        } catch (_: Exception) {
            null
        }
    }

    private fun alias(req: StreamRequest): String? {
        pages[req.animeId]?.let { return it }
        val wanted = titlesOf(req)
        var best: String? = null
        var bestScore = 0.0
        for (q in queriesOf(req)) {
            val page = html("$base/anime?search=${enc(q)}") ?: continue
            val arr = xdata(page) as? JSONArray ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val s = titleScore(o.optString("main_title"), wanted)
                if (s > bestScore) {
                    bestScore = s
                    best = o.optString("url", "").replace("\\", "")
                }
            }
            if (bestScore >= 0.95) break
        }
        if (best.isNullOrEmpty() || bestScore < 0.6) return null
        pages[req.animeId] = best
        return best
    }

    override fun resolve(request: StreamRequest): List<StreamSource> {
        val url = alias(request) ?: return emptyList()
        val page = html(url) ?: return emptyList()
        val items = (xdata(page, "items") ?: xdata(page)) as? JSONArray ?: return emptyList()
        // Episodes are numbered from 1 in order; refuse a list that can't hold this one.
        if (request.episode < 1 || request.episode > items.length()) return emptyList()
        val epUrl = items.optJSONObject(request.episode - 1)?.optString("url", "")?.replace("\\", "").orEmpty()
        if (!epUrl.startsWith("http")) return emptyList()
        val watch = html(epUrl) ?: return emptyList()
        val data = xdata(watch) as? JSONObject ?: return emptyList()
        val src = data.optString("src", "").replace("\\", "")
        if (!src.startsWith("http")) return emptyList()
        val subs = subtitlesOf(data.optJSONArray("subtitles"))
        return listOfNotNull(source(getName(), "Default", src, streamHeaders("$base/"), "sub", subs))
    }
}

// ---------------------------------------------------------------------------------------- Gojo

/** Gojo (animetsu.live), found by AniList id. */
class GojoProvider : StreamProvider {
    override fun getName() = "Gojo"

    private val api = "https://animetsu.live/v2/api/anime"
    private val proxy = "https://swiftstream.top/proxy"
    private val referer = "https://animetsu.live/"

    private fun absolute(u: String) = if (u.startsWith("/")) proxy + u else u

    override fun resolve(request: StreamRequest): List<StreamSource> {
        val h = streamHeaders(referer)
        val bytes = getBytes("$api/servers/${request.animeId}/${request.episode}", h) ?: return emptyList()
        val text = String(bytes, Charsets.UTF_8).trim()
        val servers: JSONArray = try {
            if (text.startsWith("[")) JSONArray(text) else JSONObject(text).let { it.optJSONArray("servers") ?: it.optJSONArray("data") ?: JSONArray() }
        } catch (_: Exception) {
            return emptyList()
        }
        val jobs = ArrayList<Callable<List<StreamSource>>>()
        for (i in 0 until minOf(servers.length(), 4)) {
            val id = servers.optJSONObject(i)?.optString("id", "") ?: servers.optString(i, "")
            if (id.isEmpty()) continue
            for (audio in AUDIO) {
                jobs.add(Callable {
                    val j = getJson("$api/oppai/${request.animeId}/${request.episode}?server=${enc(id)}&source_type=$audio", h)
                        ?: return@Callable emptyList<StreamSource>()
                    val srcs = j.optJSONArray("sources") ?: return@Callable emptyList<StreamSource>()
                    val subs = subtitlesOf(fixSubs(j.optJSONArray("subs")))
                    val out = ArrayList<StreamSource>()
                    for (k in 0 until minOf(srcs.length(), 2)) {
                        val s = srcs.optJSONObject(k) ?: continue
                        val u = absolute(s.optString("url", ""))
                        if (!u.startsWith("http")) continue
                        source(getName(), id.replaceFirstChar { it.uppercase() }, u, h, audio, subs, heightOf(s.optString("quality")))?.let { out.add(it) }
                    }
                    out
                })
            }
        }
        return gather(jobs, 25)
    }

    private fun fixSubs(arr: JSONArray?): JSONArray? {
        if (arr == null) return null
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.put(JSONObject().put("url", absolute(o.optString("url", ""))).put("label", o.optString("lang", "English")))
        }
        return out
    }
}
