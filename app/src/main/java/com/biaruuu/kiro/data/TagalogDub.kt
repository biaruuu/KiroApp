package com.biaruuu.kiro.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.CountDownLatch
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

    /** One card per anime that has at least one Filipino dub episode, newest first. Live: a new dub appears here at once. */
    val shows = MutableStateFlow<List<Anime>>(emptyList())

    private fun toShows(all: Collection<Entry>): List<Anime> =
        all.groupBy { it.anilistId }.values
            .sortedByDescending { list -> list.maxOf { it.createdAt } }
            .map { list ->
                val e = list.maxByOrNull { it.createdAt }!!
                val poster = list.firstOrNull { it.poster.isNotBlank() }?.poster
                Anime(
                    e.anilistId, e.title, e.title, null, poster, poster, null, null, null,
                    0, list.maxOf { it.episode }, null, 0, emptyList(), null,
                )
            }

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
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(1500)
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
            com.google.firebase.firestore.SetOptions.merge(),
        ).addOnSuccessListener { done(null) }.addOnFailureListener { done(it.message ?: "failed") }
    }

    fun delete(id: String) {
        FirebaseFirestore.getInstance().collection(COLLECTION).document(id).delete()
    }

    /** Removes several episodes at once (one request per 400). */
    fun deleteAll(ids: List<String>) {
        val db = FirebaseFirestore.getInstance()
        ids.chunked(400).forEach { chunk ->
            val b = db.batch()
            chunk.forEach { b.delete(db.collection(COLLECTION).document(it)) }
            b.commit()
        }
    }

    /** Saves many episodes in as few requests as possible; [done] gets the number saved, or an error. */
    fun publishAll(entries: List<Entry>, done: (Int, String?) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val now = System.currentTimeMillis()
        val chunks = entries.chunked(400)
        var saved = 0
        fun next(i: Int) {
            if (i >= chunks.size) { done(saved, null); return }
            val b = db.batch()
            chunks[i].forEachIndexed { n, e ->
                b.set(
                    db.collection(COLLECTION).document(docId(e.anilistId, e.episode)),
                    hashMapOf(
                        "anilistId" to e.anilistId, "title" to e.title, "poster" to e.poster,
                        "episode" to e.episode, "url" to e.url, "quality" to e.quality,
                        "referer" to e.referer, "createdAt" to now - (entries.size - (i * 400 + n)),
                    ),
                    com.google.firebase.firestore.SetOptions.merge(),
                )
            }
            b.commit().addOnSuccessListener { saved += chunks[i].size; next(i + 1) }
                .addOnFailureListener { done(saved, it.message ?: "failed") }
        }
        next(0)
    }

    /** A pasted line, resolved to an episode number. */
    data class Parsed(val episode: Int, val url: String)

    class ParseResult(
        val items: List<Parsed>,
        val invalid: Int,
        val duplicates: Int,
        /** Episodes missing between the first and last one (when numbers came from the links). */
        val gaps: List<Int>,
    )

    private val epRegex = Regex("(?i)(?:episode|eps|ep)[\\s._-]*0*(\\d{1,4})(?!\\d)")

    /**
     * One link per line. The episode number is read from the link ("... Episode 001 ..."); a link
     * without one continues from the previous episode (or [start] for the first).
     */
    fun parseLinks(text: String, start: Int): ParseResult {
        val seen = LinkedHashMap<Int, String>()
        var invalid = 0
        var dup = 0
        var last = start - 1
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (!line.startsWith("http")) { invalid++; continue }
            val decoded = try { java.net.URLDecoder.decode(line.substringAfterLast('/'), "UTF-8") } catch (_: Exception) { line }
            val ep = epRegex.findAll(decoded).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 } ?: (last + 1)
            if (seen.containsKey(ep)) { dup++; continue }
            seen[ep] = line
            last = ep
        }
        val items = seen.entries.sortedBy { it.key }.map { Parsed(it.key, it.value) }
        val eps = items.map { it.episode }.toSet()
        val gaps = if (items.size < 2) emptyList() else (items.first().episode..items.last().episode).filter { it !in eps }
        return ParseResult(items, invalid, dup, gaps)
    }

    private fun norm(s: String?) = s.orEmpty().lowercase().replace(Regex("[^a-z0-9]"), "")

    // ---- Live copy of the library. A realtime listener keeps it current (new and removed dubs arrive
    // the moment the developer saves them), so looking one up while a stream is resolved costs nothing.
    @Volatile private var index: Map<String, Entry> = emptyMap()
    @Volatile private var live: com.google.firebase.firestore.ListenerRegistration? = null
    private val firstSnapshot = CountDownLatch(1)

    fun init(c: android.content.Context) {
        warm()
    }

    /** Starts the live listener (safe to call many times). */
    fun warm() {
        if (live != null) return
        synchronized(this) {
            if (live != null) return
            live = FirebaseFirestore.getInstance().collection(COLLECTION).limit(2000)
                .addSnapshotListener { snap, err ->
                    if (snap != null) {
                        val m = HashMap<String, Entry>()
                        for (d in snap.documents) parse(d)?.let { m[d.id] = it }
                        index = m
                        shows.value = toShows(m.values)
                        firstSnapshot.countDown()
                    } else if (err != null) {
                        firstSnapshot.countDown()
                    }
                }
        }
    }

    /**
     * Instant lookup for the stream providers. The exact AniList id wins; failing that, an entry for the
     * same episode whose saved title matches one of [titles] is used (so a dub added under another entry
     * of the same show still plays). On a cold start it waits (at most 2 seconds) for the first snapshot.
     */
    fun lookup(animeId: Int, episode: Int, titles: List<String> = emptyList()): Entry? {
        warm()
        if (index.isEmpty()) try { firstSnapshot.await(2, TimeUnit.SECONDS) } catch (_: InterruptedException) { }
        val all = index
        all[docId(animeId, episode)]?.let { return it }
        val wanted = titles.map { norm(it) }.filter { it.length >= 4 }
        if (wanted.isEmpty()) return null
        return all.values.firstOrNull { e ->
            if (e.episode != episode) return@firstOrNull false
            val t = norm(e.title)
            t.isNotEmpty() && wanted.any { it == t || t.startsWith(it) || it.startsWith(t) }
        }
    }
}
