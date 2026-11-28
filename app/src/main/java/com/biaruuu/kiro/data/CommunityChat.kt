package com.biaruuu.kiro.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.biaruuu.kiro.data.auth.AuthRepository
import com.biaruuu.kiro.data.auth.AuthState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * The Community chat. Messages travel over the live socket (instant for everyone who has the chat open,
 * plus typing and @mention pop-ups) and are also saved in Firestore ("community") so the history is there
 * when someone opens the chat later. Only a username, avatar and avatar decoration are shared.
 */
object CommunityChat {
    class Msg(
        val id: String,
        val uid: String,
        val name: String,
        val avatar: String,
        val decoration: String,
        val nameFont: String,
        val text: String,
        /** A small JPEG as a data address ("data:image/jpeg;base64,..."), or empty. */
        val image: String,
        val at: Long,
        val dev: Boolean,
        val everyone: Boolean,
        val mentions: List<String>,
    )

    private const val COLLECTION = "community"
    private const val KEEP = 120
    private const val MAX_IMAGE_BYTES = 130_000

    val messages = MutableStateFlow<List<Msg>>(emptyList())

    /** Who is typing right now: name to the time that stops being true. */
    val typing = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** An @mention (or an @everyone from the developer) waiting to be shown as a pop-up. */
    val popup = MutableStateFlow<Msg?>(null)

    /** A mention arrived while the chat was closed: the Community tab shows a dot until it is opened. */
    val unreadMention = MutableStateFlow(false)

    val isOpen = MutableStateFlow(false)

    /** Raised by a pop-up: the app switches to the Community tab. */
    val openRequest = MutableStateFlow(0)

    private var reg: ListenerRegistration? = null
    private var fs: List<Msg> = emptyList()
    private val live = LinkedHashMap<String, Msg>()
    private val gone = HashSet<String>()
    private var lastTypingSent = 0L
    private var lastPopupAt = 0L

    // ------------------------------------------------------------------ who I am
    private fun me(): Triple<String, Profile, Boolean>? {
        val s = AuthRepository.state.value as? AuthState.SignedIn ?: return null
        val p = ProfileRepository.profile.value
        if (p.username.isBlank() || s.uid.isBlank()) return null
        return Triple(s.uid, p, Admin.isAdmin(s))
    }

    fun myUid(): String = me()?.first.orEmpty()

    val myName: String get() = me()?.second?.username.orEmpty()
    val isDev: Boolean get() = me()?.third == true

    private fun token(context: Context): String =
        context.applicationContext.getSharedPreferences("kiro_global", Context.MODE_PRIVATE).getString("admin_token", "").orEmpty()

    /** Tells the server who this phone is (needed for @mentions to find it). Safe to call again. */
    fun identify() {
        val (uid, p, _) = me() ?: return
        GlobalSocket.sendJson(
            JSONObject().put("type", "chat_join").put("uid", uid).put("name", p.username)
                .put("avatar", p.avatar).put("decoration", p.decoration).put("nameFont", p.nameFont),
        )
        if (isOpen.value) GlobalSocket.sendJson(JSONObject().put("type", "chat_open").put("open", true))
    }

    // ------------------------------------------------------------------ open / close
    fun setOpen(open: Boolean) {
        isOpen.value = open
        GlobalSocket.sendJson(JSONObject().put("type", "chat_open").put("open", open))
        if (open) {
            unreadMention.value = false
            popup.value = null
            listen()
        } else {
            reg?.remove()
            reg = null
        }
    }

    private fun listen() {
        if (reg != null) return
        reg = FirebaseFirestore.getInstance().collection(COLLECTION)
            .orderBy("at", Query.Direction.ASCENDING).limitToLast(KEEP.toLong())
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val list = snap.documents.mapNotNull { d ->
                    val text = d.getString("text").orEmpty()
                    val image = d.getString("img").orEmpty()
                    if (text.isEmpty() && image.isEmpty()) return@mapNotNull null
                    Msg(
                        d.id, d.getString("uid") ?: return@mapNotNull null, d.getString("name").orEmpty().ifBlank { "Guest" },
                        d.getString("avatar").orEmpty(), d.getString("decoration").orEmpty(), d.getString("nameFont").orEmpty(),
                        text, image, d.getLong("at") ?: 0L, d.getBoolean("dev") == true, d.getBoolean("everyone") == true,
                        (d.get("mentions") as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
                    )
                }
                synchronized(this) {
                    fs = list
                    gone.retainAll(list.map { it.id }.toSet())
                    publish()
                }
            }
    }

    @Synchronized
    private fun publish() {
        live.keys.removeAll(fs.map { it.id }.toSet())
        messages.value = (fs.filter { it.id !in gone } + live.values.filter { it.id !in gone }).sortedBy { it.at }.takeLast(KEEP)
    }

    // ------------------------------------------------------------------ sending
    private val mentionRegex = Regex("@([A-Za-z0-9_.]{3,20})")

    fun mentionsOf(text: String): List<String> = mentionRegex.findAll(text).map { it.groupValues[1] }.distinct().toList()

    fun mentionsMe(m: Msg): Boolean {
        val n = myName
        return m.everyone || (n.isNotEmpty() && m.mentions.any { it.equals(n, ignoreCase = true) })
    }

