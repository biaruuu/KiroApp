package com.biaruuu.kiro.data.stream

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/*
 * Anikoto and Reanime, ported from the Anivexa-API project (providers/anikoto.js, reanime.js and
 * extractors/flixcloud.js, megaplay.js). Both look the show up themselves, so they need no
 * third-party aggregator. Everything is plain HTTP; every address is opened once before it is offered.
 */

private fun avText(url: String, headers: Map<String, String>): String? = getBytes(url, headers)?.let { String(it, Charsets.UTF_8) }
private fun avEnc(s: String) = URLEncoder.encode(s, "UTF-8")
private fun avNorm(s: String?) = s.orEmpty().lowercase().replace(Regex("[^a-z0-9]"), "")

private fun avTitles(r: StreamRequest) =
    listOfNotNull(r.titleRomaji, r.titleEnglish, r.title).filter { it.isNotBlank() }.distinct()

/** Season / part numbers a title spells out ("Season 2", "2nd Season", "Part 3", "Cour 2", "Final Season"). */
private fun avMarkers(title: String): Set<String> {
    val t = title.lowercase()
    val words = mapOf("one" to 1, "first" to 1, "two" to 2, "second" to 2, "three" to 3, "third" to 3, "four" to 4, "fourth" to 4,
        "five" to 5, "fifth" to 5, "six" to 6, "sixth" to 6, "seven" to 7, "seventh" to 7, "eight" to 8, "ninth" to 9, "nine" to 9, "ten" to 10, "tenth" to 10)
    fun num(x: String) = x.toIntOrNull() ?: x.removeSuffix("st").removeSuffix("nd").removeSuffix("rd").removeSuffix("th").toIntOrNull() ?: words[x]
    val out = HashSet<String>()
    val tok = "(\\d+(?:st|nd|rd|th)?|one|first|two|second|three|third|four|fourth|five|fifth|six|sixth|seven|seventh|eight|nine|ninth|ten|tenth)"
    for ((kind, re) in listOf(
        "s" to Regex("\\bseason\\s*$tok\\b"), "s" to Regex("\\b$tok\\s+season\\b"),
        "p" to Regex("\\bpart\\s*$tok\\b"), "p" to Regex("\\b$tok\\s+part\\b"),
        "p" to Regex("\\bcour\\s*$tok\\b"), "p" to Regex("\\b$tok\\s+cour\\b"),
    )) for (m in re.findAll(t)) num(m.groupValues[1])?.let { out.add("$kind$it") }
    if (Regex("\\bfinal season\\b").containsMatchIn(t)) out.add("final")
    if (Regex("\\b(movie|film)\\b").containsMatchIn(t)) out.add("movie")
    if (Regex("\\b(ova|oad)\\b").containsMatchIn(t)) out.add("ova")
    return out
}

/** True when every wanted title disagrees with the candidate about season / part (a missing marker on the candidate is allowed). */
private fun avMarkersConflict(wanted: List<String>, candidate: List<String>): Boolean {
    val found = candidate.flatMap { avMarkers(it) }.toSet()
    if (wanted.isEmpty()) return false
    return wanted.all { w ->
        val exp = avMarkers(w)
        (found.isNotEmpty() && exp.isEmpty()) || (exp.isNotEmpty() && found.isNotEmpty() && exp.intersect(found).isEmpty())
    }
}

