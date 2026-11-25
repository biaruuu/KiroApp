package com.biaruuu.kiro.data

import android.os.SystemClock
import android.util.Log
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
import java.util.concurrent.TimeUnit

/**
 * Realtime channel for the watch party (the kiro-watchparty server on Render).
 *
 * The host's play, pause, seek and episode changes go through this socket and reach every guest
 * within a fraction of a second. Firestore stays in charge of the member list, chat, bans and the
 * room settings, and acts as the backup while this socket is not connected (for example while the
 * free Render server is waking up).
 *
 * The socket room code is different from the party code, so the host publishes it in the party
 * document as `wsRoom` and guests read it from there.
 */
object PartySocket {
    private const val TAG = "PartySocket"
    private const val SERVER = "kiro-watchparty.onrender.com"
    private const val WS_URL = "wss://$SERVER/ws"

    /** The connection address, signed with the current time so the server can tell it comes from this app. */
    private fun signedUrl(): String {
        val secret = com.biaruuu.kiro.BuildConfig.KIRO_WS_SECRET
        if (secret.isEmpty()) return WS_URL
        return try {
            val ts = System.currentTimeMillis().toString()
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(javax.crypto.spec.SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
            val sig = mac.doFinal(ts.toByteArray()).joinToString("") { "%02x".format(it) }
            "$WS_URL?ts=$ts&sig=$sig"
        } catch (_: Exception) {
            WS_URL
        }
    }
    private const val HEALTH_URL = "https://$SERVER/health"

    private val client: OkHttpClient by lazy {
        HttpClientProvider.get().newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS) // a sleeping free server can take a while to wake up
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build()
    }

    private val _open = MutableStateFlow(false)

    /** True while this phone is connected to the room on the server. */
    val open: StateFlow<Boolean> = _open

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private var socket: WebSocket? = null
    private var generation = 0
    private var wanted = false
    private var isHost = false
    private var userId = ""
    private var userName = ""
    private var wsRoomId: String? = null
    private var onHostRoom: ((String) -> Unit)? = null
    private var pendingCreate = false
    private var retry = 0
    private var reconnectJob: Job? = null
    private var pingJob: Job? = null

    /** Server clock minus this phone's clock, in ms. */
    @Volatile private var clockOffset = 0L

    private class Snap(val animeId: Int, val episode: Int, val title: String, val positionMs: Long, val playing: Boolean)

    /** The host's latest state, kept even while the socket is down so it can be re-sent. */
    @Volatile private var latest: Snap? = null
    @Volatile private var latestSettings: WatchParty.Settings? = null
    @Volatile private var latestBrowse: JSONObject? = null
    /** When we last heard from the server; used to tell "the room timed out" from "the server restarted". */
    @Volatile private var lastMsgAt = 0L
    private const val IDLE_END_MS = 10 * 60_000L
    @Volatile private var sentAnime = 0
    @Volatile private var sentEpisode = 0

    /** Wakes the free server up ahead of time (a cold start takes a while). */
    fun warmUp() {
        scope.launch {
            try {
                client.newCall(Request.Builder().url(HEALTH_URL).build()).execute().use { }
            } catch (_: Exception) {
            }
        }
    }

    /** Host: opens a room on the server; [onRoom] gets its code so it can be shared with guests. */
    fun startHost(uid: String, name: String, animeId: Int, episode: Int, title: String, onRoom: (String) -> Unit) {
        synchronized(lock) {
            resetLocked()
            wanted = true
            isHost = true
            userId = uid
            userName = name
            onHostRoom = onRoom
            sentAnime = 0
            sentEpisode = 0
            latest = if (animeId > 0) Snap(animeId, episode, title, 0L, false) else null
        }
        connect()
    }

    /** Guest: follows the server room the host published. Safe to call again with the same code. */
    fun startGuest(roomId: String, uid: String, name: String) {
        synchronized(lock) {
            if (wanted && !isHost && wsRoomId == roomId) return
            resetLocked()
            wanted = true
            isHost = false
            userId = uid
            userName = name
            wsRoomId = roomId
        }
        connect()
    }

    fun stop() {
        synchronized(lock) {
            resetLocked()
            latest = null
            latestSettings = null
            latestBrowse = null
            lastMsgAt = 0L
        }
    }

    /** Host: tells guests what screen the host is on (null = nothing special). */
    fun sendBrowse(b: JSONObject?) {
        latestBrowse = b
        if (isHost && _open.value) send(JSONObject().put("type", "browse").put("browse", b ?: JSONObject()))
    }

    /** Ask the server to drop someone's connection (the host removed them). */
    fun kick(uid: String) {
        if (isHost) send(JSONObject().put("type", "kick").put("userId", uid))
    }

