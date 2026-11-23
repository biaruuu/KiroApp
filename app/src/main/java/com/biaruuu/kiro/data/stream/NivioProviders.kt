package com.biaruuu.kiro.data.stream

import com.biaruuu.kiro.data.HttpClientProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/*
 * More places to stream anime from (Animetsu, Animex and Miruro, the same sources the Nivio app
 * uses). They find the episode by its AniList id. Every address is opened once here with the
 * headers it needs, so only ones that really answer are offered, and the headers travel with the
 * source so the player and the downloader can use them.
 */

internal const val CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
internal val POOL = Executors.newCachedThreadPool()
internal val HEIGHT = Regex("RESOLUTION=\\d+x(\\d+)")

internal fun client(seconds: Long = 18): OkHttpClient =
    HttpClientProvider.get().newBuilder().callTimeout(seconds, TimeUnit.SECONDS).readTimeout(seconds, TimeUnit.SECONDS).build()

internal fun request(url: String, headers: Map<String, String>): Request =
    Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()

internal fun getBytes(url: String, headers: Map<String, String>): ByteArray? = try {
    client().newCall(request(url, headers)).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
} catch (_: Exception) {
    null
}

internal fun getJson(url: String, headers: Map<String, String>): JSONObject? = try {
    getBytes(url, headers)?.let { JSONObject(String(it, Charsets.UTF_8)) }
} catch (_: Exception) {
    null
}

/** [langs]: the audio languages an HLS master playlist declares ("ja", "en"...); empty when it says nothing. */
internal class Checked(val hls: Boolean, val height: Int, val ms: Int, val langs: Set<String> = emptySet())

/** Opens a stream address with its headers: an HLS playlist (with its best height) or a media file. */
internal fun check(url: String, headers: Map<String, String>): Checked? {
    val start = System.nanoTime()
    return try {
        client(8).newCall(request(url, headers + ("Range" to "bytes=0-"))).execute().use { r ->
            if (!r.isSuccessful) return null
            val src = r.body?.source() ?: return null
            val ms = ((System.nanoTime() - start) / 1_000_000L).toInt()
            if (src.peek().let { it.request(7) && it.readUtf8(7) == "#EXTM3U" }) {
                val text = src.readUtf8()
                val langs = Regex("#EXT-X-MEDIA:[^\\n]*TYPE=AUDIO[^\\n]*").findAll(text).mapNotNull { m ->
                    Regex("LANGUAGE=\"([^\"]+)\"").find(m.value)?.groupValues?.get(1)?.lowercase()?.substringBefore('-')
                }.toSet()
                Checked(true, HEIGHT.findAll(text).maxOfOrNull { it.groupValues[1].toInt() } ?: 0, ms, langs)
            } else {
                Checked(false, 0, ms)
            }
        }
    } catch (_: Exception) {
        null
    }
}

internal fun languageCode(label: String): String {
    val l = label.lowercase()
    return when {
        l.startsWith("english") || l == "en" -> "en"
        l.startsWith("spanish") || l.startsWith("espa") -> "es"
        l.startsWith("portug") -> "pt"
        l.startsWith("french") || l.startsWith("fran") -> "fr"
        l.startsWith("german") || l.startsWith("deutsch") -> "de"
        l.startsWith("indonesian") -> "id"
        l.startsWith("arabic") -> "ar"
        else -> "und"
    }
}

/** Subtitle files from a list of {url|file, label|lang|language|name, kind}. Previews and ASS files are left out. */
internal fun subtitlesOf(arr: JSONArray?): List<SubtitleTrack> {
    if (arr == null) return emptyList()
    val out = ArrayList<SubtitleTrack>()
    for (i in 0 until arr.length()) {
        val t = arr.optJSONObject(i) ?: continue
        val url = t.optString("url", t.optString("file", ""))
        if (!url.startsWith("http")) continue
        val kind = t.optString("kind", "").lowercase()
        val low = url.lowercase()
        if (kind == "thumbnails" || "thumbnail" in low || "sprite" in low || "/preview" in low) continue
        if (low.endsWith(".ass") || low.endsWith(".ssa") || low.endsWith(".m3u8")) continue
        var label = listOf("label", "lang", "language", "name").map { t.optString(it, "") }.firstOrNull { it.isNotBlank() } ?: ""
        if (label.isBlank() || label.equals("auto", true) || label.equals("und", true)) label = "English"
        out.add(SubtitleTrack(url, label, languageCode(label), if (low.endsWith(".srt")) "application/x-subrip" else "text/vtt"))
    }
    out.sortBy { it.language != "en" }
    return out
}

