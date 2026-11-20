package com.biaruuu.kiro.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * An opt-in, read-only profile that anyone with the link can open: name, picture, level, streak,
 * badges and the shows you rated highest. Stored at publicProfiles/{uid}; switching it off deletes it.
 */
object PublicProfile {
    class Fav(val id: Int, val title: String, val poster: String)

    class View(
        val uid: String,
        val name: String,
        val bio: String,
        val avatar: String,
        val decoration: String,
        val nameFont: String,
        val level: Int,
        val streak: Int,
        val badges: List<String>,
        val favorites: List<Fav>,
    )

    private const val FILE = "kiro_user_data"
    private const val FLAG = "public_profile"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private fun uid(): String? = FirebaseAuth.getInstance().currentUser?.uid
    private fun doc(uid: String) = FirebaseFirestore.getInstance().collection("publicProfiles").document(uid)

    fun enabled(c: Context): Boolean = prefs(c).getBoolean(FLAG, false) && uid() != null

    fun link(): String? = uid()?.let { DeepLink.profileLink(it) }

    fun setEnabled(c: Context, on: Boolean, done: (Boolean) -> Unit = {}) {
        val me = uid() ?: return done(false)
        prefs(c).edit().putBoolean(FLAG, on).apply()
        if (on) publish(c, done) else doc(me).delete().addOnCompleteListener { done(it.isSuccessful) }
    }

    /** Re-sends the current numbers when the profile is on; cheap, so it runs at app start. */
    fun publishIfEnabled(c: Context) {
        if (enabled(c)) publish(c)
    }

    private fun publish(c: Context, done: (Boolean) -> Unit = {}) {
        val me = uid() ?: return done(false)
        val p = ProfileRepository.profile.value
        val favs = MyListStore.allRated(c).sortedByDescending { it.rating }.take(6).map {
            hashMapOf<String, Any>("id" to it.id, "title" to it.title, "poster" to it.posterUrl)
        }
        val data = hashMapOf<String, Any>(
            "name" to p.username.ifBlank { "Kiro user" },
            "bio" to p.bio,
            "avatar" to p.avatar,
            "decoration" to p.decoration,
            "nameFont" to p.nameFont,
            "level" to WatchStats.levelNow(c),
            "streak" to WatchStats.streak(c).count,
            "badges" to Achievements.unlockedIds(c),
            "favorites" to favs,
            "updatedAt" to System.currentTimeMillis(),
        )
        doc(me).set(data).addOnCompleteListener { done(it.isSuccessful) }
    }

    fun load(uid: String, done: (View?) -> Unit) {
        doc(uid).get().addOnSuccessListener { s ->
            if (!s.exists()) return@addOnSuccessListener done(null)
            @Suppress("UNCHECKED_CAST")
            val favs = (s.get("favorites") as? List<Map<String, Any?>>).orEmpty().mapNotNull {
                val id = (it["id"] as? Number)?.toInt() ?: return@mapNotNull null
                Fav(id, it["title"] as? String ?: "", it["poster"] as? String ?: "")
            }
            @Suppress("UNCHECKED_CAST")
            val badges = (s.get("badges") as? List<String>).orEmpty()
            done(
                View(
                    uid, s.getString("name").orEmpty(), s.getString("bio").orEmpty(), s.getString("avatar").orEmpty(),
                    s.getString("decoration").orEmpty(), s.getString("nameFont").orEmpty(),
                    (s.getLong("level") ?: 1L).toInt(), (s.getLong("streak") ?: 0L).toInt(), badges, favs,
                ),
            )
        }.addOnFailureListener { done(null) }
    }
}
