package com.biaruuu.kiro.data

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

/**
 * Voice call for a watch party. Every member who joins the call connects directly to every other
 * one (WebRTC, audio only), so there is no server in the middle adding delay. The watch party
 * WebSocket server (kiro-watchparty) tells everyone who is in the call, whether they are muted,
 * deafened or speaking, and relays the connection details at the start (the audio itself never
 * goes through the server).
 *
 * Optional: put a TURN server in the Firestore document `meta/turn` ({urls: [...], username, credential})
 * to help phones on strict mobile networks connect.
 */
object PartyVoice {
    /** What the room sees of one person in the call. */
    class VState(val muted: Boolean = false, val deafened: Boolean = false, val speaking: Boolean = false)

    val joined = MutableStateFlow(false)
    val muted = MutableStateFlow(false)
    val deafened = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    private val _states = MutableStateFlow<Map<String, VState>>(emptyMap())

    /** Everyone who is in the call right now (including this phone), keyed by user id. */
    val states: StateFlow<Map<String, VState>> = _states

    private class Peer(val uid: String, val pc: PeerConnection) {
        var remoteSet = false
        val pending = ArrayList<IceCandidate>()
        val remoteTracks = ArrayList<AudioTrack>()
    }

    private val main = Handler(Looper.getMainLooper())
    private val peers = HashMap<String, Peer>()
    /** Connection candidates that arrived before the offer they belong to. */
    private val early = HashMap<String, ArrayList<IceCandidate>>()
    private var appContext: Context? = null
    private var factory: PeerConnectionFactory? = null
    private var adm: JavaAudioDeviceModule? = null
    private var source: AudioSource? = null
    private var track: AudioTrack? = null
    private var code: String? = null
    internal var iceServers: List<PeerConnection.IceServer> = emptyList()
    private var oldMode = AudioManager.MODE_NORMAL
    private var oldSpeaker = false
    private var initialized = false

    private fun db() = FirebaseFirestore.getInstance()

