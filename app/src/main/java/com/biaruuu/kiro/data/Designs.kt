package com.biaruuu.kiro.data

import android.content.Context
import com.biaruuu.kiro.data.auth.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A look users can pick: an avatar decoration, a profile effect or a nameplate. Built-in ones ship
 * inside the app ([asset]); the developer publishes more from the admin panel ([data], base64) and
 * they appear for everyone straight away.
 */
data class Design(val id: String, val type: String, val name: String, val asset: String? = null, val data: String? = null)

object Designs {
    const val DECORATION = "decoration"
    const val EFFECT = "effect"
    const val NAMEPLATE = "nameplate"

    /** Largest image the admin panel accepts: base64 has to fit in one Firestore document (1 MB). */
    const val MAX_BYTES = 600_000

    private val builtIn = listOf(
        Design("cat_ears", DECORATION, "Cat Ears", asset = "decorations/cat_ears.png"),
    )

    val remote = MutableStateFlow<List<Design>>(emptyList())

    fun all(type: String): List<Design> = builtIn.filter { it.type == type } + remote.value.filter { it.type == type }

    fun find(id: String?): Design? =
        if (id.isNullOrEmpty()) null else builtIn.firstOrNull { it.id == id } ?: remote.value.firstOrNull { it.id == id }

    private var started = false

    @Synchronized
    fun start(context: Context) {
        if (started || !AuthRepository.isConfigured(context)) return
        started = true
        FirebaseFirestore.getInstance().collection("designs").orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                remote.value = snap.documents.mapNotNull { d ->
                    val data = d.getString("data") ?: return@mapNotNull null
                    Design(d.id, d.getString("type").orEmpty(), d.getString("name").orEmpty(), data = data)
                }
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

    /** "" is the normal Kiro font. */
    fun options(context: Context): List<String> =
        listOf("", "Satoshi", "Cinzel", "Roboto Mono", "Comic Neue", "Poppins", "Montserrat") +
            (if (hasPricedown(context)) listOf(PRICEDOWN) else emptyList())

    fun label(style: String) = style.ifEmpty { "Default" }

    fun family(context: Context, style: String): androidx.compose.ui.text.font.FontFamily? {
        if (style.isEmpty()) return null
        return cache.getOrPut(style) {
            try {
                val tf = if (style == PRICEDOWN) {
                    android.graphics.Typeface.createFromAsset(context.assets, PRICEDOWN_ASSET)
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
