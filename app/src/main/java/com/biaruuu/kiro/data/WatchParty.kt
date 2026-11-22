package com.biaruuu.kiro.data

import android.content.Context
import android.os.SystemClock
import com.biaruuu.kiro.data.auth.AuthRepository
import com.biaruuu.kiro.data.auth.AuthState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Watch party: the host's player state (episode, position, playing or paused) is kept in a
 * Firestore document `parties/{code}`; everyone who joined with the code follows it in real time.
 */
object WatchParty {
    class Session(val code: String, val host: Boolean)

    /** The host's latest state; [receivedAt] is this phone's clock when it arrived. */
    class Remote(
        val animeId: Int, val episode: Int, val title: String, val hostName: String,
        val positionMs: Long, val playing: Boolean, val receivedAt: Long,
        /** The host phone's clock when it sent this (used to cancel out the network delay). */
        val sentAt: Long = 0L,
    )

    val session = MutableStateFlow<Session?>(null)
    val remote = MutableStateFlow<Remote?>(null)
    /** Everyone in the party, host first, with the profile look each one chose. */
    class Member(
        val uid: String, val name: String, val avatar: String, val decoration: String, val nameFont: String, val host: Boolean,
        val voice: Boolean = false, val muted: Boolean = false,
        /** This phone's clock (elapsedRealtime) until which the person counts as typing. */
        val typingUntil: Long = 0L,
        /** Time of the newest chat message this person has seen. */
        val seenAt: Long = 0L,
    )

    val members = MutableStateFlow<List<Member>>(emptyList())

    /** What the host is using. Guests copy it so they see and hear the same thing. */
    data class Settings(
        /** Provider (source) name, for example "Anivault". Empty means "no preference". */
        val provider: String = "",
        /** "Japanese", "English" or "Filipino". */
        val audio: String = "",
        /** A subtitle language code, or "off". */
        val sub: String = "",
        val fullscreen: Boolean = false,
        val speed: Float = 1f,
        /** The host's subtitle look: font name, then indexes into the size, background and edge lists (-1 = not set). */
        val subFont: String = "",
        val subSize: Int = -1,
        val subBg: Int = -1,
        val subEdge: Int = -1,
    )

    val settings = MutableStateFlow(Settings())

    /** The host shares its source, subtitle, speed and fullscreen. Only does something for the host. */
    fun pushSettings(s: Settings) {
        val sess = session.value ?: return
        if (!sess.host || settings.value == s) return
        settings.value = s
        PartySocket.sendSettings(s)
    }

    /** Room-wide settings the host controls. [banned] maps a removed person's uid to their name. */
    class Room(
        val name: String = "", val voiceOff: Boolean = false, val banned: Map<String, String> = emptyMap(), val hostName: String = "",
        /** Permissions the host sets: can guests chat or talk in voice at all, and per person exceptions ("chat", "voice" or "chat,voice" = not allowed). */
        val guestChat: Boolean = true, val guestVoice: Boolean = true, val denied: Map<String, String> = emptyMap(),
    )

    val room = MutableStateFlow(Room())

    /** A name used only in this room (chat and member list). Empty means the profile name. */
    private var nickname: String = ""

    /** One-shot message for the player to show (for example "The host ended the party"). */
    val message = MutableStateFlow<String?>(null)

    /** One-shot notice for whoever is in the room, wherever they browse (for example "Sam left the watch party"). */
    val notice = MutableStateFlow<String?>(null)

    private val typingState = HashMap<String, Pair<Long, Long>>()
    private var knownMembers: Map<String, String>? = null
    private var lastTypingWrite = 0L
    private var lastSeenWritten = 0L

    private var partyReg: ListenerRegistration? = null
    private var membersReg: ListenerRegistration? = null

    // What the host last wrote to Firestore (so it is not written every 1.5 s while the socket works).
    private var lastFsPlaying = false
    private var lastFsEpisode = -1
    private var lastFsAt = 0L

    private fun db() = FirebaseFirestore.getInstance()
    internal fun uid() = (AuthRepository.state.value as? AuthState.SignedIn)?.uid
    internal fun myName() = nickname.ifBlank { ProfileRepository.profile.value.username.ifBlank { "Guest" } }

    private fun writeMember(code: String, me: String, host: Boolean) {
        val p = ProfileRepository.profile.value
        db().collection("parties").document(code).collection("members").document(me).set(
            mapOf(
                "name" to myName(), "avatar" to p.avatar, "decoration" to p.decoration, "nameFont" to p.nameFont,
                "host" to host, "at" to System.currentTimeMillis(),
            ),
        )
    }

