package com.biaruuu.kiro.data.stream

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap

/*
 * Pahenime (pahenime.com), ported from the Anivexa-API project's providers/pahenime.js.
 * The show is matched by its exact AniList id, so the episode is always the right one.
 * Its streams are "image-wrapped" HLS (extra bytes before the playlist and each segment),
 * so they are played through a small loopback proxy that strips those bytes.
 */
class PahenimeProvider : StreamProvider {
    override fun getName() = "Pahenime"

    private class Ep(val id: String, val number: Int, val dual: Boolean)
    private class Series(val uuid: String, val eps: Map<Int, Ep>)

    companion object {
        private const val SITE = "https://pahenime.com"
        private val series = ConcurrentHashMap<Int, Series>()
    }

    private fun h(ref: String = "$SITE/") = mapOf("User-Agent" to CHROME, "Accept" to "application/json", "Referer" to ref)

    private fun coverId(item: JSONObject): Int {
        for (k in listOf("anilist_id", "anilistId")) item.optInt(k, 0).takeIf { it > 0 }?.let { return it }
        return Regex("(?:^|/)bx(\\d+)(?:[-/.]|$)", RegexOption.IGNORE_CASE).find(item.optString("cover_image", ""))?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun fetchSeries(uuid: String, anilist: Int): Series? {
        val data = getJson("$SITE/api/anime/${URLEncoder.encode(uuid, "UTF-8")}", h()) ?: return null
        val s = data.optJSONObject("series") ?: return null
        if (s.optInt("anilist_id", -1) != anilist) return null // exact AniList match only
        val rows = data.optJSONArray("episodes") ?: return null
        val eps = HashMap<Int, Ep>()
        for (i in 0 until rows.length()) {
            val r = rows.optJSONObject(i) ?: continue
            val num = Regex("^(\\d+)(?:\\.0+)?(?:\\s|$)").find(r.opt("episode_number")?.toString()?.trim().orEmpty().ifEmpty { r.opt("number")?.toString().orEmpty().trim() })
                ?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val id = r.optString("id").trim()
            if (id.isEmpty()) continue
            val dual = r.optBoolean("is_dual_audio", false)
            val old = eps[num]
            if (old == null || (dual && !old.dual)) eps[num] = Ep(id, num, dual)
        }
        return if (eps.isEmpty()) null else Series(s.optString("uuid", uuid).ifEmpty { uuid }, eps)
    }

    private fun find(req: StreamRequest): Series? {
        series[req.animeId]?.let { return it }
        val queries = avTitlesP(req)
        val seen = LinkedHashSet<String>()
        val loose = ArrayList<Pair<String, Int>>()
        val jobs = queries.take(5).map { q ->
            Callable { getJson("$SITE/api/search?q=${URLEncoder.encode(q, "UTF-8")}&page=1", h())?.optJSONArray("data") }
        }
        for (f in POOL.invokeAll(jobs, 15, java.util.concurrent.TimeUnit.SECONDS)) {
            val arr = runCatching { f.get() }.getOrNull() ?: continue
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isEmpty()) continue
                if (!seen.add(id)) continue
                if (coverId(item) == req.animeId) {
                    fetchSeries(id, req.animeId)?.let { s -> series[req.animeId] = s; return s }
                }
                loose.add(id to coverId(item))
            }
        }
        // The catalogue does not always expose the AniList id in search; verify the near matches directly.
        for ((id, cid) in loose.take(6)) {
            if (cid != 0 && cid != req.animeId) continue
            fetchSeries(id, req.animeId)?.let { s -> series[req.animeId] = s; return s }
        }
        return null
    }

    override fun resolve(request: StreamRequest): List<StreamSource> {
        val s = find(request) ?: return emptyList()
        val ep = s.eps[request.episode] ?: return emptyList()
        val jobs = ArrayList<Callable<List<StreamSource>>>()
        for (audio in AUDIO) {
            if (audio == "dub" && !ep.dual) continue
            jobs.add(Callable { listOfNotNull(play(ep, audio)) })
        }
        return gather(jobs, 26)
    }

    private fun play(ep: Ep, audio: String): StreamSource? {
        val player = "$SITE/player?id=${URLEncoder.encode(ep.id, "UTF-8")}"
        val meta = getJson("$SITE/stream/metadata/${URLEncoder.encode(ep.id, "UTF-8")}", h(player)) ?: return null
        val tracks = meta.optJSONArray("audioTracks") ?: JSONArray()
        val want = if (audio == "dub") Regex("^(en|eng|english)$", RegexOption.IGNORE_CASE) else Regex("^(ja|jpn|jp|japanese)$", RegexOption.IGNORE_CASE)
        var track: JSONObject? = null
        for (i in 0 until tracks.length()) {
            val t = tracks.optJSONObject(i) ?: continue
            if (want.matches(t.optString("lang", t.optString("language", "")))) { track = t; break }
        }
        if (track == null && audio == "sub" && tracks.length() > 0) track = tracks.optJSONObject(0)
        val tr = track ?: return null
        val res = meta.optJSONArray("resolutions") ?: return null
        var quality = ""
        var best = -1
        for (i in 0 until res.length()) {
            val q = res.optJSONObject(i)?.optString("quality").orEmpty()
            val n = Regex("\\d+").find(q)?.value?.toIntOrNull() ?: 0
            if (n > best) { best = n; quality = q }
        }
        if (quality.isEmpty()) return null
        val servers = meta.optJSONArray("servers") ?: return null
        for (i in 0 until servers.length()) {
            val sv = servers.optJSONObject(i) ?: continue
            val surl = sv.optString("url")
            if (surl.isEmpty()) continue
            val body = JSONObject().put("quality", quality).put("audioIndex", tr.optInt("index", 0)).put("serverUrl", surl).toString()
            val start = try {
                val rq = request("$SITE/stream/start/${URLEncoder.encode(ep.id, "UTF-8")}", h(player) + mapOf("X-Requested-With" to "XMLHttpRequest"))
                    .newBuilder().post(body.toRequestBody("application/json".toMediaType())).build()
                client(14).newCall(rq).execute().use { r -> if (r.isSuccessful) JSONObject(r.body?.string().orEmpty()) else null }
            } catch (_: Exception) { null } ?: continue
            val stream = start.optString("streamUrl").takeIf { it.startsWith("http") } ?: continue
            val hdr = mapOf("User-Agent" to CHROME, "Accept" to "*/*", "Referer" to player)
            val head = getRange(stream, hdr, 0, 65535) ?: continue
            val mo = indexOf(head, "#EXTM3U".toByteArray())
            if (mo < 0) continue
            val manifest = String(head, mo, head.size - mo, Charsets.UTF_8)
            val first = manifest.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            var so = 0
            if (first != null) {
                val seg = try { URI(stream).resolve(first).toString() } catch (_: Exception) { null }
                val bytes = seg?.let { getRange(it, hdr, 0, 255) }
                if (bytes != null) {
                    val txt = String(bytes, Charsets.ISO_8859_1)
                    val box = Regex("(?:ftyp|styp|moof|moov|sidx)").find(txt)
                    if (box != null && box.range.first >= 4) {
                        val off = box.range.first - 4
                        val size = ((bytes[off].toInt() and 255) shl 24) or ((bytes[off + 1].toInt() and 255) shl 16) or ((bytes[off + 2].toInt() and 255) shl 8) or (bytes[off + 3].toInt() and 255)
                        if (size >= 8) so = off
                    }
                }
            }
            val local = WrappedHls.url(stream, mo, so, hdr) ?: continue
            val c = check(local, emptyMap()) ?: continue
            return StreamSource("$name · ${sv.optString("name", "Pahenime")}", local, if (c.hls) c.height.takeIf { it > 0 } ?: best else best, c.hls, emptyList(), audio)
                .withLatency(c.ms)
        }
        return null
    }

    private fun getRange(url: String, headers: Map<String, String>, from: Int, to: Int): ByteArray? = try {
        client(12).newCall(request(url, headers + ("Range" to "bytes=$from-$to"))).execute().use { r ->
            if (r.isSuccessful) r.body?.source()?.let { src -> src.request(to - from + 1L); src.buffer.readByteArray(minOf(src.buffer.size, to - from + 1L)) } else null
        }
    } catch (_: Exception) { null }

    private fun indexOf(data: ByteArray, pat: ByteArray): Int {
        outer@ for (i in 0..data.size - pat.size) {
            for (j in pat.indices) if (data[i + j] != pat[j]) continue@outer
            return i
        }
        return -1
    }
}

