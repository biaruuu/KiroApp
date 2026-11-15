package com.biaruuu.kiro.ui.player

import android.content.Context
import android.net.Uri
import com.biaruuu.kiro.data.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.Locale

/**
 * Turns an .srt / .vtt (a file on the phone or a web address) into a WebVTT file with every
 * time moved by the chosen delay, so subtitles that are early or late can be put in sync.
 */
object SubtitleFiles {

    private val TIME = Regex("""(?:(\d{1,2}):)?(\d{1,2}):(\d{2})[.,](\d{1,3})""")
    private val CUE_LINE = Regex("""^(\S+)\s+-->\s+(\S+)(.*)$""")

    /** Reads [source] (content://, file:// or http(s)://), shifts it by [delayMs] and returns a local WebVTT file. */
    suspend fun prepare(context: Context, source: String, delayMs: Long): Uri = withContext(Dispatchers.IO) {
        val raw = read(context, source)
        val vtt = toShiftedVtt(raw, delayMs)
        val dir = File(context.cacheDir, "subs").apply { mkdirs() }
        val file = File(dir, "${Integer.toHexString(source.hashCode())}_$delayMs.vtt")
        file.writeText(vtt)
        Uri.fromFile(file)
    }

    private fun read(context: Context, source: String): String {
        val uri = Uri.parse(source)
        return if (uri.scheme == "http" || uri.scheme == "https") {
            HttpClientProvider.get().newCall(Request.Builder().url(source).build()).execute().use { r ->
                if (!r.isSuccessful) error("Subtitle download failed (HTTP ${r.code})")
                r.body?.string().orEmpty()
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: error("Can't open that subtitle file")
        }
    }

    /** SRT or VTT text in, WebVTT out, all cue times moved by [delayMs] (never below 0). */
    fun toShiftedVtt(raw: String, delayMs: Long): String {
        val out = StringBuilder("WEBVTT\n\n")
        for (line in raw.removePrefix("﻿").lines()) {
            val t = line.trim()
            if (t.startsWith("WEBVTT")) continue
            val m = CUE_LINE.matchEntire(t)
            if (m != null && TIME.containsMatchIn(m.groupValues[1])) {
                out.append(shift(m.groupValues[1], delayMs))
                    .append(" --> ")
                    .append(shift(m.groupValues[2], delayMs))
                    .append(m.groupValues[3])
                    .append('\n')
            } else {
                out.append(line).append('\n')
            }
        }
        return out.toString()
    }

    private fun shift(stamp: String, delayMs: Long): String {
        val m = TIME.find(stamp) ?: return stamp
        val h = m.groupValues[1].ifEmpty { "0" }.toLong()
        val min = m.groupValues[2].toLong()
        val sec = m.groupValues[3].toLong()
        val ms = m.groupValues[4].padEnd(3, '0').toLong()
        val total = (((h * 60 + min) * 60 + sec) * 1000 + ms + delayMs).coerceAtLeast(0)
        return String.format(
            Locale.US, "%02d:%02d:%02d.%03d",
            total / 3_600_000, total / 60_000 % 60, total / 1000 % 60, total % 1000,
        )
    }
}
