package com.biaruuu.kiro.data

import com.biaruuu.kiro.BuildConfig
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow

/** Tells the developer (and only the developer) that a signed-in user is online and what they are watching. */
object Presence {
    data class Watching(val animeId: Int, val title: String, val episode: Int)

    val watching = MutableStateFlow<Watching?>(null)

    fun write(uid: String, online: Boolean) {
        val w = watching.value
        val data = hashMapOf<String, Any?>(
            "username" to ProfileRepository.profile.value.username.ifBlank { "User" },
            "online" to online,
            "lastSeen" to System.currentTimeMillis(),
            "version" to BuildConfig.VERSION_NAME,
            "watching" to if (online && w != null) {
                mapOf("animeId" to w.animeId, "title" to w.title, "episode" to w.episode)
            } else null,
        )
        FirebaseFirestore.getInstance().collection("presence").document(uid).set(data)
    }
}