    private fun audio(): AudioManager? = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /** Joins the call. The microphone permission must already be granted. */
    fun join(context: Context) {
        if (joined.value) return
        val c = WatchParty.session.value?.code ?: return
        val me = WatchParty.uid() ?: return
        appContext = context.applicationContext
        error.value = null
        try {
            if (!initialized) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions(),
                )
                initialized = true
            }
            adm = JavaAudioDeviceModule.builder(appContext)
                .setSamplesReadyCallback(JavaAudioDeviceModule.SamplesReadyCallback { samples -> onMicSamples(samples.data) })
                .createAudioDeviceModule()
            factory = PeerConnectionFactory.builder().setAudioDeviceModule(adm).createPeerConnectionFactory()
            source = factory!!.createAudioSource(MediaConstraints())
            track = factory!!.createAudioTrack("kiro-voice", source)
            track!!.setEnabled(!muted.value)
        } catch (e: Throwable) {
            error.value = "Voice isn't available on this phone."
            releaseAll()
            return
        }
        code = c
        // Voice calls need the phone in communication mode so the speaker and echo cancelling work.
        audio()?.let {
            oldMode = it.mode
            oldSpeaker = it.isSpeakerphoneOn
            it.mode = AudioManager.MODE_IN_COMMUNICATION
            it.isSpeakerphoneOn = true
        }
        iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        )
        // Optional TURN relay set by the developer.
        db().collection("meta").document("turn").get().addOnSuccessListener { d ->
            @Suppress("UNCHECKED_CAST")
            val urls = (d.get("urls") as? List<String>).orEmpty()
            if (urls.isNotEmpty()) {
                iceServers = iceServers + PeerConnection.IceServer.builder(urls)
                    .setUsername(d.getString("username").orEmpty()).setPassword(d.getString("credential").orEmpty())
                    .createIceServer()
            }
        }
        joined.value = true
        deafened.value = false
        // Our own entry shows up straight away; the server's list replaces it as soon as it answers.
        _states.value = _states.value + (me to VState(muted.value, false, false))
        if (PartySocket.open.value) sendJoin()
        main.post { updateMeter() }
    }

    /** Tells the server this phone is in the call (also after the socket reconnects). */
    private fun sendJoin() {
        PartySocket.sendRaw(JSONObject().put("type", "voice_join").put("muted", muted.value).put("deafened", deafened.value))
    }

    /** The socket connected (or reconnected): rejoin the call there and learn who is in it. */
    fun onSocketOpen(voice: JSONArray?) {
        if (voice != null) applyList(voice)
        if (joined.value) main.post { sendJoin() }
    }

    fun serverTooOld() {
        error.value = "The voice server is updating. Try again in a minute."
        main.post { leave() }
    }

    /** Messages from the server: who is in the call, set-up notes from other phones, who is speaking. */
    fun onSocket(type: String, o: JSONObject) {
        when (type) {
            "voice_state" -> applyList(o.optJSONArray("members"))
            "speaking" -> {
                val uid = o.optString("userId")
                val on = o.optBoolean("on", false)
                val cur = _states.value[uid] ?: return
                if (cur.speaking != on) _states.value = _states.value + (uid to VState(cur.muted, cur.deafened, on))
            }
            "voice_signal" -> {
                val from = o.optString("from")
                val data = o.optJSONObject("data") ?: return
                main.post { handleSignal(from, data) }
            }
        }
    }

    private fun applyList(arr: JSONArray?) {
        val me = WatchParty.uid()
        val old = _states.value
        val next = HashMap<String, VState>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                val uid = m.optString("userId")
                if (uid.isBlank()) continue
                next[uid] = VState(m.optBoolean("muted", false), m.optBoolean("deafened", false), old[uid]?.speaking == true)
            }
        }
        // We are in the call from our side the moment we press Join, before the server has confirmed it.
        if (joined.value && me != null && !next.containsKey(me)) next[me] = VState(muted.value, deafened.value, false)
        _states.value = next
        if (joined.value) reconcile(next.keys.filter { it != me }.toSet())
    }

    fun leave() {
        if (!joined.value && factory == null) return
        joined.value = false
        deafened.value = false
        stopMeter()
        PartySocket.sendRaw(JSONObject().put("type", "voice_leave"))
        speakingSent = false
        WatchParty.uid()?.let { _states.value = _states.value - it }
        releaseAll()
    }

    /** The room is gone: leave the call and forget who was in it. */
    fun clear() {
        leave()
        _states.value = emptyMap()
    }

    fun setMuted(m: Boolean) {
        muted.value = m
        track?.setEnabled(!m)
        if (m) setSpeaking(false)
        sendMute()
    }

    /** Deafen: you stop hearing everyone (and, like Discord, your microphone is muted too). */
    fun setDeafened(d: Boolean) {
        if (d == deafened.value) return
        deafened.value = d
        if (d) {
            micBeforeDeafen = muted.value
            muted.value = true
            track?.setEnabled(false)
            setSpeaking(false)
        } else {
            muted.value = micBeforeDeafen
            track?.setEnabled(!micBeforeDeafen)
        }
        main.post { peers.values.forEach { p -> p.remoteTracks.forEach { it.setEnabled(!d) } } }
        sendMute()
    }

    private fun sendMute() {
        if (!joined.value) return
        val me = WatchParty.uid()
        if (me != null) {
            val cur = _states.value[me]
            _states.value = _states.value + (me to VState(muted.value, deafened.value, cur?.speaking == true))
        }
        PartySocket.sendRaw(JSONObject().put("type", "voice_mute").put("muted", muted.value).put("deafened", deafened.value))
    }

    // ---- who is speaking: measured on this phone from the microphone, shared through the server
    private var micBeforeDeafen = false
    private var speakingSent = false
    private var loudSince = 0L
    private var lastLoud = 0L

    private fun onMicSamples(data: ByteArray) {
        if (muted.value) return
        var sum = 0.0
        val n = data.size / 2
        if (n == 0) return
        var i = 0
        while (i + 1 < data.size) {
            val v = ((data[i + 1].toInt() shl 8) or (data[i].toInt() and 0xFF)).toShort().toInt()
            sum += v.toDouble() * v
            i += 2
        }
        val rms = Math.sqrt(sum / n)
        val t = android.os.SystemClock.elapsedRealtime()
        if (rms > 450) {
            lastLoud = t
            if (loudSince == 0L) loudSince = t
            if (!speakingSent && t - loudSince > 120) main.post { setSpeaking(true) }
        } else {
            if (t - lastLoud > 450) {
                loudSince = 0L
                if (speakingSent) main.post { setSpeaking(false) }
            }
        }
    }

    private fun setSpeaking(on: Boolean) {
        if (speakingSent == on || !joined.value) return
        speakingSent = on
        val me = WatchParty.uid() ?: return
        val cur = _states.value[me] ?: return
        _states.value = _states.value + (me to VState(cur.muted, cur.deafened, on))
        PartySocket.sendRaw(JSONObject().put("type", "speaking").put("on", on))
    }

    // ---- With nobody else in the call the call engine isn't listening yet, so the speaking light uses its own listener.
    @Volatile private var meterRun = false

    private fun updateMeter() {
        if (joined.value && peers.isEmpty()) startMeter() else stopMeter()
    }

    private fun stopMeter() {
        meterRun = false
    }

    private fun startMeter() {
        if (meterRun || !joined.value) return
        val ctx = appContext ?: return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val rate = 16000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 2)
        } catch (_: Throwable) { return }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return }
        meterRun = true
        Thread {
            val buf = ByteArray(1600)
            try {
                rec.startRecording()
                while (meterRun) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) onMicSamples(buf.copyOf(n)) else if (n < 0) break
                }
            } catch (_: Throwable) {
            } finally {
                meterRun = false
                try { rec.stop() } catch (_: Throwable) {}
                rec.release()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun releaseAll() {
        stopMeter()
        peers.values.forEach { try { it.pc.close() } catch (_: Throwable) {} }
        peers.clear()
        early.clear()
        try { track?.dispose() } catch (_: Throwable) {}
        try { source?.dispose() } catch (_: Throwable) {}
        try { factory?.dispose() } catch (_: Throwable) {}
        try { adm?.release() } catch (_: Throwable) {}
        track = null; source = null; factory = null; adm = null
        audio()?.let {
            it.isSpeakerphoneOn = oldSpeaker
            it.mode = oldMode
        }
        code = null
    }

    /** Connects to the people in the call and drops the ones who left. */
    private fun reconcile(inCall: Set<String>) {
        val me = WatchParty.uid() ?: return
        main.post {
            if (!joined.value) return@post
            for (uid in inCall) {
                if (!peers.containsKey(uid)) createPeer(uid, offer = me < uid)
            }
            for (uid in peers.keys.toList()) {
                if (uid !in inCall) closePeer(uid)
            }
            updateMeter()
        }
    }

    private fun closePeer(uid: String) {
        peers.remove(uid)?.let { try { it.pc.close() } catch (_: Throwable) {} }
        updateMeter()
    }

    private fun createPeer(uid: String, offer: Boolean): Peer? {
        val f = factory ?: return null
        val t = track ?: return null
        stopMeter()
        val cfg = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        var created: Peer? = null
        val pc = f.createPeerConnection(cfg, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate?) {
                if (c == null) return
                send(uid, JSONObject().put("type", "ice").put("mid", c.sdpMid ?: "").put("idx", c.sdpMLineIndex).put("cand", c.sdp))
            }
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
                if (s == PeerConnection.IceConnectionState.FAILED) {
                    // Start this link over a moment later.
                    main.postDelayed({
                        if (joined.value && peers[uid] === created) {
                            closePeer(uid)
                            reconcile(_states.value.keys.filter { it != WatchParty.uid() }.toSet())
                        }
                    }, 1500)
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
                val t = r?.track() as? AudioTrack ?: return
                t.setEnabled(!deafened.value)
                main.post { created?.remoteTracks?.add(t) }
            }
        }) ?: return null
        pc.addTrack(t, listOf("kiro"))
        val peer = Peer(uid, pc)
        early.remove(uid)?.let { peer.pending.addAll(it) }
        created = peer
        peers[uid] = peer
        if (offer) {
            pc.createOffer(object : Sdp() {
                override fun onCreateSuccess(d: SessionDescription?) {
                    if (d == null) return
                    pc.setLocalDescription(Sdp(), d)
                    send(uid, JSONObject().put("type", "offer").put("sdp", d.description))
                }
            }, MediaConstraints())
        }
        return peer
    }

    private fun handleSignal(from: String, d: JSONObject) {
        if (from.isBlank()) return
        val type = d.optString("type")
        when (type) {
            "offer" -> {
                if (!joined.value) return
                val peer = peers[from] ?: createPeer(from, offer = false) ?: return
                val sdp = d.optString("sdp").ifBlank { return }
                peer.pc.setRemoteDescription(object : Sdp() {
                    override fun onSetSuccess() {
                        peer.remoteSet = true
                        peer.pending.forEach { peer.pc.addIceCandidate(it) }
                        peer.pending.clear()
                        peer.pc.createAnswer(object : Sdp() {
                            override fun onCreateSuccess(a: SessionDescription?) {
                                if (a == null) return
                                peer.pc.setLocalDescription(Sdp(), a)
                                send(from, JSONObject().put("type", "answer").put("sdp", a.description))
                            }
                        }, MediaConstraints())
                    }
                }, SessionDescription(SessionDescription.Type.OFFER, sdp))
            }
            "answer" -> {
                val peer = peers[from] ?: return
                val sdp = d.optString("sdp").ifBlank { return }
                peer.pc.setRemoteDescription(object : Sdp() {
                    override fun onSetSuccess() {
                        peer.remoteSet = true
                        peer.pending.forEach { peer.pc.addIceCandidate(it) }
                        peer.pending.clear()
                    }
                }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
            }
            "ice" -> {
                val cand = IceCandidate(d.optString("mid"), d.optInt("idx", 0), d.optString("cand").ifBlank { return })
                val peer = peers[from]
                if (peer == null) early.getOrPut(from) { ArrayList() }.add(cand)
                else if (peer.remoteSet) peer.pc.addIceCandidate(cand) else peer.pending.add(cand)
            }
        }
    }

    private fun send(to: String, body: JSONObject) {
        PartySocket.sendRaw(JSONObject().put("type", "voice_signal").put("to", to).put("data", body))
    }

    private open class Sdp : SdpObserver {
        override fun onCreateSuccess(d: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(e: String?) {}
        override fun onSetFailure(e: String?) {}
    }
}