    /**
     * Host: shares where the player is. Returns true when it went out over the socket, false when
     * the socket is not connected (the caller then relies on Firestore).
     */
    fun push(animeId: Int, episode: Int, positionMs: Long, playing: Boolean, title: String): Boolean {
        if (animeId > 0) {
            latest = Snap(animeId, episode, title.ifBlank { latest?.title.orEmpty() }, positionMs, playing)
        }
        if (!isLive()) return false
        if (animeId <= 0) return true
        return if (animeId != sentAnime || episode != sentEpisode) {
            sendVideo(animeId, episode, latest?.title.orEmpty(), positionMs, playing)
        } else {
            send(JSONObject().put("type", "host_heartbeat").put("time", positionMs / 1000.0).put("playing", playing))
        }
    }

    /** Host: picks the episode everyone watches (starts paused at 0:00). */
    fun select(animeId: Int, episode: Int): Boolean {
        latest = Snap(animeId, episode, "", 0L, false)
        if (!isLive()) return false
        return sendVideo(animeId, episode, "", 0L, false)
    }

    /** Host: shares which source, audio, subtitle and speed it uses, and whether it is fullscreen. */
    fun sendSettings(st: WatchParty.Settings) {
        latestSettings = st
        if (isLive()) sendSettingsNow(st)
    }

    private fun sendSettingsNow(st: WatchParty.Settings): Boolean = send(
        JSONObject().put("type", "host_settings").put(
            "settings",
            JSONObject().put("provider", st.provider).put("audio", st.audio).put("sub", st.sub)
                .put("fullscreen", st.fullscreen).put("speed", st.speed.toDouble())
                .put("subFont", st.subFont).put("subSize", st.subSize).put("subBg", st.subBg).put("subEdge", st.subEdge).put("src", st.src),
        ),
    )

    /** Sends any message to the room (used by the voice call). False when the socket is not connected. */
    fun sendRaw(o: JSONObject): Boolean = send(o)

    // ------------------------------------------------------------------ internals

    private fun isLive(): Boolean = synchronized(lock) { wanted && isHost && _open.value }

    private fun resetLocked() {
        wanted = false
        generation++
        reconnectJob?.cancel()
        pingJob?.cancel()
        socket?.let {
            try {
                it.send("{\"type\":\"leave_room\"}")
                it.close(1000, "bye")
            } catch (_: Exception) {
            }
        }
        socket = null
        _open.value = false
        pendingCreate = false
        retry = 0
        onHostRoom = null
        wsRoomId = null
    }

    private fun connect() {
        var gen = 0
        synchronized(lock) {
            if (!wanted) return
            socket?.cancel()
            gen = ++generation
            socket = client.newWebSocket(Request.Builder().url(signedUrl()).build(), Listener(gen))
        }
    }

    private fun current(gen: Int): Boolean = synchronized(lock) { generation == gen && wanted }

    private fun dropped(gen: Int) {
        synchronized(lock) {
            if (generation != gen) return
            socket = null
            pingJob?.cancel()
        }
        _open.value = false
        scheduleReconnect()
    }

    /** The phone got a network again: reconnect right away instead of waiting out the back-off. */
    fun networkBack() {
        synchronized(lock) {
            if (!wanted || _open.value) return
            reconnectJob?.cancel()
            retry = 0
        }
        connect()
    }

    private fun scheduleReconnect() {
        synchronized(lock) {
            if (!wanted) return
            reconnectJob?.cancel()
            val wait = minOf(8_000L, 250L shl minOf(retry, 5))
            retry++
            reconnectJob = scope.launch {
                delay(wait)
                connect()
            }
        }
    }

    private fun send(o: JSONObject): Boolean = synchronized(lock) { socket?.send(o.toString()) == true }

    private fun sendJoin(room: String) {
        send(JSONObject().put("type", "join_room").put("roomId", room).put("name", userName).put("userId", userId))
    }

    private fun sendCreate() {
        send(JSONObject().put("type", "create_room").put("name", userName).put("userId", userId))
    }

    private fun sendVideo(animeId: Int, episode: Int, title: String, positionMs: Long, playing: Boolean): Boolean {
        val meta = JSONObject().put("animeId", animeId).put("episode", episode)
        val video = JSONObject().put("url", "").put("title", title).put("episode", episode.toString()).put("meta", meta)
        val ok = send(
            JSONObject().put("type", "change_video").put("video", video)
                .put("time", positionMs / 1000.0).put("autoplay", playing),
        )
        if (ok) {
            sentAnime = animeId
            sentEpisode = episode
        }
        return ok
    }

    private fun flushHostState() {
        latest?.let { sendVideo(it.animeId, it.episode, it.title, it.positionMs, it.playing) }
        latestSettings?.let { sendSettingsNow(it) }
        latestBrowse?.let { send(JSONObject().put("type", "browse").put("browse", it)) }
    }

    /** The server gave the host role to someone else while we were away: open a fresh room. */
    private fun lostHost() {
        synchronized(lock) {
            wsRoomId = null
            pendingCreate = true
        }
        _open.value = false
        send(JSONObject().put("type", "leave_room"))
    }

    private fun startPinger() {
        synchronized(lock) {
            pingJob?.cancel()
            pingJob = scope.launch {
                while (true) {
                    delay(10_000)
                    send(JSONObject().put("type", "ping").put("t", System.currentTimeMillis()))
                }
            }
        }
    }

