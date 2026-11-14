@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.biaruuu.kiro.ui.player

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.biaruuu.kiro.R

/** What the user picked for how captions look. Indexes point into [SubtitleOptions]. */
data class SubtitleStyle(
    val font: Int = 0,
    val sizePercent: Int = 100,
    val color: Int = 0,
    val background: Int = 1,
    val edge: Int = 1,
)

object SubtitleOptions {
    val fonts = listOf("Netflix Sans", "Sans", "Serif", "Monospace", "Casual")
    val colors = listOf(
        "White" to Color.WHITE,
        "Yellow" to Color.parseColor("#FFEB3B"),
        "Cyan" to Color.parseColor("#4DD0E1"),
        "Green" to Color.parseColor("#69F0AE"),
        "Pink" to Color.parseColor("#FF80AB"),
    )
    val backgrounds = listOf(
        "None" to Color.TRANSPARENT,
        "Dim" to Color.argb(150, 0, 0, 0),
        "Black" to Color.BLACK,
    )
    val edges = listOf(
        "None" to CaptionStyleCompat.EDGE_TYPE_NONE,
        "Outline" to CaptionStyleCompat.EDGE_TYPE_OUTLINE,
        "Shadow" to CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
    )
    const val MIN_SIZE = 50
    const val MAX_SIZE = 200
    const val SIZE_STEP = 10

    fun typeface(context: Context, index: Int): Typeface = when (index) {
        0 -> ResourcesCompat.getFont(context, R.font.netflix_sans_medium) ?: Typeface.SANS_SERIF
        1 -> Typeface.SANS_SERIF
        2 -> Typeface.SERIF
        3 -> Typeface.MONOSPACE
        else -> Typeface.create("casual", Typeface.NORMAL)
    }
}

object SubtitleStyleStore {
    private const val PREFS = "kiro_user_data"

    fun load(context: Context): SubtitleStyle {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return SubtitleStyle(
            font = p.getInt("sub_font", 0).coerceIn(0, SubtitleOptions.fonts.lastIndex),
            sizePercent = p.getInt("sub_size", 100).coerceIn(SubtitleOptions.MIN_SIZE, SubtitleOptions.MAX_SIZE),
            color = p.getInt("sub_color", 0).coerceIn(0, SubtitleOptions.colors.lastIndex),
            background = p.getInt("sub_bg", 1).coerceIn(0, SubtitleOptions.backgrounds.lastIndex),
            edge = p.getInt("sub_edge", 1).coerceIn(0, SubtitleOptions.edges.lastIndex),
        )
    }

    fun save(context: Context, style: SubtitleStyle) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("sub_font", style.font)
            .putInt("sub_size", style.sizePercent)
            .putInt("sub_color", style.color)
            .putInt("sub_bg", style.background)
            .putInt("sub_edge", style.edge)
            .apply()
    }
}

/** Applies the style to the player's caption view; our choices win over the file's own styling. */
fun SubtitleView.applyKiroStyle(context: Context, style: SubtitleStyle) {
    setApplyEmbeddedStyles(false)
    setApplyEmbeddedFontSizes(false)
    setStyle(
        CaptionStyleCompat(
            SubtitleOptions.colors[style.color].second,
            SubtitleOptions.backgrounds[style.background].second,
            Color.TRANSPARENT,
            SubtitleOptions.edges[style.edge].second,
            Color.BLACK,
            SubtitleOptions.typeface(context, style.font),
        ),
    )
    // 5.33% of the video height is the normal caption size.
    setFractionalTextSize(0.0533f * style.sizePercent / 100f)
}