private fun avUnent(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replace("&#39;", "'")
    .replace("&lt;", "<").replace("&gt;", ">")

// ------------------------------------------------------------------------------------------ Anikoto

/** Anikoto (the site rotates domains, so the first one that answers is used). */
class AnikotoProvider : StreamProvider {
    override fun getName() = "Anikoto"

    private class Ep(val number: Int, val ids: String, val sub: Boolean, val dub: Boolean, val mal: Int)
    private class Show(val slug: String, val eps: List<Ep>)

    companion object {
        private val HOSTS = listOf("https://anikototv.bz", "https://anikototv.to", "https://anikoto.cz")
        private const val UA_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        @Volatile private var base: String? = null
        private val shows = ConcurrentHashMap<Int, Show>()
    }

    private fun page(extra: Map<String, String> = emptyMap()) =
        linkedMapOf("User-Agent" to UA_WEB, "Accept" to "text/html,*/*") + extra

    private fun host(): String? {
        base?.let { return it }
        for (h in HOSTS) {
            try {
                client(10).newCall(request("$h/", page())).execute().use { r ->
                    if (r.isSuccessful) {
                        val o = r.request.url.let { "${it.scheme}://${it.host}" }
                        base = o
                        return o
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun ajax(url: String, referer: String): JSONObject? =
        getJson(url, page(mapOf("X-Requested-With" to "XMLHttpRequest", "Referer" to referer, "Accept" to "application/json,*/*")))

    private fun episodes(html: String): List<Ep> {
        val out = ArrayList<Ep>()
        for (m in Regex("<a\\s+[^>]*data-id=\"[^\"]*\"[^>]*>").findAll(html)) {
            val tag = m.value
            fun attr(n: String) = Regex("data-$n=\"([^\"]*)\"").find(tag)?.groupValues?.get(1).orEmpty()
            val num = attr("num").toIntOrNull() ?: continue
            out.add(Ep(num, attr("ids"), attr("sub") == "1", attr("dub") == "1", attr("mal").toIntOrNull() ?: 0))
        }
        return out
    }

    private fun inspect(b: String, slug: String): Show? {
        val watch = avText("$b/watch/$slug", page(mapOf("Referer" to "$b/"))) ?: return null
        val id = Regex("data-id=\"(\\d+)\"").find(watch)?.groupValues?.get(1) ?: return null
        val list = ajax("$b/ajax/episode/list/$id", "$b/watch/$slug") ?: return null
        val eps = episodes(list.optString("result", ""))
        return if (eps.isEmpty()) null else Show(slug, eps)
    }

    private fun find(req: StreamRequest, b: String): Show? {
        shows[req.animeId]?.let { return it }
        val found = LinkedHashMap<String, String>()
        for (t in avTitles(req).take(3)) {
            val html = avText("$b/filter?keyword=${avEnc(t)}", page(mapOf("Referer" to "$b/"))) ?: continue
            for (m in Regex("<a\\b([^>]*)>([\\s\\S]*?)</a>").findAll(html)) {
                val slug = Regex("/watch/([^\"/?#]+)").find(Regex("href=[\"']([^\"']+)[\"']").find(m.groupValues[1])?.groupValues?.get(1).orEmpty())
                    ?.groupValues?.get(1) ?: continue
                val name = avUnent(m.groupValues[2].replace(Regex("<[^>]*>"), "").trim())
                if (name.matches(Regex("\\d+"))) continue
                if (slug !in found || found[slug].isNullOrEmpty()) found[slug] = name
            }
        }
        val wanted = avTitles(req).map { avNorm(it) }
        val titlesOf = avTitles(req)
        val ordered = found.entries.filterNot { e ->
            // "Show Season 2" must not be taken for "Show" (or the reverse): drop cards whose season/part marker clashes.
            avMarkersConflict(titlesOf, listOf(e.value, e.key.replace('-', ' ')))
        }.sortedByDescending { e ->
            val n = avNorm(e.value).ifEmpty { avNorm(e.key) }
            when {
                wanted.any { it == n } -> 3
                wanted.any { it.isNotEmpty() && (n.startsWith(it) || it.startsWith(n)) } -> 2
                wanted.any { it.isNotEmpty() && (n.contains(it) || it.contains(n)) } -> 1
                else -> 0
            }
        }.take(6)
        val jobs = ordered.map { e -> Callable { inspect(b, e.key) } }
        val inspected = POOL.invokeAll(jobs, 20, java.util.concurrent.TimeUnit.SECONDS).mapNotNull { runCatching { it.get() }.getOrNull() }
        val pick = (if (req.malId > 0) inspected.firstOrNull { s -> s.eps.any { it.mal == req.malId } } else null)
            ?: inspected.firstOrNull()
        pick?.let { shows[req.animeId] = it }
        return pick
    }

    override fun resolve(request: StreamRequest): List<StreamSource> {
        val b = host() ?: return emptyList()
        val show = find(request, b) ?: run { base = null; return emptyList() } // the domain may have rotated: look again next time
        val jobs = ArrayList<Callable<List<StreamSource>>>()
        for (audio in AUDIO) {
            val ep = show.eps.firstOrNull { it.number == request.episode && (if (audio == "dub") it.dub else it.sub) } ?: continue
            jobs.add(Callable { forAudio(b, show.slug, ep, audio) })
        }
        return gather(jobs, 28)
    }

    private fun forAudio(b: String, slug: String, ep: Ep, audio: String): List<StreamSource> {
        val html = ajax("$b/ajax/server/list?servers=${avEnc(ep.ids)}", "$b/")?.optString("result", "") ?: return emptyList()
        class Item(val linkId: String, val name: String, val type: String)
        val items = ArrayList<Item>()
        for (t in Regex("<div class=\"type\" data-type=\"([^\"]+)\">([\\s\\S]*?)</ul>\\s*</div>").findAll(html)) {
            val type = t.groupValues[1]
            for (li in Regex("<li\\s+([^>]*data-link-id[^>]*)>([\\s\\S]*?)</li>").findAll(t.groupValues[2])) {
                val link = Regex("data-link-id=\"([^\"]+)\"").find(li.groupValues[1])?.groupValues?.get(1) ?: continue
                val name = li.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
                if (type == "dl" || name.contains("download", true) || name.contains("kiwi", true)) continue
                if (type == audio || (audio == "sub" && type == "hsub")) items.add(Item(link, name, type))
            }
        }
        val seen = HashSet<String>()
        val jobs = ArrayList<Callable<List<StreamSource>>>()
        for (item in items) {
            if (!seen.add("${item.name}:${item.type}") || seen.size > 5) continue
            jobs.add(Callable { server(b, item.linkId, item.name, audio) })
        }
        return gather(jobs, 24)
    }

    private fun server(b: String, linkId: String, name: String, audio: String): List<StreamSource> {
        val embed = (if (linkId.startsWith("http")) linkId else ajax("$b/ajax/server?get=${avEnc(linkId)}", "$b/")?.optJSONObject("result")?.optString("url"))
            ?.takeIf { it.startsWith("http") } ?: return emptyList()
        val out = ArrayList<StreamSource>()
        if ("#aHR0c" in embed) {
            val direct = try { String(Base64.decode(embed.substringAfter('#'), Base64.DEFAULT)) } catch (_: Exception) { "" }
            if (".m3u8" in direct) {
                source(getName(), name, direct, streamHeaders(origin(embed) + "/"), audio, emptyList())?.let { out.add(it) }
            }
        }
        if (out.isEmpty()) {
            val details = try { MegaPlay.extract(embed) } catch (_: Exception) { null } ?: return out
            val referer = details.origin + "/"
            val subs = subtitlesOf(details.tracks)
            for ((url, _) in details.sources) {
                source(getName(), name, url, streamHeaders(referer), audio, subs)?.let { out.add(it) }
            }
        }
        return out
    }
}

// ------------------------------------------------------------------------------------------ MegaPlay

internal object MegaPlay {
    class Details(val origin: String, val sources: List<Pair<String, String>>, val tracks: JSONArray?)

    private fun unescape(v: String) = Regex("\\\\u([0-9A-Fa-f]{4})|\\\\x([0-9A-Fa-f]{2})|\\\\([\\\\'\"bnfrtv0])").replace(v) { m ->
        when {
            m.groupValues[1].isNotEmpty() -> m.groupValues[1].toInt(16).toChar().toString()
            m.groupValues[2].isNotEmpty() -> m.groupValues[2].toInt(16).toChar().toString()
            else -> when (m.groupValues[3]) { "b" -> "\b"; "n" -> "\n"; "f" -> "\u000c"; "r" -> "\r"; "t" -> "\t"; "v" -> "\u000b"; "0" -> "\u0000"; else -> m.groupValues[3] }
        }
    }

    private fun strings(script: String): List<String> {
        val out = LinkedHashSet<String>()
        for (m in Regex("\"((?:\\\\.|[^\"\\\\\\n])*)\"|'((?:\\\\.|[^'\\\\\\n])*)'").findAll(script)) {
            out.add(unescape(m.groupValues[1].ifEmpty { m.groupValues[2] }))
        }
        return out.toList()
    }

    private fun decrypt(value: String?, script: String): String? {
        if (value.isNullOrEmpty()) return null
        val data = try { Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) } catch (_: Exception) { return null }
        if (data.isEmpty() || data.size % 16 != 0) return null
        val vals = strings(script).filter { it.toByteArray().size in 1..32 }
        val ivs = vals.filter { it.toByteArray().size == 16 }
        for (k in vals) {
            val key = ByteArray(32).also { k.toByteArray().copyInto(it) }
            for (iv in ivs) {
                try {
                    val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv.toByteArray()))
                    val json = JSONObject(String(c.doFinal(data), Charsets.UTF_8))
                    val f = json.optString("file", "").ifEmpty { json.optString("url", "") }
                        .ifEmpty { json.optJSONObject("sources")?.optString("file", "").orEmpty() }
                        .ifEmpty { json.optJSONArray("sources")?.optJSONObject(0)?.optString("file", "").orEmpty() }
                    if (f.isNotEmpty()) return f
                } catch (_: Exception) {
                }
            }
        }
        return null
    }

    fun extract(embed: String): Details? {
        val uri = URI(embed)
        val origin = "${uri.scheme}://${uri.host}"
        val ua = CHROME
        val html = avText(embed, mapOf("User-Agent" to ua, "Accept" to "text/html,*/*", "Referer" to "https://hianimes.re/")) ?: return null
        val fileId = Regex("data-id=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1) ?: return null
        val scripts = Regex("<script[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).findAll(html).map { uri.resolve(it.groupValues[1]).toString() }
            .map { u -> Callable { avText(u, mapOf("User-Agent" to ua, "Referer" to embed)) } }.toList()
            .let { jobs -> POOL.invokeAll(jobs, 15, java.util.concurrent.TimeUnit.SECONDS).mapNotNull { runCatching { it.get() }.getOrNull() } }
        val script = scripts.firstOrNull { Regex("getSources", RegexOption.IGNORE_CASE).containsMatchIn(it) && Regex("AES-CBC", RegexOption.IGNORE_CASE).containsMatchIn(it) } ?: return null
        val routes = strings(script).filter { Regex("^stream/getSources[\\w/-]*$", RegexOption.IGNORE_CASE).matches(it) }.sortedBy { it.length }
        val legacy = routes.firstOrNull()
        val modern = routes.firstOrNull { it != legacy && legacy != null && it.startsWith(legacy) }
        val headers = mapOf("User-Agent" to ua, "Accept" to "application/json,*/*", "Referer" to embed, "X-Requested-With" to "XMLHttpRequest")
        fun call(route: String?) = route?.let { getJson("$origin/$it?id=$fileId&id=$fileId", headers) }
        val m = call(modern)
        val l = call(legacy)
        fun urlOf(d: JSONObject?) = d?.optJSONObject("sources")?.optString("file", "")?.takeIf { it.isNotEmpty() } ?: decrypt(d?.optString("enc"), script)
        val sources = listOfNotNull(urlOf(m)?.let { it to "modern" }, urlOf(l)?.let { it to "legacy" }).distinctBy { it.first }
        if (sources.isEmpty()) return null
        return Details(origin, sources, (m ?: l)?.optJSONArray("tracks"))
    }
}

// ------------------------------------------------------------------------------------------- Reanime

/** Reanime.to, with FlixCloud's protected addresses and manifests decoded here. */
class ReanimeProvider : StreamProvider {
    override fun getName() = "Reanime"

    private val base = "https://reanime.to"
    private val flix = "https://flixcloud.cc"
    private val api = mapOf("User-Agent" to CHROME, "Accept" to "application/json, */*")
    private val ids = ConcurrentHashMap<Int, String>()

    private fun coverId(cover: JSONObject?): Int? {
        for (k in listOf("extra_large", "large", "medium")) {
            Regex("anilist\\.co/.*/bx(\\d+)-").find(cover?.optString(k, "").orEmpty())?.let { return it.groupValues[1].toInt() }
        }
        return null
    }

    private fun empty(c: JSONObject): Boolean {
        val counts = listOf("subbed", "dubbed", "episodes").filter { c.has(it) && !c.isNull(it) }.map { c.optInt(it, 0) }
        return counts.isNotEmpty() && counts.all { it <= 0 }
    }

    private fun seriesFor(req: StreamRequest): String? {
        ids[req.animeId]?.let { return it }
        val found = LinkedHashMap<String, JSONObject>()
        for (t in avTitles(req).take(3)) {
            val results = getJson("$base/api/v1/search?q=${avEnc(t)}&limit=10", api)?.optJSONArray("results") ?: continue
            for (i in 0 until results.length()) {
                val r = results.optJSONObject(i) ?: continue
                val id = r.optString("anime_id", "")
                if (id.isNotEmpty()) found.putIfAbsent(id, r)
            }
        }
        for ((id, r) in found) {
            if (coverId(r.optJSONObject("cover_image")) == req.animeId && !empty(r)) { ids[req.animeId] = id; return id }
        }
        val rest = found.filter { coverId(it.value.optJSONObject("cover_image")) == null && !empty(it.value) }.keys.take(8)
        val details = POOL.invokeAll(rest.map { id -> Callable { id to getJson("$base/api/v1/anime/$id", api) } }, 15, java.util.concurrent.TimeUnit.SECONDS)
            .mapNotNull { runCatching { it.get() }.getOrNull() }
        details.firstOrNull { it.second?.optInt("anilist_id", -1) == req.animeId }?.let { ids[req.animeId] = it.first; return it.first }
        if (req.malId > 0) details.firstOrNull { it.second?.optInt("mal_id", -1) == req.malId }?.let { ids[req.animeId] = it.first; return it.first }
        return null
    }

    override fun resolve(request: StreamRequest): List<StreamSource> {
        val slug = seriesFor(request) ?: return emptyList()
        val watch = getJson("$base/api/watch/$slug/${request.episode}", api)
        val flixData = getJson("$base/api/flix/${request.animeId}/${request.episode}", api)
        val links = ArrayList<JSONObject>()
        val seen = HashSet<String>()
        watch?.optJSONArray("episode_links")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { links.add(it); seen.add(it.optString("\$id")) } }
        if (flixData?.optBoolean("success") == true) {
            flixData.optJSONArray("servers")?.let { a ->
                for (i in 0 until a.length()) a.optJSONObject(i)?.let { if (seen.add(it.optString("\$id"))) links.add(it) }
            }
        }
        val order = mapOf("HD-2" to 0, "HD-1" to 1)
        val jobs = ArrayList<Callable<List<StreamSource>>>()
        for (audio in AUDIO) {
            val types = if (audio == "sub") listOf("sub", "s-sub") else listOf("dub", "s-dub")
            val chosen = links.filter { it.optString("dataType") in types && it.optString("dataLink").startsWith("http") }
                .sortedBy { order[it.optString("serverName")] ?: 9 }
                .distinctBy { it.optString("serverName") + it.optString("dataType") + it.optString("dataLink") }.take(3)
            for (s in chosen) jobs.add(Callable { listOfNotNull(server(s, audio)) })
        }
        return gather(jobs, 30)
    }

    private fun server(s: JSONObject, audio: String): StreamSource? {
        val html = avText(s.optString("dataLink"), api + ("Referer" to "$base/")) ?: return null
        val f = FlixCloud.extract(html, flix, api, "$base/") ?: return null
        val headers = streamHeaders("$flix/")
        val playable = FlixCloud.playable(f.url, f.key, headers) ?: return null
        val subs = subtitlesOf(f.subtitles)
        val c = check(playable, headers)
        val hls = c?.hls ?: true
        if (c == null && !playable.startsWith("file:")) return null
        return StreamSource("${getName()} · ${s.optString("serverName", "Server")}", playable, c?.height ?: 0, hls, subs, audio).withLatency(c?.ms ?: 300).withHeaders(headers)
    }
}

// ----------------------------------------------------------------------------------------- FlixCloud

internal object FlixCloud {
    class Result(val url: String, val key: String?, val subtitles: JSONArray?)

    private fun sha(v: ByteArray) = MessageDigest.getInstance("SHA-256").digest(v)
    private fun hex(v: ByteArray) = v.joinToString("") { "%02x".format(it) }
    private fun shaHex(s: String) = hex(sha(s.toByteArray()))
    private fun b64(v: String) = Base64.decode(v, Base64.DEFAULT)

    private fun leb(b: ByteArray, at: Int): Pair<Int, Int> {
        var p = at; var v = 0; var shift = 0; var n: Int
        do { n = b[p++].toInt() and 0xFF; v = v or ((n and 127) shl shift); shift += 7 } while (n and 128 != 0)
        return v to p
    }

    private fun playlistKey(w: ByteArray): String? {
        var p = 8
        while (p < w.size) {
            val section = w[p++].toInt() and 0xFF
            val (size, np) = leb(w, p); p = np
            if (section == 11) {
                val end = p + size
                var d = p
                val (count, cp) = leb(w, d); d = cp
                for (i in 0 until count) {
                    if (d >= end) break
                    d = leb(w, d).second
                    if ((w[d].toInt() and 0xFF) != 65) break
                    d++
                    d = leb(w, d).second
                    if ((w[d].toInt() and 0xFF) == 11) d++
                    val (len, lp) = leb(w, d); d = lp
                    val seg = w.copyOfRange(d, minOf(d + len, w.size))
                    d += len
                    if (seg.size >= 64) {
                        val out = ByteArray(32) { (seg[it].toInt() xor seg[it + 32].toInt()).toByte() }
                        return Base64.encodeToString(out, Base64.NO_WRAP)
                    }
                }
            }
            p += size
        }
        return null
    }

    private class Wasm(val step: Int, val code: ByteArray) {
        fun transform(input: Int): Int {
            var local = input and 255
            val st = ArrayList<Int>()
            var i = 0
            while (i < code.size) {
                when (code[i++].toInt() and 0xFF) {
                    32 -> { val (li, n) = leb(code, i); i = n; st.add(if (li == 6) local else 0) }
                    33 -> { val (li, n) = leb(code, i); i = n; val v = st.removeAt(st.size - 1); if (li == 6) local = v and 255 }
                    65 -> { val (v, n) = leb(code, i); i = n; st.add(v) }
                    106 -> { val r = st.removeAt(st.size - 1); val l = st.removeAt(st.size - 1); st.add((l + r) and 255) }
                    107 -> { val r = st.removeAt(st.size - 1); val l = st.removeAt(st.size - 1); st.add((l - r + 256) and 255) }
                    113 -> { val r = st.removeAt(st.size - 1); val l = st.removeAt(st.size - 1); st.add(l and r and 255) }
                    114 -> { val r = st.removeAt(st.size - 1); val l = st.removeAt(st.size - 1); st.add(l or (r and 255)) }
                    115 -> { val r = st.removeAt(st.size - 1); val l = st.removeAt(st.size - 1); st.add(l xor (r and 255)) }
                    116 -> { val r = st.removeAt(st.size - 1); val l = st.removeAt(st.size - 1); st.add((l shl (r and 7)) and 255) }
                    118 -> { val r = st.removeAt(st.size - 1); val l = st.removeAt(st.size - 1); st.add((l ushr (r and 7)) and 255) }
                }
            }
            return local
        }
    }

    private fun parseWasm(b: ByteArray): Wasm {
        var p = 8
        while (p < b.size) {
            val section = b[p++].toInt() and 0xFF
            val (size, np) = leb(b, p); p = np
            if (section == 10) {
                p++
                val (bodySize, bp) = leb(b, p); p = bp
                p += bodySize
                break
            }
            p += size
        }
        val (fsize, fp) = leb(b, p); p = fp
        val body = b.copyOfRange(p, minOf(p + fsize, b.size))
        val xorEnd = intArrayOf(32, 2, 32, 5, 106, 45, 0, 0, 115, 33, 6)
        var start = -1
        outer@ for (i in 0 until body.size - xorEnd.size) {
            for (j in xorEnd.indices) if ((body[i + j].toInt() and 0xFF) != xorEnd[j]) continue@outer
            start = i + xorEnd.size
            break
        }
        if (start < 0) throw IllegalStateException("wasm transform")
        var end = -1
        var step = 36
        for (i in start until body.size - 4) {
            if ((body[i].toInt() and 0xFF) == 32 && (body[i + 1].toInt() and 0xFF) == 5 && (body[i + 2].toInt() and 0xFF) == 65) {
                val (v, n) = leb(body, i + 3)
                if ((body[n].toInt() and 0xFF) == 108) { end = i; step = v; break }
            }
        }
        if (end < 0) throw IllegalStateException("wasm keystream")
        return Wasm(step, body.copyOfRange(start, end))
    }

    private fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        var u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
        val t = u.copyOf()
        for (i in 1 until iterations) {
            u = mac.doFinal(u)
            for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
        }
        return t
    }

    // ---- the page's data block (a JavaScript object literal)
    private class Lit(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun str(q: Char): String {
            val sb = StringBuilder(); i++
            while (i < s.length && s[i] != q) {
                if (s[i] == '\\') {
                    i++
                    sb.append(when (s[i]) { 'n' -> '\n'; 't' -> '\t'; 'r' -> '\r'; else -> s[i] })
                    i++
                } else sb.append(s[i++])
            }
            i++
            return sb.toString()
        }
        fun key(): String {
            ws()
            if (s[i] == '"' || s[i] == '\'') return str(s[i])
            val m = Regex("^[a-zA-Z_$][a-zA-Z0-9_$]*").find(s.substring(i)) ?: throw IllegalStateException("key")
            i += m.value.length
            return m.value
        }
        fun obj(): JSONObject {
            val o = JSONObject(); i++; ws()
            while (i < s.length && s[i] != '}') {
                if (s[i] == ',') { i++; ws(); continue }
                val k = key(); ws(); i++
                o.put(k, value()); ws()
            }
            i++
            return o
        }
        fun arr(): JSONArray {
            val a = JSONArray(); i++; ws()
            while (i < s.length && s[i] != ']') {
                if (s[i] == ',') { i++; ws(); continue }
                a.put(value()); ws()
            }
            i++
            return a
        }
        fun value(): Any? {
            ws()
            return when {
                s[i] == '{' -> obj()
                s[i] == '[' -> arr()
                s[i] == '"' || s[i] == '\'' -> str(s[i])
                s.startsWith("true", i) -> { i += 4; true }
                s.startsWith("false", i) -> { i += 5; false }
                s.startsWith("null", i) -> { i += 4; JSONObject.NULL }
                s.startsWith("undefined", i) -> { i += 9; JSONObject.NULL }
                s.startsWith("!0", i) -> { i += 2; true }
                s.startsWith("!1", i) -> { i += 2; false }
                else -> {
                    val m = Regex("^-?[\\d.]+([eE][+-]?\\d+)?").find(s.substring(i)) ?: throw IllegalStateException("literal")
                    i += m.value.length
                    m.value.toDouble()
                }
            }
        }
    }

    private fun dataBlock(html: String): JSONObject {
        val m = Regex("\\{type:\"data\",data:(\\{)").find(html) ?: throw IllegalStateException("no data block")
        val start = html.indexOf('{', m.range.last - 1)
        var depth = 0
        for (i in start until html.length) {
            if (html[i] == '{') depth++ else if (html[i] == '}' && --depth == 0) return Lit(html.substring(start, i + 1)).obj()
        }
        throw IllegalStateException("braces")
    }

    fun extract(html: String, apiBase: String, headers: Map<String, String>, referer: String): Result? = try {
        val data = dataBlock(html)
        val seed = data.optString("obfuscation_seed", "")
        check(seed.isNotEmpty())
        var first = seed
        for (i in 0 until 3) first = shaHex(first + i)
        var second = first
        for (i in 0 until 3) second = shaHex(second + i)
        val keyField = "kf_" + first.substring(8, 16)
        val ivField = "ivf_" + first.substring(16, 24)
        val container = "cd_" + first.substring(24, 32)
        val arrayName = "ad_" + first.substring(32, 40)
        val objectName = "od_" + first.substring(40, 48)
        val tokenField = first.substring(48, 64) + "_" + first.substring(56, 64)
        val kf2 = second.substring(0, 16) + "_" + second.substring(16, 24)
        val obj = data.getJSONObject("obfuscated_crypto_data").getJSONObject(container).getJSONArray(arrayName).getJSONObject(0).getJSONObject(objectName)
        val fragment = b64(obj.getString(keyField))
        val iv = b64(obj.getString(ivField))
        val keyFragment = b64(data.getString(kf2))
        val token = data.getString(tokenField)
        val tokenData = getJson("$apiBase/api/m3u8/$token", headers + ("Referer" to referer)) ?: return null
        val videoBytes = b64(tokenData.getString(shaHex(token + "vid").substring(0, 10)))
        val tokenBytes = b64(tokenData.getString(shaHex(token + "key").substring(0, 10)))
        val seedNumber = seed.substring(0, 8).toLong(16)
        val wasm = b64(data.getString("w_payload"))
        val key = playlistKey(wasm)
        val w = parseWasm(wasm)
        val out = ByteArray(fragment.size)
        for (i in fragment.indices) {
            val v = (fragment[i].toInt() and 0xFF) xor (keyFragment[i].toInt() and 0xFF) xor (tokenBytes[i].toInt() and 0xFF)
            out[i] = (w.transform(v) xor ((i.toLong() * w.step + seedNumber) and 255).toInt()).toByte()
        }
        val derived = pbkdf2(out, seed.toByteArray(), 1000)
        for (i in 0 until 32) derived[i] = (derived[i].toInt() xor seed[i % seed.length].code).toByte()
        val aesKey = sha(derived)
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))
        val url = String(c.doFinal(videoBytes), Charsets.UTF_8).trim().trimEnd('\u0000')
        if (!url.startsWith("http")) null else Result(url, key, data.optJSONArray("subtitles"))
    } catch (_: Exception) {
        null
    }

    // ---- manifests
    private fun decodeManifest(body: ByteArray, key: String?): String? {
        val t = String(body, Charsets.UTF_8).trim()
        if (t.startsWith("#EXTM3U")) return t
        if (key.isNullOrEmpty()) return null
        return try {
            val k = b64(key)
            val p = Base64.decode(t, Base64.DEFAULT)
            val o = ByteArray(p.size) { (p[it].toInt() xor k[it % k.size].toInt()).toByte() }
            String(o, Charsets.UTF_8).trim().takeIf { it.startsWith("#EXTM3U") }
        } catch (_: Exception) {
            null
        }
    }

    private fun absolute(base: String, ref: String): String = try { URI(base).resolve(ref).toString() } catch (_: Exception) { ref }

    private fun rewrite(text: String, base: String, map: (String) -> String): String =
        text.lines().joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                line.startsWith("#") -> Regex("URI=\"([^\"]+)\"").replace(line) { "URI=\"" + map(absolute(base, it.groupValues[1])) + "\"" }
                else -> map(absolute(base, line.trim()))
            }
        }

    private val dir by lazy { File(System.getProperty("java.io.tmpdir") ?: ".", "flix").apply { mkdirs() } }

    /**
     * A playable address. A plain playlist is returned as it is; a protected one is decoded and
     * written to a local file (the master and each child playlist), whose segments stay on the web.
     */
    fun playable(url: String, key: String?, headers: Map<String, String>): String? {
        val raw = getBytes(url, headers) ?: return null
        val master = decodeManifest(raw, key) ?: return null
        if (String(raw, Charsets.UTF_8).trim().startsWith("#EXTM3U")) return url
        val stamp = System.nanoTime().toString()
        val master2 = if ("#EXT-X-STREAM-INF" in master) {
            var n = 0
            rewrite(master, url) { child ->
                val bytes = getBytes(child, headers)
                val dec = bytes?.let { decodeManifest(it, key) }
                if (dec == null) child else {
                    val f = File(dir, "$stamp-${n++}.m3u8")
                    f.writeText(rewrite(dec, child) { it })
                    f.toURI().toString()
                }
            }
        } else rewrite(master, url) { it }
        val f = File(dir, "$stamp.m3u8")
        f.writeText(master2)
        return f.toURI().toString()
    }
}