internal fun origin(referer: String) = referer.trimEnd('/')

internal fun streamHeaders(referer: String, ua: String = CHROME, extra: Map<String, String> = emptyMap()): Map<String, String> =
    linkedMapOf("User-Agent" to ua, "Referer" to referer, "Origin" to origin(referer)).apply { putAll(extra) }

internal fun source(
    provider: String, label: String, url: String, headers: Map<String, String>, audio: String,
    subs: List<SubtitleTrack>, qualityHint: Int = 0,
): StreamSource? {
    val c = check(url, headers) ?: return null
    // Some servers file a stream under the wrong audio. When the playlist names its audio language, believe it.
    var kind = audio
    val hasJa = c.langs.any { it == "ja" || it == "jpn" }
    val hasEn = c.langs.any { it == "en" || it == "eng" }
    if (audio == "dub" && hasJa && !hasEn) kind = "sub"
    else if (audio == "sub" && hasEn && !hasJa) kind = "dub"
    return StreamSource("$provider · $label", url, if (c.hls) c.height.takeIf { it > 0 } ?: qualityHint else qualityHint, c.hls, subs, kind)
        .withLatency(c.ms).withHeaders(headers)
}

internal fun heightOf(q: String?): Int = Regex("(\\d{3,4})").find(q.orEmpty())?.groupValues?.get(1)?.toInt() ?: 0

/** Runs jobs side by side and gathers what each returns; slow ones are dropped after [seconds]. */
internal fun gather(jobs: List<Callable<List<StreamSource>>>, seconds: Long): List<StreamSource> {
    val out = ArrayList<StreamSource>()
    for (f in POOL.invokeAll(jobs, seconds, TimeUnit.SECONDS)) {
        try {
            out.addAll(f.get())
        } catch (_: Exception) {
        }
    }
    return out
}

internal val AUDIO = listOf("sub", "dub")

// ------------------------------------------------------------------------------------ Animetsu

/** Animetsu's four servers: Bee, Koto, Wave and GG. */
class AnimetsuProvider : StreamProvider {
    override fun getName() = "Animetsu"

    override fun resolve(request: StreamRequest): List<StreamSource> {
        val jobs = ArrayList<Callable<List<StreamSource>>>()
        for (a in AUDIO) {
            jobs.add(Callable { bee(request, a) })
            jobs.add(Callable { listed(request, a, "Koto", "https://anshu.screenscape.me/watch/anikoto/${request.animeId}/$a/anikoto-${request.episode}", "hls", "https://megaplay.buzz/", true) })
            jobs.add(Callable { listed(request, a, "Wave", "https://anshu.screenscape.me/watch/aniwaves/${request.animeId}/$a/aniwaves-${request.episode}", "hls", "https://play.echovideo.ru/", false) })
            jobs.add(Callable { listed(request, a, "GG", "https://api.animetsu.site/watch/animegg/${request.animeId}/$a/animegg-${request.episode}", "mp4", "https://www.animegg.org/", false) })
        }
        return gather(jobs, 26)
    }

    private fun bee(req: StreamRequest, audio: String): List<StreamSource> {
        val referer = "https://beta.zanora.xyz/"
        val api = getJson("https://api.aniclover.cc/api/watch/${req.animeId}/$audio/${req.episode}", streamHeaders(referer)) ?: return emptyList()
        if (!api.optBoolean("success")) return emptyList()
        val url = api.optString("streamUrl", "").ifEmpty { api.optJSONArray("streams")?.optJSONObject(0)?.optString("url", "").orEmpty() }
        if (!url.startsWith("http")) return emptyList()
        val subs = subtitlesOf(api.optJSONArray("subtitles") ?: api.optJSONArray("tracks"))
        return listOfNotNull(source(getName(), "Bee", url, streamHeaders(referer), audio, subs))
    }