    private fun friendly(e: Exception): String =
        if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            "Watch party isn't switched on yet: publish the new Firestore rules (see the release notes)."
        } else {
            "Couldn't reach the watch party server. Check your connection."
        }

    internal fun newCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { alphabet.random() }.joinToString("")
    }

    /** Starts a party for the episode being watched; [onDone] gets the code, or an error message. */
    fun host(c: Context, animeId: Int, episode: Int, title: String, roomName: String = "", onDone: (String?, String?) -> Unit) {
        val me = uid() ?: return onDone(null, "Log in to start a watch party")
        val code = newCode()
        val data = hashMapOf<String, Any>(
            "hostUid" to me, "hostName" to myName(), "animeId" to animeId, "episode" to episode,
            "title" to title, "positionMs" to 0L, "playing" to false, "createdAt" to System.currentTimeMillis(),
            "sentAt" to System.currentTimeMillis(), "roomName" to roomName.trim(), "voiceOff" to false,
            "banned" to emptyMap<String, String>(),
        )
        db().collection("parties").document(code).set(data)
            .addOnSuccessListener {
                session.value = Session(code, true)
                writeMember(code, me, true)
                Achievements.bump(c, "hosted")
                listenMembers(code)
                listenParty(code, me)
                // Realtime playback goes over the socket; guests find its room code in `wsRoom`.
                PartySocket.startHost(me, myName(), animeId, episode, title) { wsId ->
                    db().collection("parties").document(code).update("wsRoom", wsId)
                }
                onDone(code, null)
            }
            .addOnFailureListener { onDone(null, friendly(it)) }
    }

    /** Looks the code up (without joining yet); [onDone] gets the party's current state, or an error message. */
    fun lookup(code: String, onDone: (Remote?, String?) -> Unit) {
        if (uid() == null) return onDone(null, "Log in to join a watch party")
        db().collection("parties").document(code.trim().uppercase()).get()
            .addOnSuccessListener { snap ->
                val r = if (snap.exists()) toRemote(snap.data) else null
                val banned = (snap.get("banned") as? Map<*, *>)?.containsKey(uid()) == true
                if (r == null) onDone(null, "No watch party with that code")
                else if (banned) onDone(null, "The host removed you from that room")
                else onDone(r, null)
            }
            .addOnFailureListener { onDone(null, friendly(it)) }
    }

    private fun toRemote(d: Map<String, Any>?): Remote? {
        if (d == null) return null
        return Remote(
            (d["animeId"] as? Number)?.toInt() ?: return null,
            (d["episode"] as? Number)?.toInt() ?: return null,
            d["title"] as? String ?: "",
            d["hostName"] as? String ?: "Host",
            (d["positionMs"] as? Number)?.toLong() ?: 0L,
            d["playing"] as? Boolean ?: false,
            SystemClock.elapsedRealtime(),
            (d["sentAt"] as? Number)?.toLong() ?: 0L,
        )
    }

    /** Joins the party as a guest and starts following the host. */
    fun enter(c: Context, rawCode: String) {
        val code = rawCode.trim().uppercase()
        val me = uid() ?: return
        if (session.value?.code == code) return
        stopListening()
        session.value = Session(code, false)
        remote.value = null
        Achievements.bump(c, "joined")
        PartySocket.warmUp()
        writeMember(code, me, false)
        listenParty(code, me)
        listenMembers(code)
    }

    /** Follows the party document: the host's playback state and the room settings, live. */
    private fun listenParty(code: String, me: String) {
        partyReg?.remove()
        partyReg = db().collection("parties").document(code).addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            if (!snap.exists()) {
                if (!snap.metadata.isFromCache && session.value?.host == false) {
                    message.value = "The host ended the watch party"
                    stopListening()
                    session.value = null
                }
                return@addSnapshotListener
            }
            @Suppress("UNCHECKED_CAST")
            val banned = (snap.get("banned") as? Map<String, Any>)?.mapValues { it.value.toString() }.orEmpty()
            val denied = (snap.get("denied") as? Map<String, Any>)?.mapValues { it.value.toString() }.orEmpty()
            room.value = Room(
                snap.getString("roomName").orEmpty(), snap.getBoolean("voiceOff") == true, banned, snap.getString("hostName").orEmpty(),
                snap.getBoolean("guestChat") != false, snap.getBoolean("guestVoice") != false, denied,
            )
            if (session.value?.host == false && banned.containsKey(me)) {
                message.value = "You were removed from the room"
                leave()
                return@addSnapshotListener
            }
            // Guests follow the host over the realtime socket; Firestore only fills in while it is not connected.
            val wsRoom = snap.getString("wsRoom").orEmpty()
            if (session.value?.host == false && wsRoom.isNotBlank()) PartySocket.startGuest(wsRoom, me, myName())
            toRemote(snap.data)?.let { if (session.value?.host == true || !PartySocket.open.value) remote.value = it }
        }
    }

    private fun listenMembers(code: String) {
        membersReg?.remove()
        membersReg = db().collection("parties").document(code).collection("members")
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { d ->
                    // Typing: the writer's clock may differ from ours, so count 4.5 s from when the change reached us.
                    val raw = d.getLong("typingAt") ?: 0L
                    val prev = typingState[d.id]
                    val until = when {
                        raw == 0L -> 0L
                        prev?.first == raw -> prev.second
                        else -> SystemClock.elapsedRealtime() + 4500L
                    }
                    typingState[d.id] = raw to until
                    Member(
                        d.id, d.getString("name") ?: return@mapNotNull null, d.getString("avatar").orEmpty(),
                        d.getString("decoration").orEmpty(), d.getString("nameFont").orEmpty(), d.getBoolean("host") ?: false,
                        d.getBoolean("voice") ?: false, d.getBoolean("muted") ?: false,
                        until, d.getLong("seenAt") ?: 0L,
                    )
                }?.sortedWith(compareByDescending<Member> { it.host }.thenBy { it.name.lowercase() }) ?: emptyList()
                members.value = list
                // Tell the room when someone leaves. Only server snapshots count, so a stale cache never fakes it.
                if (snap != null && !snap.metadata.isFromCache) {
                    val nowIds = list.associate { it.uid to it.name }
                    val me = uid()
                    knownMembers?.forEach { (id, name) ->
                        if (id !in nowIds && id != me) {
                            notice.value = if (room.value.banned.containsKey(id)) "$name was removed from the watch party"
                            else "$name left the watch party"
                        }
                    }
                    knownMembers = nowIds
                }
            }
    }

    /** The host shares where they are; guests catch up to it. [playing] means actually running, not buffering. */
    fun push(animeId: Int, episode: Int, positionMs: Long, playing: Boolean, title: String = "") {
        val s = session.value ?: return
        if (!s.host) return
        val viaSocket = PartySocket.push(animeId, episode, positionMs, playing, title)
        // Firestore is the backup (and what a late joiner reads before the socket connects), so
        // while the socket works it is only written on a change or every 10 seconds.
        val nowMs = System.currentTimeMillis()
        if (viaSocket && playing == lastFsPlaying && episode == lastFsEpisode && nowMs - lastFsAt < 10_000L) return
        lastFsPlaying = playing
        lastFsEpisode = episode
        lastFsAt = nowMs
        val fields = hashMapOf<String, Any>(
            "animeId" to animeId, "episode" to episode, "positionMs" to positionMs, "playing" to playing,
            "sentAt" to System.currentTimeMillis(),
        )
        if (title.isNotBlank()) fields["title"] = title
        db().collection("parties").document(s.code).update(fields)
    }

    /** The host picks what everyone watches (the player then keeps it updated). */
    fun select(animeId: Int, episode: Int) {
        val s = session.value ?: return
        if (!s.host) return
        PartySocket.select(animeId, episode)
        lastFsEpisode = episode
        lastFsAt = System.currentTimeMillis()
        lastFsPlaying = false
        db().collection("parties").document(s.code).update(
            mapOf("animeId" to animeId, "episode" to episode, "positionMs" to 0L, "playing" to false, "title" to "", "sentAt" to System.currentTimeMillis()),
        )
    }

    fun setRoomName(name: String) {
        val s = session.value ?: return
        if (s.host) db().collection("parties").document(s.code).update("roomName", name.trim().take(40))
    }

    /** Whether this person may write in the room chat (the host always can). */
    fun canChat(): Boolean {
        val s = session.value ?: return false
        if (s.host) return true
        val r = room.value
        return r.guestChat && r.denied[uid()]?.contains("chat") != true
    }

    /** Whether this person may join the voice call (the host always can). */
    fun canVoice(): Boolean {
        val s = session.value ?: return false
        if (s.host) return true
        val r = room.value
        return !r.voiceOff && r.guestVoice && r.denied[uid()]?.contains("voice") != true
    }

    fun setGuestChat(on: Boolean) {
        val s = session.value ?: return
        if (s.host) db().collection("parties").document(s.code).update("guestChat", on)
    }

    fun setGuestVoice(on: Boolean) {
        val s = session.value ?: return
        if (s.host) db().collection("parties").document(s.code).update("guestVoice", on)
    }

    /** Host: allow or block one guest from chat and voice. */
    fun setDenied(uid: String, chat: Boolean, voice: Boolean) {
        val s = session.value ?: return
        if (!s.host || uid == me()) return
        val v = listOfNotNull(if (chat) "chat" else null, if (voice) "voice" else null).joinToString(",")
        db().collection("parties").document(s.code).update(
            "denied.$uid", if (v.isEmpty()) com.google.firebase.firestore.FieldValue.delete() else v,
        )
    }

    fun setVoiceOff(off: Boolean) {
        val s = session.value ?: return
        if (s.host) db().collection("parties").document(s.code).update("voiceOff", off)
    }

    /** Removes someone from the room and keeps them out until the host allows them back. */
    fun ban(uid: String, name: String) {
        val s = session.value ?: return
        if (!s.host || uid == me()) return
        db().collection("parties").document(s.code).update("banned.$uid", name)
        db().collection("parties").document(s.code).collection("members").document(uid).delete()
    }

    fun unban(uid: String) {
        val s = session.value ?: return
        if (s.host) db().collection("parties").document(s.code).update("banned.$uid", com.google.firebase.firestore.FieldValue.delete())
    }

    /** Deletes every chat message in the room (host only). */
    fun clearChat() {
        val s = session.value ?: return
        if (!s.host) return
        val col = db().collection("parties").document(s.code).collection("messages")
        col.get().addOnSuccessListener { snap ->
            val batch = db().batch()
            snap.documents.forEach { batch.delete(it.reference) }
            batch.commit()
        }
    }

    /** Changes the name shown in this room only. */
    fun setNickname(nick: String) {
        nickname = nick.trim().take(24)
        val s = session.value ?: return
        val me = uid() ?: return
        db().collection("parties").document(s.code).collection("members").document(me).update("name", myName())
    }

    private fun me() = uid()

    /** Tells the room you are typing (at most one write every 2 s); [on] = false clears it at once. */
    fun setTyping(on: Boolean) {
        val s = session.value ?: return
        val me = uid() ?: return
        val now = System.currentTimeMillis()
        if (on) {
            if (now - lastTypingWrite < 2000L) return
            lastTypingWrite = now
        } else {
            if (lastTypingWrite == 0L) return
            lastTypingWrite = 0L
        }
        db().collection("parties").document(s.code).collection("members").document(me)
            .update("typingAt", if (on) now else 0L)
    }

    /** Records the newest chat message you have looked at, so others can see "Seen by". */
    fun markSeen(at: Long) {
        val s = session.value ?: return
        val me = uid() ?: return
        if (at <= lastSeenWritten) return
        lastSeenWritten = at
        db().collection("parties").document(s.code).collection("members").document(me).update("seenAt", at)
    }

    /** Tells the room whether this phone is in the voice call and whether its mic is muted. */
    fun setVoice(on: Boolean, muted: Boolean) {
        val s = session.value ?: return
        val me = uid() ?: return
        db().collection("parties").document(s.code).collection("members").document(me)
            .update(mapOf("voice" to on, "muted" to muted))
    }

    private fun stopListening() {
        PartySocket.stop()
        lastFsEpisode = -1
        lastFsAt = 0L
        partyReg?.remove()
        membersReg?.remove()
        partyReg = null
        membersReg = null
        members.value = emptyList()
        room.value = Room()
        settings.value = Settings()
        typingState.clear()
        knownMembers = null
        lastTypingWrite = 0L
        lastSeenWritten = 0L
        nickname = ""
    }

    /** The host ends the party for everyone; a guest just leaves. */
    fun leave() {
        val s = session.value ?: return
        val me = uid()
        stopListening()
        session.value = null
        remote.value = null
        try {
            if (me != null) db().collection("parties").document(s.code).collection("members").document(me).delete()
            if (s.host) db().collection("parties").document(s.code).delete()
        } catch (_: Exception) {
        }
    }
}
