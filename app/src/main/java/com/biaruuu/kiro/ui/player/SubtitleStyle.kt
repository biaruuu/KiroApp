@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.biaruuu.kiro.ui.player

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.biaruuu.kiro.R
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * How captions look. Font is a name from [SubtitleOptions.fonts] ("Custom" uses the imported file);
 * size, background and edge are indexes into the option lists below.
 */
data class SubtitleStyle(
    val font: String = SubtitleOptions.DEFAULT_FONT,
    val size: Int = 1,
    val background: Int = 0,
    val edge: Int = 2,
)

object SubtitleOptions {
    const val DEFAULT_FONT = "Netflix Sans"
    const val CUSTOM = "Custom"

    /** Font name, bundled file (null = not a file) and the weight to use for variable fonts. */
    private data class Face(val asset: String?, val weight: Int)

    private val faces = linkedMapOf(
        DEFAULT_FONT to Face(null, 0),
        "Roboto (System)" to Face(null, 0),
        "Inter" to Face("fonts/inter.ttf", 600),
        "Open Sans" to Face("fonts/opensans.ttf", 600),
        "Poppins" to Face("fonts/poppins_medium.ttf", 0),
        "Montserrat" to Face("fonts/montserrat.ttf", 600),
        "Cinzel" to Face("fonts/cinzel.ttf", 600),
        "Roboto Mono" to Face("fonts/robotomono.ttf", 600),
        "Comic Neue" to Face("fonts/comicneue_bold.ttf", 0),
    )

    /** Every choice, with "Custom" (a font file the user imports) last. */
    val fonts: List<String> = faces.keys.toList() + CUSTOM

    /** Same four sizes as the reference: Small, Medium, Large, Extra Large (14/18/24/30 on a base of 18). */
    val sizes = listOf("Small" to 14f, "Medium" to 18f, "Large" to 24f, "Extra Large" to 30f)
    val backgrounds = listOf(
        "Transparent" to Color.TRANSPARENT,
        "Semi-Transparent" to Color.argb(0x88, 0, 0, 0),
        "Solid" to Color.BLACK,
    )
    val edges = listOf(
        "None" to CaptionStyleCompat.EDGE_TYPE_NONE,
        "Subtle Shadow" to CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
        "Outline" to CaptionStyleCompat.EDGE_TYPE_OUTLINE,
    )

    private val cache = ConcurrentHashMap<String, Typeface>()

    private fun customFile(context: Context) = File(context.filesDir, "subtitle_font.ttf")

    fun customName(context: Context): String? =
        context.applicationContext.getSharedPreferences("kiro_user_data", Context.MODE_PRIVATE)
            .getString("sub_custom_font_name", null)

    /** Copies a font file the user picked (.ttf / .otf) into the app and remembers its name. */
    fun importCustomFont(context: Context, uri: Uri, displayName: String): Boolean = try {
        val target = customFile(context)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: return false
        // Make sure it really is a font before keeping it.
        Typeface.createFromFile(target)
        context.applicationContext.getSharedPreferences("kiro_user_data", Context.MODE_PRIVATE).edit()
            .putString("sub_custom_font_name", displayName.substringBeforeLast('.'))
            .apply()
        cache.remove(CUSTOM)
        true
    } catch (_: Exception) {
        customFile(context).delete()
        false
    }

    fun typeface(context: Context, font: String): Typeface = cache.getOrPut(font) {
        val bold = if (Build.VERSION.SDK_INT >= 28) 600 else 0
        when (font) {
            DEFAULT_FONT -> ResourcesCompat.getFont(context, R.font.netflix_sans_bold)
            "Roboto (System)" ->
                if (bold > 0) Typeface.create(Typeface.SANS_SERIF, bold, false) else Typeface.SANS_SERIF
            CUSTOM -> customFile(context).takeIf { it.exists() }?.let {
                try {
                    Typeface.createFromFile(it)
                } catch (_: Exception) {
                    null
                }
            }
            else -> faces[font]?.let { face ->
                try {
                    val builder = Typeface.Builder(context.assets, face.asset!!)
                    if (face.weight > 0) builder.setFontVariationSettings("'wght' ${face.weight}")
                    builder.build()
                } catch (_: Exception) {
                    null
                }
            }
        } ?: Typeface.SANS_SERIF
    }
}

object SubtitleStyleStore {
    private const val PREFS = "kiro_user_data"

    fun load(context: Context): SubtitleStyle {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val font = p.getString("sub_font_name", SubtitleOptions.DEFAULT_FONT)
            ?.takeIf { it in SubtitleOptions.fonts } ?: SubtitleOptions.DEFAULT_FONT
        return SubtitleStyle(
            font = font,
            size = p.getInt("sub_size_idx", 1).coerceIn(0, SubtitleOptions.sizes.lastIndex),
            background = p.getInt("sub_bg_idx", 0).coerceIn(0, SubtitleOptions.backgrounds.lastIndex),
            edge = p.getInt("sub_edge_idx", 2).coerceIn(0, SubtitleOptions.edges.lastIndex),
        )
    }

    fun save(context: Context, style: SubtitleStyle) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("sub_font_name", style.font)
            .putInt("sub_size_idx", style.size)
            .putInt("sub_bg_idx", style.background)
            .putInt("sub_edge_idx", style.edge)
            .apply()
    }
}

/** Applies the style to the player's caption view; our choices win over the file's own styling. */
fun SubtitleView.applyKiroStyle(context: Context, style: SubtitleStyle) {
    setApplyEmbeddedStyles(false)
    setApplyEmbeddedFontSizes(false)
    setStyle(
        CaptionStyleCompat(
            Color.WHITE,
            SubtitleOptions.backgrounds[style.background].second,
            Color.TRANSPARENT,
            SubtitleOptions.edges[style.edge].second,
            Color.BLACK,
            SubtitleOptions.typeface(context, style.font),
        ),
    )
    // 5.33% of the video height is the normal size (Medium).
    setFractionalTextSize(0.0533f * SubtitleOptions.sizes[style.size].second / 18f)
}
