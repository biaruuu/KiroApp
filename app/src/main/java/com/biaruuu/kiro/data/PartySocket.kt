package com.biaruuu.kiro.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.biaruuu.kiro.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Watch party over a WebSocket relay (see server/party). The host's state reaches every guest the
 * moment it changes, and a dropped connection is re-opened automatically with backoff. Used when the
 * build has a PARTY_WS_URL; otherwise [WatchParty] keeps using Firestore.
 */
object PartySocket {
    val enabled: Boolean get() = BuildConfig.PARTY_WS_URL.isNotBlank()

    private val main = Handler(Looper.getMainLooper())
    private val client: OkHttpClient by lazy {
        HttpClientProvider.get().newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(20, TimeUnit.SECONDS).build()
    }

    private var ws: WebSocket? = null
    private var gen = 0                 // bumps on every (re)connect so stale callbacks are ignored
    private var retry = 0
    private var joinMsg: JSONObject? = null
    private var lastState: JSONObject? = null
    private var onJoined: ((String?) -> Unit)? = null
    private var active = false

    private fun request() = Request.Builder().url(BuildConfig.PARTY_WS_URL).build()

    private fun memberJson(): JSONObject {
        val p = ProfileRepository.profile.value
        return JSONObject().put("name", WatchParty.myName()).put("avatar", p.avatar)
            .put("decoration", p.decoration).put("nameFont", p.nameFont)
    }

    /** Asks the relay about a party without joining it. */
    fun lookup(code: String, onDone: (WatchParty.Remote?, String?) -> Unit) {
        var finished = false
        fun finish(r: WatchParty.Remote?, e: String?) {
            if (finished) return
            finished = true
            main.post { onDone(r, e) }
        }
        val sock = client.newWebSocket(request(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(JSONObject().put("t", "lookup").put("code", code.trim().uppercase()).toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val o = try { JSONObject(text) } catch (_: Exception) { return }
                if (o.optString("t") == "info") finish(toRemote(o), null)
                else if (o.optString("t") == "error") finish(null, o.optString("msg"))
                webSocket.close(1000, null)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                finish(null, "Couldn't reach the watch party server. Check your connection.")
        })
        main.postDelayed({ if (!finished) { finish(null, "Couldn't reach the watch party server. Check your connection."); sock.cancel() } }, 8000)
    }

    private fun toRemote(o: JSONObject) = WatchParty.Remote(
        o.optInt("animeId"), o.optInt("episode"), o.optString("title"), o.optString("hostName", "Host"),
        o.optLong("positionMs"), o.optBoolean("playing"), SystemClock.elapsedRealtime(), o.optLong("sentAt"),
    )

    fun host(uid: String, code: String, animeId: Int, episode: Int, title: String, done: (String?) -> Unit) {
        close()
        active = true
        onJoined = done
        lastState = JSONObject().put("t", "state").put("animeId", animeId).put("episode", episode)
            .put("positionMs", 0L).put("playing", false)
        joinMsg = JSONObject().put("t", "host").put("code", code).put("uid", uid).put("hostName", WatchParty.myName())
            .put("title", title).put("animeId", animeId).put("episode", episode).put("member", memberJson())
        connect()
        main.postDelayed({ onJoined?.let { cb -> onJoined = null; cb("Couldn't reach the watch party server. Check your connection.") } }, 8000)
    }

    fun join(uid: String, code: String) {
        close()
        active = true
        joinMsg = JSONObject().put("t", "join").put("code", code).put("uid", uid).put("member", memberJson())
        connect()
    }

    private fun connect() {
        val my = ++gen
        ws = client.newWebSocket(request(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (my != gen) return
                retry = 0
                webSocket.send(joinMsg.toString())
                // After a reconnect the host re-sends where it is so guests don't wait for the next tick.
                if (joinMsg?.optString("t") == "host") lastState?.let { webSocket.send(it.toString()) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (my != gen) return
                val o = try { JSONObject(text) } catch (_: Exception) { return }
                when (o.optString("t")) {
                    "joined" -> main.post { onJoined?.let { cb -> onJoined = null; cb(null) } }
                    "state" -> WatchParty.remote.value = toRemote(o).let { r ->
                        // The relay doesn't repeat the title and host name on every tick.
                        val old = WatchParty.remote.value
                        if (r.title.isEmpty() && old != null) WatchParty.Remote(r.animeId, r.episode, old.title, old.hostName, r.positionMs, r.playing, r.receivedAt, r.sentAt) else r
                    }
                    "members" -> {
                        val a = o.optJSONArray("list")
                        WatchParty.members.value = (0 until (a?.length() ?: 0)).mapNotNull { i ->
                            val m = a!!.optJSONObject(i) ?: return@mapNotNull null
                            WatchParty.Member(
                                m.optString("uid"), m.optString("name", "Guest"), m.optString("avatar"),
                                m.optString("decoration"), m.optString("nameFont"), m.optBoolean("host"),
                            )
                        }.sortedWith(compareByDescending<WatchParty.Member> { it.host }.thenBy { it.name.lowercase() })
                    }
                    "ended" -> main.post {
                        WatchParty.message.value = "The host ended the watch party"
                        WatchParty.leave(local = true)
                    }
                    "error" -> main.post {
                        val cb = onJoined
                        if (cb != null) { onJoined = null; cb(o.optString("msg")) }
                        else { WatchParty.message.value = o.optString("msg"); WatchParty.leave(local = true) }
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = dropped(my)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = dropped(my)
        })
    }

    private fun dropped(my: Int) {
        if (my != gen || !active) return
        val wait = minOf(10_000L, 1000L shl minOf(retry, 4))
        retry++
        main.postDelayed({ if (my == gen && active) connect() }, wait)
    }

    /** The host's latest position; sent at once, and kept for re-sending after a reconnect. */
    fun push(animeId: Int, episode: Int, positionMs: Long, playing: Boolean) {
        val o = JSONObject().put("t", "state").put("animeId", animeId).put("episode", episode)
            .put("positionMs", positionMs).put("playing", playing)
        lastState = o
        ws?.send(o.toString())
    }

    fun end() { ws?.send("{\"t\":\"end\"}") }

    fun close() {
        active = false
        gen++
        ws?.close(1000, null)
        ws = null
        joinMsg = null
        onJoined = null
    }
}
