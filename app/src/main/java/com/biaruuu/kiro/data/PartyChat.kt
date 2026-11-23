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
    class Msg(val id: String, val uid: String, val name: String, val avatar: String, val decoration: String, val text: String, val at: Long, val edited: Boolean = false)

    val messages = MutableStateFlow<List<Msg>>(emptyList())

    private var reg: ListenerRegistration? = null
    private var code: String? = null

    // What the database last said, plus what arrived (or went) through the live socket before the database caught up.
    private var fs: List<Msg> = emptyList()
    private val live = LinkedHashMap<String, Msg>()
    private val gone = HashSet<String>()

    @Synchronized
    private fun publish() {
        val have = fs.map { it.id }.toSet()
        live.keys.removeAll(have)
        val all = (fs.filter { it.id !in gone } + live.values.filter { it.id !in gone }).sortedBy { it.at }
        messages.value = all.takeLast(150)
    }

    /** Starts listening for [newCode] (or stops when null). Safe to call again with the same code. */
    fun listen(newCode: String?) {
        if (newCode == code) return
        reg?.remove()
        reg = null
        code = newCode
        synchronized(PartyChat) { fs = emptyList(); live.clear(); gone.clear() }
        messages.value = emptyList()
        if (newCode == null) return
        reg = FirebaseFirestore.getInstance().collection("parties").document(newCode).collection("messages")
            .orderBy("at", Query.Direction.ASCENDING).limitToLast(150)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val list = snap.documents.mapNotNull { d ->
                    Msg(
                        d.id, d.getString("uid") ?: return@mapNotNull null, d.getString("name").orEmpty().ifBlank { "Guest" },
                        d.getString("avatar").orEmpty(), d.getString("decoration").orEmpty(),
                        d.getString("text") ?: return@mapNotNull null, d.getLong("at") ?: 0L,
                        d.getBoolean("edited") == true,
                    )
                }
                synchronized(PartyChat) {
                    fs = list
                    // A deletion the database has now caught up with no longer needs remembering.
                    val have = list.map { it.id }.toSet()
                    gone.retainAll(have)
                }
                publish()
            }
    }

    /** A message that came through the live socket (arrives before the database copy). */
    fun onLive(o: org.json.JSONObject) {
        val id = o.optString("id")
        val uid = o.optString("from")
        val text = o.optString("text")
        if (id.isBlank() || uid.isBlank() || text.isBlank()) return
        synchronized(PartyChat) {
            if (fs.any { it.id == id }) return
            live[id] = Msg(
                id, uid, o.optString("name").ifBlank { "Guest" }, o.optString("avatar"), o.optString("decoration"),
                text, o.optLong("at", System.currentTimeMillis()), o.optBoolean("edited", false),
            )
        }
        publish()
    }

    fun onGone(id: String) {
        if (id.isBlank()) return
        synchronized(PartyChat) { gone.add(id); live.remove(id) }
        publish()
    }

    private fun col(c: String) = FirebaseFirestore.getInstance().collection("parties").document(c).collection("messages")

    /** Removes one of your own messages for everyone in the room. */
    fun delete(id: String) {
        val c = code ?: return
        synchronized(PartyChat) { gone.add(id); live.remove(id) }
        publish()
        PartySocket.sendRaw(org.json.JSONObject().put("type", "chat_delete").put("id", id))
        col(c).document(id).delete()
    }

    /**
     * Changes one of your own messages. It is written again with the same time (so it keeps its place)
     * and the old one is removed in the same step, which the existing Firestore rules already allow.
     */
    fun edit(m: Msg, newText: String) {
        val c = code ?: return
        if (m.uid != WatchParty.uid()) return
        val clean = newText.trim().take(500)
        if (clean.isEmpty() || clean == m.text) return
        val col = col(c)
        val newDoc = col.document()
        synchronized(PartyChat) { gone.add(m.id); live.remove(m.id) }
        PartySocket.sendRaw(org.json.JSONObject().put("type", "chat_delete").put("id", m.id))
        PartySocket.sendRaw(
            org.json.JSONObject().put("type", "chat").put("id", newDoc.id).put("name", m.name).put("avatar", m.avatar)
                .put("decoration", m.decoration).put("text", clean).put("at", m.at).put("edited", true),
        )
        val batch = FirebaseFirestore.getInstance().batch()
        batch.set(
            newDoc,
            mapOf(
                "uid" to m.uid, "name" to m.name, "avatar" to m.avatar, "decoration" to m.decoration,
                "text" to clean, "at" to m.at, "edited" to true,
            ),
        )
        batch.delete(col.document(m.id))
        batch.commit()
        publish()
    }

    fun send(text: String) {
        val c = code ?: return
        val me = WatchParty.uid() ?: return
        val clean = text.trim().take(500)
        if (clean.isEmpty()) return
        val p = ProfileRepository.profile.value
        val doc = col(c).document()
        val at = System.currentTimeMillis()
        // Both at once: the live socket gets it to everyone now, the database keeps it for later.
        PartySocket.sendRaw(
            org.json.JSONObject().put("type", "chat").put("id", doc.id).put("name", WatchParty.myName())
                .put("avatar", p.avatar).put("decoration", p.decoration).put("text", clean).put("at", at),
        )
        doc.set(
            mapOf(
                "uid" to me, "name" to WatchParty.myName(), "avatar" to p.avatar, "decoration" to p.decoration,
                "text" to clean, "at" to at,
            ),
        )
    }
}