// ------------------------------------------------------------------------------------------ VOE

/** VOE embeds (voe.sx): the page carries one obfuscated JSON blob holding the HLS address and captions. */
internal object Voe {
    class Details(val hls: String, val tracks: JSONArray?, val origin: String)

    fun matches(url: String) = Regex("^https?://(?:www\\.)?[a-z0-9.-]*voe[a-z0-9.-]*/e/[A-Za-z0-9_-]+", RegexOption.IGNORE_CASE).containsMatchIn(url)

    private fun decode(raw: String): JSONObject? = try {
        val rot = raw.map { c ->
            when (c) {
                in 'a'..'z' -> ((c - 'a' + 13) % 26 + 'a'.code).toChar()
                in 'A'..'Z' -> ((c - 'A' + 13) % 26 + 'A'.code).toChar()
                else -> c
            }
        }.joinToString("")
        var step = rot
        for (t in listOf("@$", "^^", "~@", "%?", "*~", "!!", "#&")) step = step.replace(t, "_")
        step = step.replace("_", "")
        step = String(Base64.decode(step, Base64.DEFAULT), Charsets.ISO_8859_1)
        step = step.map { (it.code - 3).toChar() }.joinToString("").reversed()
        JSONObject(String(Base64.decode(step, Base64.DEFAULT), Charsets.UTF_8))
    } catch (_: Exception) {
        null
    }