    /** [done] gets null when sent, or the reason it could not be. */
    fun send(context: Context, text: String, image: String, done: (String?) -> Unit) {
        val (uid, p, admin) = me() ?: return done("Sign in and choose a username to chat.")
        val body = text.trim().take(1000)
        if (body.isEmpty() && image.isEmpty()) return done(null)
        val db = FirebaseFirestore.getInstance()
        val id = db.collection(COLLECTION).document().id
        val tok = if (admin) token(context) else ""
        val dev = admin && tok.isNotEmpty()
        val everyone = dev && Regex("(^|\\s)@everyone\\b", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val mentions = mentionsOf(body).filter { !it.equals("everyone", true) }
        val at = System.currentTimeMillis()
        val msg = Msg(id, uid, p.username, p.avatar, p.decoration, p.nameFont, body, image, at, dev, everyone, mentions)
        synchronized(this) { live[id] = msg; publish() }

        val ws = JSONObject().put("type", "chat_send").put("id", id).put("text", body)
        if (image.isNotEmpty()) ws.put("image", image)
        if (dev) ws.put("token", tok)
        GlobalSocket.sendJson(ws)

        val data = hashMapOf<String, Any>(
            "uid" to uid, "name" to p.username, "avatar" to p.avatar, "decoration" to p.decoration, "nameFont" to p.nameFont,
            "text" to body, "at" to at, "dev" to dev, "everyone" to everyone, "mentions" to mentions,
        )
        if (image.isNotEmpty()) data["img"] = image
        db.collection(COLLECTION).document(id).set(data)
            .addOnSuccessListener { done(null) }
            .addOnFailureListener {
                synchronized(this) { live.remove(id); gone.add(id); publish() }
                done(it.message ?: "Couldn't send")
            }
    }

    fun typingPing() {
        val now = System.currentTimeMillis()
        if (now - lastTypingSent < 2500) return
        lastTypingSent = now
        GlobalSocket.sendJson(JSONObject().put("type", "chat_typing"))
    }

    fun canDelete(context: Context, m: Msg): Boolean = isDev || me()?.first == m.uid

    fun delete(context: Context, m: Msg) {
        synchronized(this) { gone.add(m.id); live.remove(m.id); publish() }
        FirebaseFirestore.getInstance().collection(COLLECTION).document(m.id).delete()
        if (isDev) {
            val t = token(context)
            if (t.isNotEmpty()) GlobalSocket.sendJson(JSONObject().put("type", "chat_delete").put("id", m.id).put("token", t))
        }
    }

    // ------------------------------------------------------------------ from the socket
    fun onConnected() = identify()

    private fun parse(m: JSONObject): Msg {
        val arr: JSONArray? = m.optJSONArray("mentions")
        val mentions = (0 until (arr?.length() ?: 0)).map { arr!!.optString(it) }
        return Msg(
            m.optString("id"), m.optString("uid"), m.optString("name"), m.optString("avatar"), m.optString("decoration"),
            m.optString("nameFont"), m.optString("text"), m.optString("image"), m.optLong("at", System.currentTimeMillis()),
            m.optBoolean("dev"), m.optBoolean("everyone"), mentions,
        )
    }

    fun onSocket(type: String, m: JSONObject) {
        when (type) {
            "chat_msg" -> {
                val msg = parse(m)
                synchronized(this) { live[msg.id] = msg; publish() }
                typing.value = typing.value - msg.name
            }
            "chat_mention" -> {
                val msg = parse(m)
                if (isOpen.value) return
                unreadMention.value = true
                val now = System.currentTimeMillis()
                // One pop-up at a time, and a plain @name does not repeat within 8 seconds. An @everyone always shows.
                if (msg.everyone || now - lastPopupAt > 8_000) {
                    lastPopupAt = now
                    popup.value = msg
                }
            }
            "chat_typing" -> typing.value = typing.value.filterValues { it > System.currentTimeMillis() } +
                (m.optString("name") to System.currentTimeMillis() + 5_000)
            "chat_deleted" -> synchronized(this) { gone.add(m.optString("id")); live.remove(m.optString("id")); publish() }
        }
    }

    fun dismissPopup() { popup.value = null }

    fun requestOpen() { openRequest.value = openRequest.value + 1 }

    // ------------------------------------------------------------------ photos
    /** Shrinks a picked photo to a small JPEG (about 1000 px, at most ~130 KB) and returns it as a data address, or null. */
    fun prepareImage(context: Context, uri: Uri): String? = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > 2000) sample *= 2
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        var edge = 1000
        var result: ByteArray? = null
        while (edge >= 400 && result == null) {
            val scale = minOf(1f, edge.toFloat() / maxOf(raw.width, raw.height))
            val bmp = if (scale < 1f) Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt().coerceAtLeast(1), (raw.height * scale).toInt().coerceAtLeast(1), true) else raw
            for (q in intArrayOf(78, 66, 54)) {
                val out = ByteArrayOutputStream()
                // A flat white background so transparent pictures do not turn black.
                val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                android.graphics.Canvas(flat).also { it.drawColor(android.graphics.Color.WHITE); it.drawBitmap(bmp, 0f, 0f, null) }
                flat.compress(Bitmap.CompressFormat.JPEG, q, out)
                if (out.size() <= MAX_IMAGE_BYTES) { result = out.toByteArray(); break }
            }
            edge -= 200
        }
        result?.let { "data:image/jpeg;base64," + Base64.encodeToString(it, Base64.NO_WRAP) }
    } catch (_: Throwable) {
        null
    }
}
