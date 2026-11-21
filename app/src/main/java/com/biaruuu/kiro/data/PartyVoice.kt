package com.biaruuu.kiro.data

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.JavaAudioDeviceModule
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

/**
 * Voice call for a watch party. Every member who joins the call connects directly to every other
 * one (WebRTC, audio only), so there is no server in the middle adding delay. The Firestore
 * collection `parties/{code}/signals` is only used to exchange the connection details at the start.
 *
 * Optional: put a TURN server in the Firestore document `meta/turn` ({urls: [...], username, credential})
 * to help phones on strict mobile networks connect.
 */
object PartyVoice {
    val joined = MutableStateFlow(false)
    val muted = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    private class Peer(val uid: String, val pc: PeerConnection) {
        var remoteSet = false
        val pending = ArrayList<IceCandidate>()
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
    private var signalReg: ListenerRegistration? = null
    private var code: String? = null
    private var iceServers: List<PeerConnection.IceServer> = emptyList()
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
            adm = JavaAudioDeviceModule.builder(appContext).createAudioDeviceModule()
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
        val now = System.currentTimeMillis()
        signalReg = db().collection("parties").document(c).collection("signals").whereEqualTo("to", me)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val added = snap.documentChanges.filter { it.type == DocumentChange.Type.ADDED }
                    .sortedBy { (it.document.get("at") as? Number)?.toLong() ?: 0L }
                for (ch in added) {
                    val doc = ch.document
                    val data = doc.data
                    doc.reference.delete()
                    // Ignore old leftovers (the limit is wide so phones with slightly different clocks still work).
                    if (((data["at"] as? Number)?.toLong() ?: 0L) < now - 300_000L) continue
                    main.post { handleSignal(data) }
                }
            }
        joined.value = true
        WatchParty.setVoice(true, muted.value)
        reconcile(WatchParty.members.value)
    }

    fun leave() {
        if (!joined.value && factory == null) return
        joined.value = false
        WatchParty.setVoice(false, false)
        releaseAll()
    }

    fun setMuted(m: Boolean) {
        muted.value = m
        track?.setEnabled(!m)
        if (joined.value) WatchParty.setVoice(true, m)
    }

    private fun releaseAll() {
        signalReg?.remove()
        signalReg = null
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

    /** Connects to members who are in the call and drops the ones who left. Call when the member list changes. */
    fun reconcile(members: List<WatchParty.Member>) {
        if (!joined.value) return
        val me = WatchParty.uid() ?: return
        val inCall = members.filter { it.voice && it.uid != me }.map { it.uid }.toSet()
        main.post {
            for (uid in inCall) {
                if (!peers.containsKey(uid)) createPeer(uid, offer = me < uid)
            }
            for (uid in peers.keys.toList()) {
                if (uid !in inCall) closePeer(uid)
            }
        }
    }

    private fun closePeer(uid: String) {
        peers.remove(uid)?.let { try { it.pc.close() } catch (_: Throwable) {} }
    }

    private fun createPeer(uid: String, offer: Boolean): Peer? {
        val f = factory ?: return null
        val t = track ?: return null
        val cfg = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        var created: Peer? = null
        val pc = f.createPeerConnection(cfg, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate?) {
                if (c == null) return
                send(uid, mapOf("type" to "ice", "mid" to (c.sdpMid ?: ""), "idx" to c.sdpMLineIndex, "cand" to c.sdp))
            }
            override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
                if (s == PeerConnection.IceConnectionState.FAILED) {
                    // Start this link over a moment later.
                    main.postDelayed({
                        if (joined.value && peers[uid] === created) {
                            closePeer(uid)
                            reconcile(WatchParty.members.value)
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
            override fun onAddTrack(r: RtpReceiver?, s: Array<out MediaStream>?) {}
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
                    send(uid, mapOf("type" to "offer", "sdp" to d.description))
                }
            }, MediaConstraints())
        }
        return peer
    }

    private fun handleSignal(d: Map<String, Any>) {
        val from = d["from"] as? String ?: return
        val type = d["type"] as? String ?: return
        when (type) {
            "offer" -> {
                if (!joined.value) return
                val peer = peers[from] ?: createPeer(from, offer = false) ?: return
                val sdp = d["sdp"] as? String ?: return
                peer.pc.setRemoteDescription(object : Sdp() {
                    override fun onSetSuccess() {
                        peer.remoteSet = true
                        peer.pending.forEach { peer.pc.addIceCandidate(it) }
                        peer.pending.clear()
                        peer.pc.createAnswer(object : Sdp() {
                            override fun onCreateSuccess(a: SessionDescription?) {
                                if (a == null) return
                                peer.pc.setLocalDescription(Sdp(), a)
                                send(from, mapOf("type" to "answer", "sdp" to a.description))
                            }
                        }, MediaConstraints())
                    }
                }, SessionDescription(SessionDescription.Type.OFFER, sdp))
            }
            "answer" -> {
                val peer = peers[from] ?: return
                val sdp = d["sdp"] as? String ?: return
                peer.pc.setRemoteDescription(object : Sdp() {
                    override fun onSetSuccess() {
                        peer.remoteSet = true
                        peer.pending.forEach { peer.pc.addIceCandidate(it) }
                        peer.pending.clear()
                    }
                }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
            }
            "ice" -> {
                val cand = IceCandidate(
                    d["mid"] as? String ?: "", (d["idx"] as? Number)?.toInt() ?: 0, d["cand"] as? String ?: return,
                )
                val peer = peers[from]
                if (peer == null) early.getOrPut(from) { ArrayList() }.add(cand)
                else if (peer.remoteSet) peer.pc.addIceCandidate(cand) else peer.pending.add(cand)
            }
        }
    }

    private fun send(to: String, body: Map<String, Any>) {
        val c = code ?: return
        val me = WatchParty.uid() ?: return
        db().collection("parties").document(c).collection("signals")
            .add(body + mapOf("from" to me, "to" to to, "at" to System.currentTimeMillis()))
    }

    private open class Sdp : SdpObserver {
        override fun onCreateSuccess(d: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(e: String?) {}
        override fun onSetFailure(e: String?) {}
    }
}
