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
