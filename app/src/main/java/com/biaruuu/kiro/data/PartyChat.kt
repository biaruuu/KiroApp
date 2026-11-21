package com.biaruuu.kiro.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Live text chat for a watch party: messages live in `parties/{code}/messages`. Firestore shows a
 * message you send to yourself immediately and delivers it to the others as soon as it reaches the
 * server, so it feels instant.
 */
object PartyChat {
    class Msg(val id: String, val uid: String, val name: String, val avatar: String, val decoration: String, val text: String, val at: Long)

    val messages = MutableStateFlow<List<Msg>>(emptyList())

    private var reg: ListenerRegistration? = null
    private var code: String? = null

    /** Starts listening for [newCode] (or stops when null). Safe to call again with the same code. */
    fun listen(newCode: String?) {
        if (newCode == code) return
        reg?.remove()
        reg = null
        code = newCode
        messages.value = emptyList()
        if (newCode == null) return
        reg = FirebaseFirestore.getInstance().collection("parties").document(newCode).collection("messages")
            .orderBy("at", Query.Direction.ASCENDING).limitToLast(150)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                messages.value = snap.documents.mapNotNull { d ->
                    Msg(
                        d.id, d.getString("uid") ?: return@mapNotNull null, d.getString("name").orEmpty().ifBlank { "Guest" },
                        d.getString("avatar").orEmpty(), d.getString("decoration").orEmpty(),
                        d.getString("text") ?: return@mapNotNull null, d.getLong("at") ?: 0L,
                    )
                }
            }
    }

    fun send(text: String) {
        val c = code ?: return
        val me = WatchParty.uid() ?: return
        val clean = text.trim().take(500)
        if (clean.isEmpty()) return
        val p = ProfileRepository.profile.value
        FirebaseFirestore.getInstance().collection("parties").document(c).collection("messages").add(
            mapOf(
                "uid" to me, "name" to WatchParty.myName(), "avatar" to p.avatar, "decoration" to p.decoration,
                "text" to clean, "at" to System.currentTimeMillis(),
            ),
        )
    }
}
