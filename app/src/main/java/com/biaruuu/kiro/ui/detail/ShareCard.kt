package com.biaruuu.kiro.ui.detail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.biaruuu.kiro.data.Anime
import java.io.File
import java.io.FileOutputStream

/** A neat 1080 x 1350 picture of a show (poster, score, your rating, genres) for sharing. */
object ShareCard {
    private const val W = 1080
    private const val H = 1350

    suspend fun create(context: Context, anime: Anime, ratingHalves: Int): File? {
        val poster = loadPoster(context, anime.posterUrl)
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Backdrop: the poster, enlarged and darkened, over a deep purple-black gradient.
        fill.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), Color.parseColor("#1A1530"), Color.parseColor("#0B0B0E"), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), fill)
        fill.shader = null
        if (poster != null) {
            val scale = maxOf(W.toFloat() / poster.width, H.toFloat() / poster.height) * 1.15f
            val dw = poster.width * scale
            val dh = poster.height * scale
            c.drawBitmap(poster, null, RectF((W - dw) / 2, (H - dh) / 2, (W + dw) / 2, (H + dh) / 2), fill)
            fill.color = Color.argb(205, 10, 9, 16)
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), fill)
        }

        // Poster card.
        val pw = 520f
        val ph = 780f
        val left = (W - pw) / 2
        val top = 96f
        val rect = RectF(left, top, left + pw, top + ph)
        fill.color = Color.argb(90, 0, 0, 0)
        c.drawRoundRect(RectF(rect.left, rect.top + 14, rect.right, rect.bottom + 14), 30f, 30f, fill)
        if (poster != null) {
            val clip = Path().apply { addRoundRect(rect, 30f, 30f, Path.Direction.CW) }
            c.save()
            c.clipPath(clip)
            val s = maxOf(pw / poster.width, ph / poster.height)
            val dw = poster.width * s
            val dh = poster.height * s
            c.drawBitmap(poster, null, RectF(rect.centerX() - dw / 2, rect.centerY() - dh / 2, rect.centerX() + dw / 2, rect.centerY() + dh / 2), fill)
            c.restore()
        } else {
            fill.color = Color.parseColor("#2B2B35")
            c.drawRoundRect(rect, 30f, 30f, fill)
        }

        // Title (up to two lines, centred).
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 62f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val layout = StaticLayout.Builder.obtain(anime.displayTitle, 0, anime.displayTitle.length, title, 900)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).build()
        var y = top + ph + 52f
        c.save(); c.translate((W - 900) / 2f, y); layout.draw(c); c.restore()
        y += layout.height + 18f

        // Format, year, episodes.
        val meta = listOfNotNull(
            anime.format?.replace('_', ' ')?.takeIf { it.isNotBlank() },
            anime.seasonYear.takeIf { it > 0 }?.toString(),
            anime.episodes.takeIf { it > 0 }?.let { "$it episodes" },
        ).joinToString("  ·  ")
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B8B8C2"); textSize = 34f; textAlign = Paint.Align.CENTER }
        if (meta.isNotEmpty()) c.drawText(meta, W / 2f, y + 34f, metaPaint)
        y += 78f

        // Score and your rating pills.
        val pills = mutableListOf<Pair<String, Int>>()
        if (anime.averageScore > 0) pills += "★ %.1f".format(anime.averageScore / 10.0) to Color.parseColor("#F5C542")
        if (ratingHalves > 0) {
            val stars = if (ratingHalves % 2 == 0) "${ratingHalves / 2}" else "${ratingHalves / 2}.5"
            pills += "My rating  ★ $stars" to Color.parseColor("#9C8CFF")
        }
        val pillPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 40f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val widths = pills.map { pillPaint.measureText(it.first) + 72f }
        val total = widths.sum() + 24f * (pills.size - 1).coerceAtLeast(0)
        var x = (W - total) / 2f
        pills.forEachIndexed { i, (text, color) ->
            val r = RectF(x, y, x + widths[i], y + 74f)
            fill.color = Color.argb(46, Color.red(color), Color.green(color), Color.blue(color))
            c.drawRoundRect(r, 37f, 37f, fill)
            pillPaint.color = color
            c.drawText(text, r.left + 36f, r.centerY() + 14f, pillPaint)
            x += widths[i] + 24f
        }
        if (pills.isNotEmpty()) y += 74f + 36f

        // Genres.
        val genres = anime.genres?.take(4)?.joinToString("   ·   ").orEmpty()
        if (genres.isNotEmpty()) {
            val gp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8E8E99"); textSize = 32f; textAlign = Paint.Align.CENTER }
            c.drawText(genres, W / 2f, y + 30f, gp)
        }

        // Footer.
        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 40f; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        c.drawText("Kiro", W / 2f, H - 62f, brand)

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "kiro-${anime.id}.png")
        return try {
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            file
        } catch (_: Exception) {
            null
        } finally {
            bmp.recycle()
        }
    }

    private suspend fun loadPoster(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return try {
            val request = ImageRequest.Builder(context).data(url).allowHardware(false).size(900, 1350).build()
            (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
        } catch (_: Exception) {
            null
        }
    }
}
