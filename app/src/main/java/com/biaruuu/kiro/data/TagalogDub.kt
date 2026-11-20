package com.biaruuu.kiro.data

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.TimeUnit

/**
 * The Tagalog dub library. The developer adds episodes from the admin panel (an AniList anime, an
 * episode number and a video address); they are saved in Firestore under "tagdub/{anilistId}_{episode}"
 * and the player offers them as a "Tagalog Dub" source for that exact anime and episode.
 */
object TagalogDub {
    data class Entry(
        val id: String,
        val anilistId: Int,
        val title: String,
        val poster: String,
        val episode: Int,
        val url: String,
        val quality: Int,
        val referer: String,
        val createdAt: Long,
    )

    private const val COLLECTION = "tagdub"
    val entries = MutableStateFlow<List<Entry>>(emptyList())

    fun docId(animeId: Int, episode: Int) = "${animeId}_$episode"

    private fun parse(d: com.google.firebase.firestore.DocumentSnapshot): Entry? {
        val url = d.getString("url").orEmpty()
        val anime = (d.getLong("anilistId") ?: return null).toInt()
        if (!url.startsWith("http")) return null
        return Entry(
            d.id, anime, d.getString("title").orEmpty(), d.getString("poster").orEmpty(),
            (d.getLong("episode") ?: return null).toInt(), url, (d.getLong("quality") ?: 0L).toInt(),
            d.getString("referer").orEmpty(), d.getLong("createdAt") ?: 0L,
        )
    }

    /** Live list for the admin panel; call the returned function to stop listening. */
    fun listen(): () -> Unit {
        val reg = FirebaseFirestore.getInstance().collection(COLLECTION)
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(300)
            .addSnapshotListener { snap, _ ->
                if (snap != null) entries.value = snap.documents.mapNotNull { parse(it) }
            }
        return { reg.remove() }
    }

    fun publish(entry: Entry, done: (String?) -> Unit) {
        val id = docId(entry.anilistId, entry.episode)
        FirebaseFirestore.getInstance().collection(COLLECTION).document(id).set(
            hashMapOf(
                "anilistId" to entry.anilistId, "title" to entry.title, "poster" to entry.poster,
                "episode" to entry.episode, "url" to entry.url, "quality" to entry.quality,
                "referer" to entry.referer, "createdAt" to System.currentTimeMillis(),
            ),
        ).addOnSuccessListener { done(null) }.addOnFailureListener { done(it.message ?: "failed") }
    }

    fun delete(id: String) {
        FirebaseFirestore.getInstance().collection(COLLECTION).document(id).delete()
    }

    /** Blocking lookup for the stream providers (they already run off the main thread). */
    fun lookup(animeId: Int, episode: Int): Entry? = try {
        val snap = Tasks.await(FirebaseFirestore.getInstance().collection(COLLECTION).document(docId(animeId, episode)).get(), 10, TimeUnit.SECONDS)
        if (snap.exists()) parse(snap) else null
    } catch (_: Exception) {
        null
    }
}
