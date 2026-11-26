package com.biaruuu.kiro.data

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * The live channel every open phone keeps to the Kiro server (the same server as the watch party).
 * The developer's announcements arrive on it the moment they are sent, and the likes and dislikes
 * people tap come back on it, so the totals move on every screen in real time.
 *
 * It is connected only while the app is on screen, and reconnects by itself (1 s, 2 s ... up to 30 s).
 */
object GlobalSocket {
    data class Live(
        val id: String,
        val text: String,
        val username: String,
        val durationMs: Long,
        /** How much of the prompt was left when it arrived (a late joiner sees only the rest). */
        val remainingMs: Long,
        val likes: Int,
        val dislikes: Int,
    )

    private const val URL = "wss://kiro-watchparty.onrender.com/ws"
    private const val PREFS = "kiro_global"

    /** The announcement on screen right now, or null. */
    val current = MutableStateFlow<Live?>(null)

    /** This phone's own pick for [current]: "like", "dislike" or null. */
    val myChoice = MutableStateFlow<String?>(null)

    /** Phones connected right now. */
    val online = MutableStateFlow(0)

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val client: OkHttpClient by lazy {
        HttpClientProvider.get().newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS) // the free server may be asleep
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build()
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private var socket: WebSocket? = null
    private var wanted = false
    private var generation = 0
    private var retry = 0
    private var reconnect: Job? = null
    private var voter = ""
    private var pending: ((String?) -> Unit)? = null

    @Synchronized
    private fun voterId(context: Context): String {
        if (voter.isEmpty()) {
            val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            voter = p.getString("voter", null) ?: UUID.randomUUID().toString().also { p.edit().putString("voter", it).apply() }
        }
        return voter
    }

    fun start(context: Context) {
        voterId(context)
        synchronized(lock) {
            wanted = true
            if (socket != null) return
        }
        connect()
    }

    fun stop() {
        synchronized(lock) {
            wanted = false
            generation++
            reconnect?.cancel()
            socket?.close(1000, "bye")
            socket = null
        }
        _connected.value = false
    }

    private fun connect() {
        val gen: Int
        synchronized(lock) {
            if (!wanted) return
            gen = ++generation
        }
        val ws = client.newWebSocket(Request.Builder().url(URL).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (gen != generation) return
                retry = 0
                _connected.value = true
                webSocket.send(JSONObject().put("type", "global_join").toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (gen != generation) return
                runCatching { handle(JSONObject(text)) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = lost(gen)
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = lost(gen)
        })
        synchronized(lock) { socket = ws }
    }

    private fun lost(gen: Int) {
        if (gen != generation) return
        _connected.value = false
        synchronized(lock) { socket = null }
        val wait = minOf(30_000L, 1_000L shl minOf(retry, 5))
        retry++
        reconnect?.cancel()
        reconnect = scope.launch {
            delay(wait)
            connect()
        }
    }

    private fun handle(m: JSONObject) {
        when (m.optString("type")) {
            "global_ready" -> online.value = m.optInt("online")
            "announcement" -> {
                online.value = m.optInt("online", online.value)
                val remaining = m.optLong("remainingMs")
                if (remaining <= 0) return
                myChoice.value = null
                current.value = Live(
                    m.optString("id"), m.optString("text"), m.optString("username"),
                    m.optLong("durationMs", 12_000), remaining, m.optInt("likes"), m.optInt("dislikes"),
                )
            }
            "announcement_counts" -> {
                online.value = m.optInt("online", online.value)
                val c = current.value ?: return
                if (c.id == m.optString("id")) current.value = c.copy(likes = m.optInt("likes"), dislikes = m.optInt("dislikes"))
            }
            "announcement_end" -> if (current.value?.id == m.optString("id")) current.value = null
            "announce_ok" -> pending?.invoke(null).also { pending = null }
            "error" -> pending?.invoke(m.optString("message", "failed")).also { pending = null }
        }
    }

    /** Tap on like or dislike; tapping the same one again takes it back. Shown at once, confirmed by the server. */
    fun react(choice: String) {
        val c = current.value ?: return
        val before = myChoice.value
        val now = if (before == choice) null else choice
        myChoice.value = now
        var likes = c.likes
        var dislikes = c.dislikes
        if (before == "like") likes--
        if (before == "dislike") dislikes--
        if (now == "like") likes++
        if (now == "dislike") dislikes++
        current.value = c.copy(likes = maxOf(0, likes), dislikes = maxOf(0, dislikes))
        socket?.send(
            JSONObject().put("type", "react").put("id", c.id).put("choice", now ?: "none").put("voter", voter).toString(),
        )
    }

    /** Developer only: the server checks [token] against its ADMIN_TOKEN. [done] gets null on success or the reason. */
    fun announce(token: String, text: String, username: String, durationMs: Long, done: (String?) -> Unit) {
        val ws = socket
        if (ws == null || !_connected.value) {
            done("Not connected to the server yet. Wait a moment and try again.")
            return
        }
        pending = done
        ws.send(
            JSONObject().put("type", "announce").put("token", token).put("text", text)
                .put("username", username).put("durationMs", durationMs).toString(),
        )
        val sentAt = SystemClock.elapsedRealtime()
        scope.launch {
            delay(8_000)
            if (pending === done && SystemClock.elapsedRealtime() - sentAt >= 8_000) {
                pending = null
                done("No answer from the server.")
            }
        }
    }

    fun hide(token: String) {
        socket?.send(JSONObject().put("type", "announce_clear").put("token", token).toString())
    }
}