    fun extract(embed: String, referer: String): Details? {
        var current = embed
        val headers = mapOf("User-Agent" to CHROME, "Accept" to "text/html,application/xhtml+xml,*/*", "Referer" to referer)
        repeat(6) {
            val body = avText(current, headers) ?: return null
            Regex("window\\.location\\.href\\s*=\\s*['\"]([^'\"]+)['\"]").find(body)?.let { current = URI(current).resolve(it.groupValues[1]).toString(); return@repeat }
            if (body.contains("Redirecting", true) && body.contains("permanentToken")) {
                Regex("https?://[^'\"]+/e/[^'\"]+").find(body)?.let { current = it.value; return@repeat }
            }
            val blob = Regex("<script[^>]*type\\s*=\\s*[\"']application/json[\"'][^>]*>\\s*(\\[[\\s\\S]*?\\])\\s*</script>", RegexOption.IGNORE_CASE).find(body)
                ?.groupValues?.get(1) ?: return null
            val first = try { JSONArray(blob).optString(0, "") } catch (_: Exception) { "" }
            val cfg = decode(first) ?: return null
            val hls = cfg.optString("source", "").takeIf { it.startsWith("http") } ?: return null
            val o = URI(current).let { "${it.scheme}://${it.host}" }
            return Details(hls, cfg.optJSONArray("captions"), o)
        }
        return null
    }
}

