package com.biaruuu.kiro.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32

/**
 * Animated PNG (APNG) support that works on every Android version. Android's own decoder only
 * plays them on newer phones, so the frames are read here and played by [ApngDrawable].
 */
object Apng {
    class Frames(val bitmaps: List<Bitmap>, val delaysMs: IntArray, val width: Int, val height: Int)

    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private class Chunk(val type: String, val data: ByteArray)

    private fun chunks(bytes: ByteArray): List<Chunk> {
        val out = ArrayList<Chunk>()
        if (bytes.size < 8 || !bytes.copyOfRange(0, 8).contentEquals(SIGNATURE)) return out
        val buf = ByteBuffer.wrap(bytes)
        var pos = 8
        while (pos + 12 <= bytes.size) {
            val len = buf.getInt(pos)
            if (len < 0 || pos + 12 + len > bytes.size) break
            val type = String(bytes, pos + 4, 4, Charsets.ISO_8859_1)
            out.add(Chunk(type, bytes.copyOfRange(pos + 8, pos + 8 + len)))
            pos += 12 + len
        }
        return out
    }

    fun isApng(bytes: ByteArray): Boolean {
        for (c in chunks(bytes)) {
            if (c.type == "acTL") return true
            if (c.type == "IDAT") return false
        }
        return false
    }

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val head = ByteBuffer.allocate(4).putInt(data.size).array()
        out.write(head)
        val typeBytes = type.toByteArray(Charsets.ISO_8859_1)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        out.write(ByteBuffer.allocate(4).putInt(crc.value.toInt()).array())
    }

    private class FrameInfo(
        val w: Int, val h: Int, val x: Int, val y: Int,
        val delayMs: Int, val dispose: Int, val blend: Int,
    ) {
        val data = ByteArrayOutputStream()
    }

    /** All frames, already combined into full-size pictures; null if the file can't be read. */
    fun decode(bytes: ByteArray, maxBytes: Long = 64L * 1024 * 1024, maxSide: Int = 640): Frames? {
        val all = chunks(bytes)
        val ihdr = all.firstOrNull { it.type == "IHDR" }?.data ?: return null
        if (ihdr.size < 13) return null
        val width = ByteBuffer.wrap(ihdr).getInt(0)
        val height = ByteBuffer.wrap(ihdr).getInt(4)
        if (width <= 0 || height <= 0) return null

        val shared = all.filter { it.type == "PLTE" || it.type == "tRNS" }
        val frames = ArrayList<FrameInfo>()
        var current: FrameInfo? = null
        for (c in all) {
            when (c.type) {
                "fcTL" -> {
                    val b = ByteBuffer.wrap(c.data)
                    val num = b.getShort(20).toInt() and 0xFFFF
                    val den = (b.getShort(22).toInt() and 0xFFFF).let { if (it == 0) 100 else it }
                    var ms = num * 1000 / den
                    if (ms <= 10) ms = 100
                    current = FrameInfo(b.getInt(4), b.getInt(8), b.getInt(12), b.getInt(16), ms, c.data[24].toInt(), c.data[25].toInt())
                    frames.add(current)
                }
                // The default image only counts as a frame when an fcTL came before it.
                "IDAT" -> current?.data?.write(c.data)
                "fdAT" -> current?.data?.write(c.data, 4, c.data.size - 4)
            }
        }
        if (frames.size < 2) return null
        // Big, long animations are shrunk so their frames fit in memory.
        var scale = minOf(1f, maxSide.toFloat() / maxOf(width, height))
        while ((width * scale).toLong() * (height * scale).toLong() * 4 * frames.size > maxBytes) scale *= 0.85f
        val outW = (width * scale).toInt()
        val outH = (height * scale).toInt()
        if (minOf(outW, outH) < 64) return null

        val canvasBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)
        val sourcePaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
        val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        val out = ArrayList<Bitmap>()
        val delays = IntArray(frames.size)

        for ((i, f) in frames.withIndex()) {
            val png = ByteArrayOutputStream()
            png.write(SIGNATURE)
            val header = ihdr.copyOf()
            ByteBuffer.wrap(header).putInt(0, f.w).putInt(4, f.h)
            writeChunk(png, "IHDR", header)
            for (s in shared) writeChunk(png, s.type, s.data)
            writeChunk(png, "IDAT", f.data.toByteArray())
            writeChunk(png, "IEND", ByteArray(0))
            val bytesPng = png.toByteArray()
            val frame = BitmapFactory.decodeByteArray(bytesPng, 0, bytesPng.size) ?: return null

            val region = Rect(f.x, f.y, f.x + f.w, f.y + f.h)
            val backup = if (f.dispose == 2) Bitmap.createBitmap(canvasBitmap, 0, 0, width, height) else null
            canvas.drawBitmap(frame, f.x.toFloat(), f.y.toFloat(), if (f.blend == 0) sourcePaint else null)
            out.add(if (scale == 1f) canvasBitmap.copy(Bitmap.Config.ARGB_8888, false) else Bitmap.createScaledBitmap(canvasBitmap, outW, outH, true))
            delays[i] = f.delayMs
            frame.recycle()

            when (f.dispose) {
                1 -> canvas.drawRect(region, clearPaint)
                2 -> if (backup != null) {
                    canvas.drawRect(region, clearPaint)
                    canvas.drawBitmap(backup, region, region, sourcePaint)
                    backup.recycle()
                }
            }
        }
        canvasBitmap.recycle()
        return Frames(out, delays, outW, outH)
    }
}

/** Plays the frames of an [Apng.Frames] in a loop; share one [Apng.Frames] between many drawables. */
class ApngDrawable(private val frames: Apng.Frames) : Drawable(), Animatable, Runnable {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var index = 0
    private var running = false

    override fun draw(canvas: Canvas) {
        canvas.drawBitmap(frames.bitmaps[index], null, bounds, paint)
    }

    override fun run() {
        if (!running) return
        index = (index + 1) % frames.bitmaps.size
        invalidateSelf()
        scheduleSelf(this, SystemClock.uptimeMillis() + frames.delaysMs[index])
    }

    override fun start() {
        if (running) return
        running = true
        scheduleSelf(this, SystemClock.uptimeMillis() + frames.delaysMs[index])
    }

    override fun stop() {
        running = false
        unscheduleSelf(this)
    }

    override fun isRunning() = running
    override fun getIntrinsicWidth() = frames.width
    override fun getIntrinsicHeight() = frames.height
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