    private fun handle(text: String) {
        val o = try {
            JSONObject(text)
        } catch (_: Exception) {
            return
        }
        lastMsgAt = SystemClock.elapsedRealtime()
        when (o.optString("type")) {
            "connected" -> {
                clockOffset = o.optLong("serverTime") - System.currentTimeMillis()
                val room = synchronized(lock) { wsRoomId }
                if (isHost) {
                    if (room != null) sendJoin(room) else sendCreate()
                } else if (room != null) {
                    sendJoin(room)
                }
                startPinger()
            }
            "pong" -> {
                val t = o.optLong("t")
                val rtt = System.currentTimeMillis() - t
                if (t > 0 && rtt in 0L..1500L) clockOffset = o.optLong("serverTime") - (t + rtt / 2)
            }
            "room_created" -> {
                val id = o.optString("roomId")
                synchronized(lock) {
                    wsRoomId = id
                    retry = 0
                }
                _open.value = true
                onHostRoom?.invoke(id)
                flushHostState()
                PartyVoice.onSocketOpen(o.optJSONArray("voice"))
            }
            "joined" -> {
                val serverHost = o.optJSONObject("you")?.optBoolean("isHost") == true
                if (isHost && !serverHost) {
                    lostHost()
                    return
                }
                synchronized(lock) { retry = 0 }
                _open.value = true
                if (isHost) flushHostState() else {
                    applyState(o.optJSONObject("state"))
                    WatchParty.onBrowse(o.optJSONObject("browse"))
                }
                PartyVoice.onSocketOpen(o.optJSONArray("voice"))
            }
            "state" -> if (!isHost) applyState(o)
            "browse" -> if (!isHost) WatchParty.onBrowse(o.optJSONObject("browse"))
            "chat" -> PartyChat.onLive(o)
            "chat_delete" -> PartyChat.onGone(o.optString("id"))
            "voice_state", "voice_signal", "speaking" -> PartyVoice.onSocket(o.optString("type"), o)
            "left" -> {
                val create = synchronized(lock) {
                    val p = pendingCreate
                    pendingCreate = false
                    p
                }
                if (create) sendCreate()
            }
            "replaced", "kicked" -> {
                // Same account connected somewhere else, or removed: do not fight for the room.
                synchronized(lock) { wanted = false }
                _open.value = false
            }
            "error" -> when (o.optString("code")) {
                "room_not_found" -> {
                    val idleFor = SystemClock.elapsedRealtime() - lastMsgAt
                    if (lastMsgAt > 0L && idleFor > IDLE_END_MS) {
                        // Everyone was offline for 10 minutes: the server ended the room.
                        lastMsgAt = 0L
                        WatchParty.endedByIdle()
                    } else if (isHost) {
                        synchronized(lock) { wsRoomId = null }
                        sendCreate()
                    } else {
                        // The host may still be opening (or re-opening) the room: try again shortly.
                        _open.value = false
                        synchronized(lock) { socket?.close(1000, "retry") }
                    }
                }
                "not_host" -> if (isHost) lostHost()
                "unknown_type" -> {
                    if (o.optString("message").contains("voice")) PartyVoice.serverTooOld()
                    Log.w(TAG, "server error: $text")
                }
                else -> Log.w(TAG, "server error: $text")
            }
        }
    }

    /** Turns the server's playback state into what the player already understands. */
    private fun applyState(st: JSONObject?) {
        if (st == null) return
        st.optJSONObject("settings")?.let { o ->
            WatchParty.settings.value = WatchParty.Settings(
                provider = o.optString("provider"),
                audio = o.optString("audio"),
                sub = o.optString("sub"),
                fullscreen = o.optBoolean("fullscreen", false),
                speed = o.optDouble("speed", 1.0).toFloat(),
                subFont = o.optString("subFont"),
                subSize = o.optInt("subSize", -1),
                subBg = o.optInt("subBg", -1),
                subEdge = o.optInt("subEdge", -1),
                src = o.optString("src"),
            )
        }
        val video = st.optJSONObject("video") ?: return
        val meta = video.optJSONObject("meta") ?: return
        val animeId = meta.optInt("animeId", 0)
        val episode = meta.optInt("episode", 0)
        if (animeId <= 0 || episode <= 0) return
        val serverTime = st.optLong("serverTime", 0L)
        val sentAt = if (serverTime > 0L) serverTime - clockOffset else System.currentTimeMillis()
        WatchParty.remote.value = WatchParty.Remote(
            animeId,
            episode,
            video.optString("title"),
            WatchParty.room.value.hostName.ifBlank { "Host" },
            (st.optDouble("position", 0.0) * 1000).toLong(),
            st.optBoolean("playing", false),
            SystemClock.elapsedRealtime(),
            sentAt,
        )
    }

    private class Listener(private val gen: Int) : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            if (current(gen)) handle(text)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            dropped(gen)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "socket failure: ${t.message}")
            dropped(gen)
        }
    }
}
