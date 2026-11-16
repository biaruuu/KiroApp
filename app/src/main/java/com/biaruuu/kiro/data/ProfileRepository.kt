package com.biaruuu.kiro.data

import android.content.Context
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the user chose to show: a username, a short bio and a small profile picture. */
data class Profile(
    val username: String = "",
    val bio: String = "",
    /** The picture as base64 JPEG, or empty when there is none. */
    val avatar: String = "",
    /** Ids of the chosen look (see [Designs]); empty means none. [nameFont] "" is the normal font. */
    val decoration: String = "",
    val effect: String = "",
    val nameplate: String = "",
    val nameFont: String = "",
    /** True once the first answer (cache or server) has arrived. */
    val loaded: Boolean = false,
)

/**
 * The signed-in user's profile, stored in Firestore (users/{uid}) and observed live, so a change
 * made here or on another device shows up everywhere straight away.
 */
object ProfileRepository {

    const val MAX_USERNAME = 20
    const val MIN_USERNAME = 3
    const val MAX_BIO = 150

    private const val PREFS = "kiro_user_data"

    private val _profile = MutableStateFlow(Profile())
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    private var listener: ListenerRegistration? = null
    private var doc: DocumentReference? = null
    private var uid: String? = null

    @JvmStatic
    fun start(context: Context, userId: String) {
        if (uid == userId && listener != null) return
        stop()
        uid = userId
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ref = FirebaseFirestore.getInstance().collection("users").document(userId)
        doc = ref
        listener = ref.addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            // An empty answer from the cache only means "not downloaded yet"; wait for the server.
            if (!snap.exists() && snap.metadata.isFromCache && !prefs.getBoolean("profile_done_$userId", false)) {
                return@addSnapshotListener
            }
            val name = snap.getString("username").orEmpty()
            if (name.isNotBlank()) prefs.edit().putBoolean("profile_done_$userId", true).apply()
            _profile.value = Profile(
                username = name,
                bio = snap.getString("bio").orEmpty(),
                avatar = snap.getString("avatar").orEmpty(),
                decoration = snap.getString("decoration").orEmpty(),
                effect = snap.getString("effect").orEmpty(),
                nameplate = snap.getString("nameplate").orEmpty(),
                nameFont = snap.getString("nameFont").orEmpty(),
                loaded = true,
            )
        }
    }

    @JvmStatic
    fun stop() {
        listener?.remove()
        listener = null
        doc = null
        uid = null
        _profile.value = Profile()
    }

    /** True when this account has already picked a username on this phone. */
    fun knownDone(context: Context, userId: String): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("profile_done_$userId", false)

    fun validUsername(name: String): Boolean =
        name.trim().length in MIN_USERNAME..MAX_USERNAME && name.trim().all { it.isLetterOrDigit() || it == '_' || it == '.' }

    /** Updates only the fields given. [onDone] gets null on success. */
    fun save(
        username: String? = null,
        bio: String? = null,
        avatar: String? = null,
        decoration: String? = null,
        effect: String? = null,
        nameplate: String? = null,
        nameFont: String? = null,
        onDone: (String?) -> Unit = {}) {
        val ref = doc
        if (ref == null) {
            onDone("You are signed out.")
            return
        }
        val data = HashMap<String, Any>()
        username?.let { data["username"] = it.trim() }
        bio?.let { data["bio"] = it.trim() }
        avatar?.let { data["avatar"] = it }
        decoration?.let { data["decoration"] = it }
        effect?.let { data["effect"] = it }
        nameplate?.let { data["nameplate"] = it }
        nameFont?.let { data["nameFont"] = it }
        data["updatedAt"] = System.currentTimeMillis()
        ref.set(data, SetOptions.merge()).addOnCompleteListener { t ->
            onDone(if (t.isSuccessful) null else "Couldn't save. Check your connection and try again.")
        }
        // The local snapshot fires right away, so the UI already shows the new values.
    }
}