// ------------------------------------------------------------------------------------------- AniHQ

/** AniHQ (anihq.cc), ported from the Anivexa-API project's providers/anihq.js: a WordPress site that embeds VOE. */
class AniHQProvider : StreamProvider {
    override fun getName() = "AniHQ"

    private class Cand(val url: String, val slug: String, val baseSlug: String, val audio: String?, val titles: List<String>, val score: Double)
    private class Variant(val cand: Cand, val episodes: Map<Int, String>)

    companion object {
        private const val SITE = "https://anihq.cc"
        @Volatile private var nonce: String? = null
        @Volatile private var nonceAt = 0L
        private val cache = ConcurrentHashMap<Int, Map<String, Variant?>>()
    }

    private fun ajaxHeaders(ref: String = "$SITE/") =
        mapOf("User-Agent" to CHROME, "Accept" to "application/json, text/plain, */*", "Referer" to ref, "X-Requested-With" to "XMLHttpRequest")

    private fun nonce(force: Boolean = false): String? {
        if (!force) nonce?.let { if (System.currentTimeMillis() - nonceAt < 300_000) return it }
        val html = avText("$SITE/", mapOf("User-Agent" to CHROME, "Accept" to "text/html,*/*")) ?: return nonce
        val n = Regex("search_actions\\\\?[\"']\\s*:\\s*\\\\?[\"']([a-z0-9_-]+)", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
        if (n != null) { nonce = n; nonceAt = System.currentTimeMillis() }
        return n ?: nonce
    }

    private fun stripAudio(s: String) = s.replace(Regex("\\s+english\\s+(?:subbed|dubbed)\\s*$", RegexOption.IGNORE_CASE), "").replace(Regex("\\s+"), " ").trim()

    private fun parse(html: String): List<Cand> {
        val out = ArrayList<Cand>()
        for (m in Regex("<a\\b([^>]*)>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE).findAll(html)) {
            val href = Regex("href=[\"']([^\"']+)[\"']").find(m.groupValues[1])?.groupValues?.get(1) ?: continue
            val path = try { URI(avUnent(href)).let { u -> if (u.host != null && !u.host.endsWith("anihq.cc")) null else u.path } } catch (_: Exception) { null } ?: continue
            if (!path.startsWith("/anime-show/")) continue
            val slug = path.trimEnd('/').substringAfterLast('/')
            val audio = Regex("-english-(subbed|dubbed)$").find(slug)?.groupValues?.get(1)?.let { if (it == "dubbed") "dub" else "sub" }
            val baseSlug = slug.replace(Regex("-english-(?:subbed|dubbed)$"), "")
            val titles = LinkedHashSet<String>()
            Regex("title=[\"']([^\"']*)[\"']").find(m.groupValues[1])?.let { titles.add(stripAudio(avUnent(it.groupValues[1]))) }
            for (sp in Regex("<span\\b[^>]*class=[\"'][^\"']*group-data-\\[language=[^\"']*[\"'][^>]*>([\\s\\S]*?)</span>").findAll(m.groupValues[2])) {
                titles.add(stripAudio(avUnent(sp.groupValues[1].replace(Regex("<[^>]*>"), "").trim())))
            }
            titles.add(baseSlug.replace('-', ' '))
            val list = titles.filter { it.isNotBlank() }
            if (list.isEmpty()) continue
            out.add(Cand("$SITE$path", slug, baseSlug, audio, list, 0.0))
        }
        return out
    }

    private fun search(q: String, retry: Boolean = true): List<Cand> {
        val n = nonce() ?: return emptyList()
        val url = "$SITE/wp-admin/admin-ajax.php?action=instant_search&query=${avEnc(q)}&nonce=${avEnc(n)}"
        val json = getJson(url, ajaxHeaders())
        if (json == null) {
            if (retry) { nonce(true); return search(q, false) }
            return emptyList()
        }
        if (!json.optBoolean("success")) return emptyList()
        return parse(json.optJSONObject("data")?.optString("html", "").orEmpty())
    }

    private fun score(c: Cand, wanted: List<String>): Cand? {
        if (avMarkersConflict(wanted, c.titles)) return null
        var best = 0.0
        for (w in wanted.map { avNorm(it) }.filter { it.isNotEmpty() }) for (t in c.titles.map { avNorm(it) }.filter { it.isNotEmpty() }) {
            val sc = when {
                w == t -> 1.0
                w.startsWith(t) || t.startsWith(w) -> 0.8 * minOf(w.length, t.length) / maxOf(w.length, t.length) + 0.2
                w.contains(t) || t.contains(w) -> 0.6 * minOf(w.length, t.length) / maxOf(w.length, t.length) + 0.1
                else -> 0.0
            }
            if (sc > best) best = sc
        }
        return if (best >= 0.6) Cand(c.url, c.slug, c.baseSlug, c.audio, c.titles, best) else null
    }

    private fun inspect(c: Cand, audio: String): Variant? {
        val posts = try { JSONArray(avText("$SITE/wp-json/wp/v2/anime?slug=${avEnc(c.baseSlug)}", mapOf("User-Agent" to CHROME, "Accept" to "application/json")) ?: return null) } catch (_: Exception) { return null }
        var post: JSONObject? = null
        for (i in 0 until posts.length()) posts.optJSONObject(i)?.let { if (it.optString("slug") == c.baseSlug) post = it }
        val p = post ?: return null
        val classes = p.optJSONArray("class_list")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        if (!classes.contains(if (audio == "dub") "anime_attribute-dub" else "anime_attribute-sub")) return null
        val id = p.optString("id")
        fun page(n: Int) = getJson("$SITE/wp-admin/admin-ajax.php?action=get_episodes&anime_id=$id&page=$n&order=desc", ajaxHeaders(c.url))
            ?.takeIf { it.optBoolean("success") }?.optJSONObject("data")
        val first = page(1) ?: return null
        val eps = HashMap<Int, String>()
        fun add(d: JSONObject) {
            val rows = d.optJSONArray("episodes") ?: return
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val num = r.optString("meta_number").toDoubleOrNull()?.toInt()
                    ?: Regex("\\d+").find(r.optString("number"))?.value?.toIntOrNull() ?: continue
                val u = r.optString("url").let { avUnent(it) }
                val path = try { URI(u).path } catch (_: Exception) { null } ?: continue
                if (path.startsWith("/watch/")) eps[num] = "$SITE$path"
            }
        }
        add(first)
        val pages = first.optInt("max_episodes_page", 1).coerceIn(1, 60)
        if (pages > 1) {
            val jobs = (2..pages).map { n -> Callable { page(n) } }
            for (f in POOL.invokeAll(jobs, 15, java.util.concurrent.TimeUnit.SECONDS)) runCatching { f.get() }.getOrNull()?.let { add(it) }
        }
        return if (eps.isEmpty()) null else Variant(c, eps)
    }

    private fun variants(req: StreamRequest): Map<String, Variant?> {
        cache[req.animeId]?.let { return it }
        val wanted = avTitles(req)
        if (wanted.isEmpty()) return emptyMap()
        val found = LinkedHashMap<String, Cand>()
        val jobs = wanted.take(4).map { t -> Callable { search(t) } }
        for (f in POOL.invokeAll(jobs, 15, java.util.concurrent.TimeUnit.SECONDS)) {
            for (c in runCatching { f.get() }.getOrNull().orEmpty()) score(c, wanted)?.let { if (c.slug !in found) found[c.slug] = it }
        }
        val out = HashMap<String, Variant?>()
        for (audio in AUDIO) {
            val ranked = found.values.filter { it.audio == null || it.audio == audio }.sortedByDescending { it.score }.take(4)
            val inspected = POOL.invokeAll(ranked.map { c -> Callable { inspect(c, audio) } }, 20, java.util.concurrent.TimeUnit.SECONDS)
                .mapNotNull { runCatching { it.get() }.getOrNull() }
            out[audio] = inspected.maxWithOrNull(compareBy<Variant> { it.cand.score }.thenBy { it.episodes.size })
        }
        if (out.values.any { it != null }) cache[req.animeId] = out
        return out
    }

    override fun resolve(request: StreamRequest): List<StreamSource> {
        val v = variants(request)
        val jobs = ArrayList<Callable<List<StreamSource>>>()
        for (audio in AUDIO) {
            val watch = v[audio]?.episodes?.get(request.episode) ?: continue
            jobs.add(Callable { forEpisode(watch, v[audio]!!.cand.url, audio) })
        }
        return gather(jobs, 26)
    }

    private fun forEpisode(watch: String, ref: String, audio: String): List<StreamSource> {
        val html = avText(watch, mapOf("User-Agent" to CHROME, "Accept" to "text/html,*/*", "Referer" to ref)) ?: return emptyList()
        val embeds = ArrayList<String>()
        for (m in Regex("<iframe\\b([^>]*)>", RegexOption.IGNORE_CASE).findAll(html)) {
            for (a in listOf("src", "data-src", "data-lazy-src", "data-aniturbo-src")) {
                val raw = Regex("$a=[\"']([^\"']+)[\"']").find(m.groupValues[1])?.groupValues?.get(1) ?: continue
                val u = try { URI(watch).resolve(avUnent(raw)).toString() } catch (_: Exception) { continue }
                if (u.startsWith("http") && u !in embeds) embeds.add(u)
            }
        }
        val out = ArrayList<StreamSource>()
        for (e in embeds.sortedByDescending { Voe.matches(it) }.take(2)) {
            if (Voe.matches(e)) {
                val d = Voe.extract(e, watch) ?: continue
                source(getName(), "VOE", d.hls, streamHeaders(d.origin + "/"), audio, subtitlesOf(d.tracks))?.let { out.add(it) }
            } else {
                val d = try { MegaPlay.extract(e) } catch (_: Exception) { null } ?: continue
                for ((url, _) in d.sources) source(getName(), "Embed", url, streamHeaders(d.origin + "/"), audio, subtitlesOf(d.tracks))?.let { out.add(it) }
            }
            if (out.isNotEmpty()) break
        }
        return out
    }
}
