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
    val background: Int = SubtitleOptions.DARK_BOX,
    val edge: Int = 0,
    /** Each new caption line fades in instead of popping on. */
    val fade: Boolean = true,
)

object SubtitleOptions {
    /** Captions default to Satoshi, the bundled Satoshi Bold file. */
    const val DEFAULT_FONT = "Satoshi"
    const val TREBUCHET = "Trebuchet MS"
    const val CUSTOM = "Custom"

    /** Font name, bundled file (null = not a file) and the weight to use for variable fonts. */
    private data class Face(val asset: String?, val weight: Int)

    private val faces = linkedMapOf(
        DEFAULT_FONT to Face("fonts/satoshi_bold.otf", 0),
        TREBUCHET to Face("fonts/trebuchet.ttf", 0),
        "Roboto" to Face(null, 0),
        "Inter" to Face("fonts/inter.ttf", 500),
        "Open Sans" to Face("fonts/opensans.ttf", 500),
        "Poppins" to Face("fonts/poppins_medium.ttf", 0),
        "Montserrat" to Face("fonts/montserrat.ttf", 500),
        "Cinzel" to Face("fonts/cinzel.ttf", 500),
        "Roboto Mono" to Face("fonts/robotomono.ttf", 500),
        "Comic Neue" to Face("fonts/comicneue_bold.ttf", 0),
    )

    /** Every choice, with "Custom" (a font file the user imports) last. */
    val fonts: List<String> = faces.keys.toList() + CUSTOM

    /** Same four sizes as the reference: Small, Medium, Large, Extra Large (14/18/24/30 on a base of 18). */
    val sizes = listOf("Small" to 14f, "Medium" to 18f, "Large" to 24f, "Extra Large" to 30f)

    /** The reference draws captions at 1.1x the chosen size, in dp. */
    const val TEXT_SCALE = 1.1f
    val backgrounds = listOf(
        "Transparent" to Color.TRANSPARENT,
        "Semi-Transparent" to Color.argb(0x73, 0, 0, 0),
        "Solid" to Color.BLACK,
        "Dark Box" to Color.argb(0xB8, 0x0A, 0x0A, 0x0C),
    )
    const val DARK_BOX = 3
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
    fun importCustomFont(context: Context, uri: Uri, displayName: String): Boolean {
        return try {
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
    }

    fun typeface(context: Context, font: String): Typeface = cache.getOrPut(font) {
        val bold = if (Build.VERSION.SDK_INT >= 28) 500 else 0
        when (font) {
            "Roboto" ->
                if (bold > 0) Typeface.create(Typeface.SANS_SERIF, bold, false) else Typeface.SANS_SERIF
            CUSTOM -> customFile(context).takeIf { it.exists() }?.let {
                try {
                    Typeface.create(Typeface.createFromFile(it), Typeface.BOLD)
                } catch (_: Exception) {
                    null
                }
            }
            else -> faces[font]?.let { face ->
                try {
                    val builder = Typeface.Builder(context.assets, face.asset!!)
                    if (face.weight > 0) builder.setFontVariationSettings("'wght' ${face.weight}")
                    val tf = builder.build()
                    // Static regular files (Trebuchet MS) get the same semi-bold look as the others.
                    if (font == TREBUCHET && tf != null) Typeface.create(tf, Typeface.BOLD) else tf
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
            background = p.getInt("sub_bg_idx", SubtitleOptions.DARK_BOX).coerceIn(0, SubtitleOptions.backgrounds.lastIndex),
            edge = p.getInt("sub_edge_idx", 0).coerceIn(0, SubtitleOptions.edges.lastIndex),
            fade = p.getBoolean("sub_fade", true),
        )
    }

    fun save(context: Context, style: SubtitleStyle) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("sub_font_name", style.font)
            .putInt("sub_size_idx", style.size)
            .putInt("sub_bg_idx", style.background)
            .putInt("sub_edge_idx", style.edge)
            .putBoolean("sub_fade", style.fade)
            .apply()
    }
}

/** Applies the style to the player's caption view; our choices win over the file's own styling. */
fun SubtitleView.applyKiroStyle(context: Context, style: SubtitleStyle, scale: Float = 1f) {
    setApplyEmbeddedStyles(false)
    setApplyEmbeddedFontSizes(false)
    setStyle(
        CaptionStyleCompat(
            Color.WHITE,
            SubtitleOptions.backgrounds[style.background].second,
            Color.TRANSPARENT,
            SubtitleOptions.edges[style.edge].second,
            // The reference's subtle shadow is black at 85% opacity; the outline is solid black.
            if (SubtitleOptions.edges[style.edge].second == CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW) Color.argb(0xD9, 0, 0, 0) else Color.BLACK,
            SubtitleOptions.typeface(context, style.font),
        ),
    )
    // Size follows the picture: a fraction of the view height, so captions grow in full screen, on
    // tablets and shrink with the room's small player, with no fixed dp that is too big or too small.
    setFractionalTextSize(SubtitleOptions.sizes[style.size].second * SubtitleOptions.TEXT_SCALE / 360f)
    setBottomPaddingFraction(0.06f)
    if (getTag(R.id.kiro_caption_pad) == null) {
        setTag(R.id.kiro_caption_pad, true)
        addOnLayoutChangeListener { v, l, _, r, _, _, _, _, _ ->
            val side = ((r - l) * 0.1f).toInt()
            if (v.paddingLeft != side) v.setPadding(side, 0, side, 0)
        }
    }
}
