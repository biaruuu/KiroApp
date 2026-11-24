package com.biaruuu.kiro.data

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/**
 * Live screen of the host for everyone in a watch party. The host captures its screen (Android's screen
 * recording permission) and sends it as video straight to each guest phone over WebRTC; the party server only
 * relays the set-up notes and tells guests when the host starts or stops.
 */
object PartyScreen {
    /** The host is sharing right now (everyone in the room knows). */
    val sharing = MutableStateFlow(false)

    /** This phone is the one capturing. */
    val mine = MutableStateFlow(false)

    /** Guest: the incoming picture. */
    val video = MutableStateFlow<VideoTrack?>(null)

    private val main = Handler(Looper.getMainLooper())
    private var egl: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var inited = false
    private var appContext: Context? = null

    // host side
    private var capturer: ScreenCapturerAndroid? = null
    private var helper: SurfaceTextureHelper? = null
    private var source: VideoSource? = null
    private var track: VideoTrack? = null
    private val viewers = HashMap<String, Link>()

    // guest side
    private var incoming: Link? = null
    private var asked = false

    private class Link(val pc: PeerConnection) {
        var remoteSet = false
        val pending = ArrayList<IceCandidate>()
    }

    /** Remembers the app context (a guest needs it to open the incoming picture). */
    fun attach(c: Context) {
        appContext = c.applicationContext
    }

    fun eglContext(): EglBase.Context = ensureEgl().eglBaseContext

    private fun ensureEgl(): EglBase = egl ?: EglBase.create().also { egl = it }

