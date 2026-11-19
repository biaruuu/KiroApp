package com.biaruuu.kiro.data

import android.content.Context
import com.biaruuu.kiro.data.auth.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A look users can pick: an avatar decoration, a library effect or a library decoration. Built-in
 * ones ship inside the app ([asset]); the developer publishes more from the admin panel and they
 * appear for everyone straight away. Published images are saved on the phone ([file]) after the
 * first download.
 */
data class Design(
    val id: String,
    val type: String,
    val name: String,
    val asset: String? = null,
    val file: String? = null,
    val bytes: ByteArray? = null,
    /** Level needed to use it; 0 = worked out from its place in the list (see [Designs.requiredLevel]). */
    val level: Int = 0,
)

object Designs {
    const val DECORATION = "decoration"
    const val LIBRARY_EFFECT = "library_effect"
    const val LIBRARY_DECORATION = "library_decoration"

    /** Largest image the admin panel accepts. It is stored in pieces because one Firestore document holds only 1 MB. */
    const val MAX_BYTES = 5 * 1024 * 1024
    const val PART_BYTES = 500_000

    private val builtIn = listOf(
        Design("cat_ears", DECORATION, "Cat Ears", asset = "decorations/cat_ears.png"),
    )

    val remote = MutableStateFlow<List<Design>>(emptyList())

    fun all(type: String): List<Design> = builtIn.filter { it.type == type } + remote.value.filter { it.type == type }

    private val UNLOCK_LEVELS = listOf(3, 6, 10, 15, 20, 25, 30, 40, 50)

    /**
     * Avatar decorations are earned: the first needs level 3, then 6, 10, 15, 20, 25, 30, 40, 50 and
     * every 10 levels after that, in the order they were added. A decoration can also carry its own level.
     */
    fun requiredLevel(d: Design): Int {
        if (d.type != DECORATION) return 1
        if (d.level > 0) return d.level
        val i = all(DECORATION).indexOfFirst { it.id == d.id }.coerceAtLeast(0)
        return UNLOCK_LEVELS.getOrElse(i) { UNLOCK_LEVELS.last() + 10 * (i - UNLOCK_LEVELS.size + 1) }
    }

    fun find(id: String?): Design? =
        if (id.isNullOrEmpty()) null else builtIn.firstOrNull { it.id == id } ?: remote.value.firstOrNull { it.id == id }

    /** Saves a new design: the details in one document, the image in numbered parts next to it. */
    fun publish(type: String, name: String, bytes: ByteArray, onDone: (String?) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val ref = db.collection("designs").document()
        val batch = db.batch()
        val parts = (bytes.size + PART_BYTES - 1) / PART_BYTES
        for (i in 0 until parts) {
            val slice = bytes.copyOfRange(i * PART_BYTES, minOf(bytes.size, (i + 1) * PART_BYTES))
            batch.set(ref.collection("parts").document(i.toString().padStart(3, '0')), hashMapOf("i" to i, "data" to android.util.Base64.encodeToString(slice, android.util.Base64.NO_WRAP)))
        }
        batch.set(ref, hashMapOf("type" to type, "name" to name, "parts" to parts, "size" to bytes.size, "createdAt" to System.currentTimeMillis()))
        batch.commit().addOnSuccessListener { onDone(null) }.addOnFailureListener { onDone(it.message ?: "failed") }
    }

    fun delete(id: String) {
        val db = FirebaseFirestore.getInstance()
        val ref = db.collection("designs").document(id)
        ref.collection("parts").get().addOnSuccessListener { snap ->
            val batch = db.batch()
            snap.documents.forEach { batch.delete(it.reference) }
            batch.delete(ref)
            batch.commit()
        }.addOnFailureListener { ref.delete() }
    }

    private var started = false
    private val ready = java.util.concurrent.ConcurrentHashMap<String, Design>()
    private val loading = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val io = java.util.concurrent.Executors.newSingleThreadExecutor()

    @Synchronized
    fun start(context: Context) {
        if (started || !AuthRepository.isConfigured(context)) return
        started = true
        val app = context.applicationContext
        val dir = java.io.File(app.filesDir, "designs").also { it.mkdirs() }
        val db = FirebaseFirestore.getInstance()
        db.collection("designs").orderBy("createdAt", Query.Direction.ASCENDING).addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            class Meta(val id: String, val type: String, val name: String, val version: Long, val parts: Int, val legacy: String?, val level: Int)
            val metas = snap.documents.map {
                Meta(it.id, it.getString("type").orEmpty(), it.getString("name").orEmpty(), it.getLong("createdAt") ?: 0L, (it.getLong("parts") ?: 0L).toInt(), it.getString("data"), (it.getLong("level") ?: 0L).toInt())
            }
            val order = metas.map { it.id }
            ready.keys.retainAll(order.toSet())
            fun publishList() { remote.value = order.mapNotNull { ready[it] } }
            for (m in metas) {
                val file = java.io.File(dir, "${m.id}_${m.version}.bin")
                if (file.exists()) {
                    ready[m.id] = Design(m.id, m.type, m.name, file = file.absolutePath, level = m.level)
                } else if (m.legacy != null) {
                    file.writeBytes(android.util.Base64.decode(m.legacy, android.util.Base64.DEFAULT))
                    ready[m.id] = Design(m.id, m.type, m.name, file = file.absolutePath, level = m.level)
                } else if (m.parts > 0 && loading.add(m.id)) {
                    io.execute {
                        try {
                            val docs = com.google.android.gms.tasks.Tasks.await(
                                db.collection("designs").document(m.id).collection("parts").orderBy("i").get(),
                            )
                            if (docs.size() == m.parts) {
                                val out = java.io.ByteArrayOutputStream()
                                docs.documents.forEach { out.write(android.util.Base64.decode(it.getString("data").orEmpty(), android.util.Base64.DEFAULT)) }
                                dir.listFiles()?.filter { it.name.startsWith(m.id + "_") }?.forEach { it.delete() }
                                file.writeBytes(out.toByteArray())
                                ready[m.id] = Design(m.id, m.type, m.name, file = file.absolutePath, level = m.level)
                                publishList()
                            }
                        } catch (_: Exception) {
                        } finally {
                            loading.remove(m.id)
                        }
                    }
                }
            }
            publishList()
        }
    }
}

/** Fonts for the display name. */
object NameStyles {
    private val cache = HashMap<String, androidx.compose.ui.text.font.FontFamily?>()

    /** "" is the normal Kiro font. */
    fun options(): List<String> = listOf("", "Satoshi", "Cinzel", "Roboto Mono", "Comic Neue", "Poppins", "Montserrat")

    fun label(style: String) = style.ifEmpty { "Default" }

    fun family(context: Context, style: String): androidx.compose.ui.text.font.FontFamily? {
        if (style.isEmpty()) return null
        return cache.getOrPut(style) {
            try {
                val tf = com.biaruuu.kiro.ui.player.SubtitleOptions.typeface(context, style)
                androidx.compose.ui.text.font.FontFamily(tf)
            } catch (_: Exception) {
                null
            }
        }
    }
}