private fun avTitlesP(r: StreamRequest) = listOfNotNull(r.titleEnglish, r.titleRomaji, r.title).filter { it.isNotBlank() }.distinct()

/** Loopback proxy that removes the extra bytes in front of image-wrapped playlists and segments. */
internal object WrappedHls {
    @Volatile private var server: ServerSocket? = null
    private val POOL_T = java.util.concurrent.Executors.newCachedThreadPool()

    private fun start(): ServerSocket? {
        server?.let { if (!it.isClosed) return it }
        synchronized(this) {
            server?.let { if (!it.isClosed) return it }
            val s = try { ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")) } catch (_: Exception) { return null }
            server = s
            POOL_T.execute {
                while (!s.isClosed) {
                    val c = try { s.accept() } catch (_: Exception) { break }
                    POOL_T.execute { handle(c) }
                }
            }
            return s
        }
    }

    private fun enc(s: String) = Base64.encodeToString(s.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    private fun dec(s: String) = String(Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))

    private fun link(port: Int, kind: String, target: String, mo: Int, so: Int, hdr: Map<String, String>): String {
        val h = enc(JSONObject(hdr as Map<*, *>).toString())
        return "http://127.0.0.1:$port/$kind?u=${enc(target)}&mo=$mo&so=$so&h=$h"
    }

    fun url(target: String, mo: Int, so: Int, hdr: Map<String, String>): String? {
        val s = start() ?: return null
        return link(s.localPort, "m", target, mo, so, hdr)
    }

    private fun handle(c: Socket) {
        try {
            c.soTimeout = 30_000
            val line = c.getInputStream().bufferedReader().readLine() ?: return
            val path = line.split(" ").getOrNull(1) ?: return
            val kind = path.substring(1, path.indexOf('?'))
            val q = path.substringAfter('?').split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
            val target = dec(q["u"] ?: return)
            val mo = q["mo"]?.toIntOrNull() ?: 0
            val so = q["so"]?.toIntOrNull() ?: 0
            val hjson = JSONObject(dec(q["h"] ?: return))
            val hdr = hjson.keys().asSequence().associateWith { hjson.getString(it) }
            val out = BufferedOutputStream(c.getOutputStream())
            client(30).newCall(request(target, hdr)).execute().use { r ->
                if (!r.isSuccessful) {
                    out.write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); out.flush(); return
                }
                val body = r.body ?: return
                if (kind == "m") {
                    val raw = body.bytes()
                    val text = String(raw, mo.coerceAtMost(raw.size), (raw.size - mo).coerceAtLeast(0), Charsets.UTF_8)
                    val port = (server?.localPort) ?: return
                    val rewritten = text.lines().joinToString("\n") { l ->
                        val t = l.trim()
                        when {
                            t.isEmpty() -> l
                            t.startsWith("#") -> Regex("URI=\"([^\"]+)\"").replace(l) { m ->
                                "URI=\"" + link(port, "m", URI(target).resolve(m.groupValues[1]).toString(), mo, so, hdr) + "\""
                            }
                            else -> {
                                val abs = URI(target).resolve(t).toString()
                                // Playlists nest as "m", media files as "s".
                                link(port, if (abs.substringBefore('?').endsWith(".m3u8")) "m" else "s", abs, mo, so, hdr)
                            }
                        }
                    }.toByteArray()
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.apple.mpegurl\r\nContent-Length: ${rewritten.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    out.write(rewritten)
                } else {
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nConnection: close\r\n\r\n".toByteArray())
                    val src = body.source()
                    var skip = so.toLong()
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = src.read(buf)
                        if (n < 0) break
                        var off = 0
                        if (skip > 0) { val d = minOf(skip, n.toLong()).toInt(); skip -= d; off = d }
                        if (n > off) out.write(buf, off, n - off)
                    }
                }
                out.flush()
            }
        } catch (_: Exception) {
        } finally {
            try { c.close() } catch (_: Exception) {}
        }
    }
}