    private fun listed(
        req: StreamRequest, audio: String, label: String, apiUrl: String, type: String, defaultReferer: String, withSubs: Boolean,
    ): List<StreamSource> {
        val api = getJson(apiUrl, streamHeaders("https://animetsu.site/")) ?: return emptyList()
        val streams = api.optJSONArray("streams")
        val jobs = ArrayList<Callable<List<StreamSource>>>()
        var shared: List<SubtitleTrack> = emptyList()
        if (withSubs) {
            shared = subtitlesOf(streams?.optJSONObject(0)?.optJSONArray("subtitles") ?: api.optJSONArray("subtitles"))
        }
        var count = 0
        if (streams != null) {
            for (i in 0 until streams.length()) {
                val s = streams.optJSONObject(i) ?: continue
                val url = s.optString("url", "")
                if (s.optString("type") != type || !url.startsWith("http") || count >= 3) continue
                count++
                val referer = if (label == "GG") defaultReferer else s.optString("referer", defaultReferer).ifEmpty { defaultReferer }
                val name = s.optString("server", "").ifEmpty { label }.let { if (it == label) label else "$label $it" }
                val q = heightOf(s.optString("quality", ""))
                jobs.add(Callable { listOfNotNull(source(getName(), name, url, streamHeaders(referer), audio, shared, q)) })
            }
        }
        if (jobs.isEmpty() && type == "hls") {
            val url = api.optString("proxied_stream_url", "").ifEmpty { api.optString("stream_url", "") }
            if (url.startsWith("http")) {
                jobs.add(Callable { listOfNotNull(source(getName(), "$label HD-1", url, streamHeaders(defaultReferer), audio, shared)) })
            }
        }
        return gather(jobs, 22)
    }
}

// -------------------------------------------------------------------------------------- Animex

/** Animex.one, found through the page address of the AniList id. */
class AnimexProvider : StreamProvider {
    override fun getName() = "Animex"

    private val slugs = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private val api = "https://pp.animex.one/rest/api"
    private val headers = mapOf("User-Agent" to CHROME, "Origin" to "https://animex.one", "Referer" to "https://animex.one/", "Accept" to "*/*")

    private fun slug(id: Int): String? {
        slugs[id]?.let { return it }
        val html = getBytes("https://animex.one/anime/$id", headers)?.let { String(it, Charsets.UTF_8) } ?: return null
        val found = Regex("slug:\"([^\"]+)\"").find(html)?.groupValues?.get(1) ?: return null
        slugs[id] = found
        return found
    }

    override fun resolve(request: StreamRequest): List<StreamSource> {
        val slug = slug(request.animeId) ?: return emptyList()
        val servers = getJson("$api/servers?id=$slug&epNum=${request.episode}", headers) ?: return emptyList()
        val jobs = ArrayList<Callable<List<StreamSource>>>()
        for (audio in AUDIO) {
            val list = servers.optJSONArray(if (audio == "dub") "dubProviders" else "subProviders") ?: continue
            for (i in 0 until minOf(list.length(), 3)) {
                val p = list.optJSONObject(i) ?: continue
                val id = p.optString("id", "")
                if (id.isEmpty()) continue
                val name = p.optString("serverName", id)
                jobs.add(Callable { listOfNotNull(sourceFor(request, slug, audio, id, name)) })
            }
        }
        return gather(jobs, 26)
    }

    private fun sourceFor(req: StreamRequest, slug: String, audio: String, providerId: String, name: String): StreamSource? {
        val data = getJson("$api/sources?id=$slug&epNum=${req.episode}&type=$audio&providerId=$providerId", headers) ?: return null
        val sources = data.optJSONArray("sources") ?: return null
        var pick: JSONObject? = null
        for (i in 0 until sources.length()) {
            val s = sources.optJSONObject(i) ?: continue
            if (!s.optString("url", "").startsWith("http")) continue
            val hls = s.optString("url").contains(".m3u8") || s.optString("type") == "video/mpegurl"
            if (pick == null || hls) pick = s
            if (hls) break
        }
        val url = pick?.optString("url") ?: return null
        val extra = HashMap<String, String>()
        data.optJSONObject("headers")?.let { h -> h.keys().forEach { k -> extra[k] = h.optString(k) } }
        val merged = LinkedHashMap(headers).apply { putAll(extra) }
        return source(getName(), name, url, merged, audio, subtitlesOf(data.optJSONArray("tracks")), heightOf(pick.optString("quality", "")))
    }
}

// -------------------------------------------------------------------------------------- Miruro

/** Miruro: the catalogue answers with a scrambled, compressed body that is decoded here. */
class MiruroProvider : StreamProvider {
    override fun getName() = "Miruro"

