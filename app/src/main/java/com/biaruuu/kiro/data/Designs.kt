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
            class Meta(val id: String, val type: String, val name: String, val version: Long, val parts: Int, val legacy: String?)
            val metas = snap.documents.map {
                Meta(it.id, it.getString("type").orEmpty(), it.getString("name").orEmpty(), it.getLong("createdAt") ?: 0L, (it.getLong("parts") ?: 0L).toInt(), it.getString("data"))
            }
            val order = metas.map { it.id }
            ready.keys.retainAll(order.toSet())
            fun publishList() { remote.value = order.mapNotNull { ready[it] } }
            for (m in metas) {
                val file = java.io.File(dir, "${m.id}_${m.version}.bin")
                if (file.exists()) {
                    ready[m.id] = Design(m.id, m.type, m.name, file = file.absolutePath)
                } else if (m.legacy != null) {
                    file.writeBytes(android.util.Base64.decode(m.legacy, android.util.Base64.DEFAULT))
                    ready[m.id] = Design(m.id, m.type, m.name, file = file.absolutePath)
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
                                ready[m.id] = Design(m.id, m.type, m.name, file = file.absolutePath)
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

/** Fonts for the display name. "Pricedown" only shows up when its font file has been added to the app. */
object NameStyles {
    const val PRICEDOWN = "Pricedown"
    private const val PRICEDOWN_ASSET = "fonts/pricedown.otf"

    private val cache = HashMap<String, androidx.compose.ui.text.font.FontFamily?>()

    private fun hasPricedown(context: Context): Boolean =
        try { context.assets.open(PRICEDOWN_ASSET).close(); true } catch (_: Exception) { false }

    /**
     * A font file the person picked from their own phone. It stays on this phone only, so nothing is
     * redistributed and it is their own copy of the font that is used.
     */
    const val CUSTOM = "Custom"
    private const val CUSTOM_FILE = "name_font.ttf"

    private fun customFile(context: Context) = java.io.File(context.filesDir, CUSTOM_FILE)

    fun hasCustom(context: Context) = customFile(context).exists()

    fun customName(context: Context): String =
        context.getSharedPreferences("kiro_user_data", Context.MODE_PRIVATE).getString("name_font_label", null) ?: "My font"

    /** Copies the picked font into the app; false if the file isn't a usable font. */
    fun importCustom(context: Context, uri: android.net.Uri): Boolean = try {
        val target = customFile(context)
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
        android.graphics.Typeface.createFromFile(target)
        val label = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }?.substringBeforeLast('.') ?: "My font"
        context.getSharedPreferences("kiro_user_data", Context.MODE_PRIVATE).edit().putString("name_font_label", label).apply()
        cache.remove(CUSTOM)
        true
    } catch (_: Throwable) {
        customFile(context).delete()
        false
    }

    /** "" is the normal Kiro font. */
    fun options(context: Context): List<String> =
        listOf("", "Satoshi", "Cinzel", "Roboto Mono", "Comic Neue", "Poppins", "Montserrat") +
            (if (hasPricedown(context)) listOf(PRICEDOWN) else emptyList()) +
            (if (hasCustom(context)) listOf(CUSTOM) else emptyList())

    fun label(context: Context, style: String) = when (style) {
        "" -> "Default"
        CUSTOM -> customName(context)
        else -> style
    }

    fun family(context: Context, style: String): androidx.compose.ui.text.font.FontFamily? {
        if (style.isEmpty()) return null
        return cache.getOrPut(style) {
            try {
                val tf = if (style == PRICEDOWN) {
                    android.graphics.Typeface.createFromAsset(context.assets, PRICEDOWN_ASSET)
                } else if (style == CUSTOM) {
                    if (!hasCustom(context)) return@getOrPut null
                    android.graphics.Typeface.createFromFile(customFile(context))
                } else {
                    com.biaruuu.kiro.ui.player.SubtitleOptions.typeface(context, style)
                }
                androidx.compose.ui.text.font.FontFamily(tf)
            } catch (_: Exception) {
                null
            }
        }
    }
}