    private fun ensureFactory(c: Context): PeerConnectionFactory? {
        factory?.let { return it }
        return try {
            if (!inited) {
                PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(c.applicationContext).createInitializationOptions())
                inited = true
            }
            val e = ensureEgl()
            PeerConnectionFactory.builder()
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(e.eglBaseContext, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(e.eglBaseContext))
                .createPeerConnectionFactory().also { factory = it }
        } catch (_: Throwable) {
            null
        }
    }

    private fun ice(): List<PeerConnection.IceServer> =
        PartyVoice.iceServers.ifEmpty { listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()) }

    private fun send(to: String, body: JSONObject) {
        PartySocket.sendRaw(JSONObject().put("type", "screen_signal").put("to", to).put("data", body))
    }

    // ------------------------------------------------------------------ host

    /** Starts capturing. The screen recording permission result and the foreground notification must already be in place. */
    fun start(context: Context, permission: Intent) {
        if (mine.value) return
        appContext = context.applicationContext
        val f = ensureFactory(context) ?: return
        try {
            val e = ensureEgl()
            val src = f.createVideoSource(true)
            val h = SurfaceTextureHelper.create("kiro-screen", e.eglBaseContext)
            val cap = ScreenCapturerAndroid(permission, object : MediaProjection.Callback() {
                override fun onStop() {
                    main.post { stop() }
                }
            })
            cap.initialize(h, context.applicationContext, src.capturerObserver)
            val dm = context.resources.displayMetrics
            val scale = minOf(1f, 1280f / maxOf(dm.widthPixels, dm.heightPixels))
            val w = ((dm.widthPixels * scale).toInt() / 2) * 2
            val hh = ((dm.heightPixels * scale).toInt() / 2) * 2
            cap.startCapture(w, hh, 15)
            capturer = cap; helper = h; source = src
            track = f.createVideoTrack("kiro-screen", src)
            mine.value = true
            sharing.value = true
            PartySocket.sendRaw(JSONObject().put("type", "screen_start"))
        } catch (_: Throwable) {
            stop()
        }
    }

    /** A guest asked for the picture: open a link to it. */
    fun onWatch(from: String) {
        if (!mine.value) return
        main.post { openViewer(from) }
    }

    private fun openViewer(uid: String) {
        val f = factory ?: return
        val t = track ?: return
        viewers.remove(uid)?.let { try { it.pc.close() } catch (_: Throwable) {} }
        val cfg = PeerConnection.RTCConfiguration(ice()).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
        val pc = f.createPeerConnection(cfg, observer(uid)) ?: return
        pc.addTrack(t, listOf("screen"))
        try {
            for (s in pc.senders) {
                val p = s.parameters
                for (e in p.encodings) { e.maxBitrateBps = 2_000_000; e.maxFramerate = 15 }
                p.degradationPreference = org.webrtc.RtpParameters.DegradationPreference.MAINTAIN_RESOLUTION
                s.parameters = p
            }
        } catch (_: Throwable) {}
        val link = Link(pc)
        viewers[uid] = link
        pc.createOffer(object : Sdp() {
            override fun onCreateSuccess(d: SessionDescription?) {
                if (d == null) return
                pc.setLocalDescription(Sdp(), d)
                send(uid, JSONObject().put("type", "offer").put("sdp", d.description))
            }
        }, MediaConstraints())
    }

    fun stop() {
        val wasMine = mine.value
        mine.value = false
        if (wasMine) {
            sharing.value = false
            PartySocket.sendRaw(JSONObject().put("type", "screen_stop"))
        }
        viewers.values.forEach { try { it.pc.close() } catch (_: Throwable) {} }
        viewers.clear()
        try { capturer?.stopCapture() } catch (_: Throwable) {}
        try { capturer?.dispose() } catch (_: Throwable) {}
        try { track?.dispose() } catch (_: Throwable) {}
        try { source?.dispose() } catch (_: Throwable) {}
        try { helper?.dispose() } catch (_: Throwable) {}
        capturer = null; track = null; source = null; helper = null
        appContext?.let { ScreenShareService.stop(it) }
    }

    // ----------------------------------------------------------------- guest

    /** The server says the host started or stopped sharing. */
    fun onState(on: Boolean) {
        if (mine.value) return
        sharing.value = on
        if (on) requestWatch() else closeIncoming()
    }

    /** Joined (or reconnected) while the host is sharing. */
    fun onJoined(on: Boolean, isHost: Boolean) {
        if (isHost) {
            // The socket came back: tell the room again if this phone is still capturing.
            if (mine.value) PartySocket.sendRaw(JSONObject().put("type", "screen_start"))
            return
        }
        sharing.value = on
        if (on) requestWatch() else closeIncoming()
    }

    /** A guest opened the room while the host was already sharing and nothing arrived yet. */
    fun ensure() {
        if (!mine.value && sharing.value && video.value == null) requestWatch()
    }

    private fun requestWatch() {
        asked = true
        PartySocket.sendRaw(JSONObject().put("type", "screen_watch"))
    }

    private fun closeIncoming() {
        asked = false
        main.post {
            incoming?.let { try { it.pc.close() } catch (_: Throwable) {} }
            incoming = null
            video.value = null
        }
    }

    // --------------------------------------------------------------- signals

    fun onSignal(from: String, d: JSONObject) {
        main.post { handle(from, d) }
    }

    private fun handle(from: String, d: JSONObject) {
        when (d.optString("type")) {
            "offer" -> { // guest
                val ctx = appContext ?: return
                val f = ensureFactory(ctx) ?: return
                incoming?.let { try { it.pc.close() } catch (_: Throwable) {} }
                val cfg = PeerConnection.RTCConfiguration(ice()).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
                val pc = f.createPeerConnection(cfg, observer(from)) ?: return
                val link = Link(pc)
                incoming = link
                val sdp = d.optString("sdp")
                if (sdp.isBlank()) return
                pc.setRemoteDescription(object : Sdp() {
                    override fun onSetSuccess() {
                        link.remoteSet = true
                        link.pending.forEach { pc.addIceCandidate(it) }
                        link.pending.clear()
                        pc.createAnswer(object : Sdp() {
                            override fun onCreateSuccess(a: SessionDescription?) {
                                if (a == null) return
                                pc.setLocalDescription(Sdp(), a)
                                send(from, JSONObject().put("type", "answer").put("sdp", a.description))
                            }
                        }, MediaConstraints())
                    }
                }, SessionDescription(SessionDescription.Type.OFFER, sdp))
            }
            "answer" -> { // host
                val link = viewers[from] ?: return
                val sdp = d.optString("sdp")
                if (sdp.isBlank()) return
                link.pc.setRemoteDescription(object : Sdp() {
                    override fun onSetSuccess() {
                        link.remoteSet = true
                        link.pending.forEach { link.pc.addIceCandidate(it) }
                        link.pending.clear()
                    }
                }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
            }
            "ice" -> {
                val c = IceCandidate(d.optString("mid"), d.optInt("idx", 0), d.optString("cand").ifBlank { return })
                val link = if (mine.value) viewers[from] else incoming
                if (link == null) return
                if (link.remoteSet) link.pc.addIceCandidate(c) else link.pending.add(c)
            }
        }
    }

    private fun observer(peer: String) = object : PeerConnection.Observer {
        override fun onIceCandidate(c: IceCandidate?) {
            if (c == null) return
            send(peer, JSONObject().put("type", "ice").put("mid", c.sdpMid ?: "").put("idx", c.sdpMLineIndex).put("cand", c.sdp))
        }
        override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
            if (s == PeerConnection.IceConnectionState.FAILED && !mine.value && sharing.value) {
                // The picture link broke: ask the host to start it again.
                main.postDelayed({ if (sharing.value && !mine.value) requestWatch() }, 1500)
            }
        }
        override fun onSignalingChange(s: PeerConnection.SignalingState?) {}
        override fun onIceConnectionReceivingChange(b: Boolean) {}
        override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(c: Array<out IceCandidate>?) {}
        override fun onAddStream(s: MediaStream?) {}
        override fun onRemoveStream(s: MediaStream?) {}
        override fun onDataChannel(d: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(r: RtpReceiver?, s: Array<out MediaStream>?) {
            val t = r?.track() as? VideoTrack ?: return
            t.setEnabled(true)
            main.post { video.value = t }
        }
    }

    /** Leaves the room: stop sharing, drop the picture. */
    fun clear() {
        if (mine.value) stop()
        closeIncoming()
        sharing.value = false
    }

    private open class Sdp : SdpObserver {
        override fun onCreateSuccess(d: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(e: String?) {}
        override fun onSetFailure(e: String?) {}
    }
}