    private val base = "https://www.miruro.bz"
    private val ids = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private val xorKey = "miruro/catalog".toByteArray()
    private val apiHeaders = mapOf(
        "Accept" to "application/octet-stream, application/json, */*",
        "Accept-Language" to "en-US,en;q=0.9",
        "Referer" to "$base/",
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Mobile Safari/537.36",
        "sec-ch-ua" to "\"Chromium\";v=\"155\", \"Not A(Brand\";v=\"24\"",
        "sec-ch-ua-mobile" to "?1",
        "sec-ch-ua-platform" to "\"Android\"",
        "sec-fetch-site" to "same-origin",
        "sec-fetch-mode" to "cors",
        "sec-fetch-dest" to "empty",
    )

    private fun fetch(url: String): JSONObject? = try {
        client(20).newCall(request(url, apiHeaders)).execute().use { r ->
            if (!r.isSuccessful) return null
            val bytes = r.body?.bytes() ?: return null
            val octet = r.header("Content-Type").orEmpty().contains("octet-stream")
            val text = if (octet) {
                val x = ByteArray(bytes.size) { (bytes[it].toInt() xor xorKey[it % xorKey.size].toInt()).toByte() }
                GZIPInputStream(x.inputStream()).use { String(it.readBytes(), Charsets.UTF_8) }
            } else String(bytes, Charsets.UTF_8)
            JSONObject(text)
        }
    } catch (_: Exception) {
        null
    }

    private fun idFor(req: StreamRequest): String? {
        ids[req.animeId]?.let { return it }
        val titles = listOfNotNull(req.titleRomaji, req.titleEnglish, req.title).filter { it.isNotBlank() }.distinct()
        for (t in titles) {
            val data = fetch("$base/api/v1/anime?q=${java.net.URLEncoder.encode(t, "UTF-8")}&limit=15&sort=-popularity")?.optJSONArray("data") ?: continue
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val al = item.optJSONObject("external_ids")?.optJSONArray("anilist") ?: continue
                for (j in 0 until al.length()) {
                    if (al.opt(j).toString() == req.animeId.toString()) {
                        val id = item.optString("id", "")
                        if (id.isNotEmpty()) { ids[req.animeId] = id; return id }
                    }
                }
            }
        }
        return null
    }

    override fun resolve(request: StreamRequest): List<StreamSource> {
        val id = idFor(request) ?: return emptyList()
        val play = fetch("$base/api/v1/anime/$id/episodes/${request.episode}/play") ?: return emptyList()
        val tracks = play.optJSONArray("tracks") ?: return emptyList()
        val jobs = ArrayList<Callable<List<StreamSource>>>()
        for (i in 0 until tracks.length()) {
            val t = tracks.optJSONObject(i) ?: continue
            val audio = if (t.optString("track").lowercase() == "dub") "dub" else "sub"
            val providers = t.optJSONArray("providers") ?: continue
            var count = 0
            for (pi in 0 until providers.length()) {
                val p = providers.optJSONObject(pi) ?: continue
                val pName = p.optString("provider", "")
                val servers = p.optJSONArray("servers") ?: continue
                for (si in 0 until servers.length()) {
                    val s = servers.optJSONObject(si) ?: continue
                    val h = s.optJSONObject("headers")
                    val referer = h?.optString("Referer", "").orEmpty().ifEmpty { "$base/" }
                    if ("ibyteimg.com" in referer || "vivibebe.site" in referer) continue
                    val stream = s.optJSONArray("streams")?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.firstOrNull { it.optString("url").startsWith("http") } } ?: continue
                    if (count >= 4) continue
                    count++
                    val url = stream.optString("url")
                    val subs = subtitlesOf(s.optJSONArray("subtitles") ?: p.optJSONArray("subtitles") ?: t.optJSONArray("subtitles"))
                    val label = listOf(pName, s.optString("server", "")).filter { it.isNotBlank() }.joinToString(" ").ifEmpty { "Server" }
                    val headers = streamHeaders(referer, extra = mapOf("Origin" to (h?.optString("Origin", "").orEmpty().ifEmpty { origin(referer) })))
                    val q = heightOf(stream.optString("resolution", stream.optString("quality", "")))
                    jobs.add(Callable { listOfNotNull(source(getName(), label, url, headers, audio, subs, q)) })
                }
            }
        }
        return gather(jobs, 26)
    }
}
