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
    class Member(val uid: String, val name: String, val avatar: String, val decoration: String, val nameFont: String, val host: Boolean)

    val members = MutableStateFlow<List<Member>>(emptyList())

    /** One-shot message for the player to show (for example "The host ended the party"). */
    val message = MutableStateFlow<String?>(null)

    private var partyReg: ListenerRegistration? = null
    private var membersReg: ListenerRegistration? = null

    private fun db() = FirebaseFirestore.getInstance()
    internal fun uid() = (AuthRepository.state.value as? AuthState.SignedIn)?.uid
    internal fun myName() = ProfileRepository.profile.value.username.ifBlank { "Guest" }

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
    fun host(c: Context, animeId: Int, episode: Int, title: String, onDone: (String?, String?) -> Unit) {
        val me = uid() ?: return onDone(null, "Log in to start a watch party")
        val code = newCode()
        if (PartySocket.enabled) {
            PartySocket.host(me, code, animeId, episode, title) { err ->
                if (err != null) onDone(null, err)
                else {
                    session.value = Session(code, true)
                    remote.value = null
                    Achievements.bump(c, "hosted")
                    onDone(code, null)
                }
            }
            return
        }
        val data = hashMapOf<String, Any>(
            "hostUid" to me, "hostName" to myName(), "animeId" to animeId, "episode" to episode,
            "title" to title, "positionMs" to 0L, "playing" to false, "createdAt" to System.currentTimeMillis(),
            "sentAt" to System.currentTimeMillis(),
        )
        db().collection("parties").document(code).set(data)
            .addOnSuccessListener {
                session.value = Session(code, true)
                writeMember(code, me, true)
                Achievements.bump(c, "hosted")
                listenMembers(code)
                onDone(code, null)
            }
            .addOnFailureListener { onDone(null, friendly(it)) }
    }

    /** Looks the code up (without joining yet); [onDone] gets the party's current state, or an error message. */
    fun lookup(code: String, onDone: (Remote?, String?) -> Unit) {
        if (uid() == null) return onDone(null, "Log in to join a watch party")
        if (PartySocket.enabled) return PartySocket.lookup(code, onDone)
        db().collection("parties").document(code.trim().uppercase()).get()
            .addOnSuccessListener { snap ->
                val r = if (snap.exists()) toRemote(snap.data) else null
                if (r == null) onDone(null, "No watch party with that code") else onDone(r, null)
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
        if (PartySocket.enabled) return PartySocket.join(me, code)
        writeMember(code, me, false)
        partyReg = db().collection("parties").document(code).addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            if (!snap.exists()) {
                if (!snap.metadata.isFromCache) {
                    message.value = "The host ended the watch party"
                    stopListening()
                    session.value = null
                }
                return@addSnapshotListener
            }
            toRemote(snap.data)?.let { remote.value = it }
        }
        listenMembers(code)
    }

    private fun listenMembers(code: String) {
        membersReg?.remove()
        membersReg = db().collection("parties").document(code).collection("members")
            .addSnapshotListener { snap, _ ->
                members.value = snap?.documents?.mapNotNull { d ->
                    Member(
                        d.id, d.getString("name") ?: return@mapNotNull null, d.getString("avatar").orEmpty(),
                        d.getString("decoration").orEmpty(), d.getString("nameFont").orEmpty(), d.getBoolean("host") ?: false,
                    )
                }?.sortedWith(compareByDescending<Member> { it.host }.thenBy { it.name.lowercase() }) ?: emptyList()
            }
    }

    /** The host shares where they are; guests catch up to it. [playing] means actually running, not buffering. */
    fun push(animeId: Int, episode: Int, positionMs: Long, playing: Boolean) {
        val s = session.value ?: return
        if (!s.host) return
        if (PartySocket.enabled) return PartySocket.push(animeId, episode, positionMs, playing)
        db().collection("parties").document(s.code).update(
            mapOf(
                "animeId" to animeId, "episode" to episode, "positionMs" to positionMs, "playing" to playing,
                "sentAt" to System.currentTimeMillis(),
            ),
        )
    }

    private fun stopListening() {
        partyReg?.remove()
        membersReg?.remove()
        partyReg = null
        membersReg = null
        members.value = emptyList()
    }

    /** The host ends the party for everyone; a guest just leaves. */
    fun leave(local: Boolean = false) {
        val s = session.value ?: return
        val me = uid()
        stopListening()
        session.value = null
        remote.value = null
        if (PartySocket.enabled) {
            if (s.host && !local) PartySocket.end()
            PartySocket.close()
            members.value = emptyList()
            return
        }
        try {
            if (me != null) db().collection("parties").document(s.code).collection("members").document(me).delete()
            if (s.host) db().collection("parties").document(s.code).delete()
        } catch (_: Exception) {
        }
    }
}
