package com.biaruuu.kiro.data

import android.content.Context
import android.os.Handler
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Automatic Filipino captions for the developer's dub. The sound of the episode is pulled out of the
 * very same video file the viewers watch (so the timing always lines up), sent to a speech-to-text
 * service (Whisper, Tagalog), cleaned of the false lines it invents over music and silence, and saved
 * with the episode as WebVTT. Viewers just see the captions: nothing is transcribed while they watch.
 *
 * The service key lives in Firestore `adminconfig/asr` (only the developer can read it).
 */
object TagalogCaptions {
    class Config(val endpoint: String, val model: String, val key: String)

    const val DEFAULT_ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
    const val DEFAULT_MODEL = "whisper-large-v3"
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).writeTimeout(5, TimeUnit.MINUTES).readTimeout(10, TimeUnit.MINUTES).build()

    private fun ref() = FirebaseFirestore.getInstance().collection("adminconfig").document("asr")

    fun loadConfig(done: (Config?) -> Unit) {
        ref().get().addOnSuccessListener { d ->
            val key = d.getString("key").orEmpty()
            done(
                if (key.isBlank()) null
                else Config(d.getString("endpoint").orEmpty().ifBlank { DEFAULT_ENDPOINT }, d.getString("model").orEmpty().ifBlank { DEFAULT_MODEL }, key),
            )
        }.addOnFailureListener { done(null) }
    }

    fun saveConfig(c: Config, done: (String?) -> Unit) {
        ref().set(mapOf("endpoint" to c.endpoint, "model" to c.model, "key" to c.key))
            .addOnSuccessListener { done(null) }.addOnFailureListener { done(it.message ?: "failed") }
    }

    fun remove(id: String) {
        FirebaseFirestore.getInstance().collection("tagdub").document(id).update("captions", com.google.firebase.firestore.FieldValue.delete())
    }

    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    /** Stops a captions job that is running for this episode. */
    fun cancel(id: String) { cancelled.add(id) }

    private const val CHUNK_MS = 8L * 60 * 1000
    /** Without a way to know the end, a piece clearly smaller than a full one (8 min at 48 kbps = 2.9 MB) was the last. */
    private const val FULL_CHUNK_BYTES = 2_400_000L

    /** Runs the whole job. [progress] gets short status lines; [done] gets null on success or an error. */
    fun generate(context: Context, e: TagalogDub.Entry, progress: (String, Float) -> Unit, done: (String?) -> Unit) {
        fun finish(err: String?) { main.post { done(err) } }
        // [frac] is how far the whole episode is (0..1), or -1 when the length is not known yet.
        fun say(s: String, frac: Float = -1f) {
            val label = if (frac >= 0f) "$s · ${(frac * 100).toInt()}%" else s
            main.post { progress(label, frac) }
        }
        loadConfig { cfg ->
            if (cfg == null) return@loadConfig finish("Save your transcription key first.")
            val all = ArrayList<Seg>()
            fun complete() {
                Thread {
                    val vtt = toVtt(clean(all))
                    if (vtt == null) { finish("The service heard no speech."); return@Thread }
                    say("Saving…")
                    FirebaseFirestore.getInstance().collection("tagdub").document(e.id).update("captions", vtt)
                        .addOnSuccessListener { finish(null) }.addOnFailureListener { finish(it.message ?: "Could not save") }
                }.start()
            }
            cancelled.remove(e.id)
            val total = java.util.concurrent.atomic.AtomicLong(0)
            fun overall(i: Int, within: Float): Float {
                val t = total.get()
                return if (t <= 0) -1f else ((i * CHUNK_MS + within * CHUNK_MS) / t).coerceIn(0f, 1f)
            }
            val headers = if (e.referer.startsWith("http")) mapOf("Referer" to e.referer, "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36") else emptyMap()
            // The episode is listened to in 8 minute pieces: long audio makes the service skip speech and drift,
            // and pieces mean an episode of any length works.
            fun step(i: Int) {
                if (cancelled.remove(e.id)) return finish("Stopped.")
                say("Getting the sound", overall(i, 0f))
                extract(
                    context, e.url, headers, i * CHUNK_MS, (i + 1) * CHUNK_MS,
                    onDuration = { total.set(it) },
                    onFrac = { f -> say("Getting the sound", overall(i, f * 0.5f)) },
                ) { file, ended, err ->
                    if (file == null || file.length() < 8_000) {
                        file?.delete()
                        if (i == 0) return@extract finish(err ?: "Could not read the sound of this video.")
                        return@extract complete()
                    }
                    Thread {
                        try {
                            say("Listening", overall(i, 0.5f))
                            all.addAll(transcribe(cfg, file, i * CHUNK_MS / 1000.0))
                            say("Listening", overall(i, 1f))
                            main.post { if (ended || i >= 200) complete() else step(i + 1) }
                        } catch (ex: Exception) {
                            finish(ex.message ?: "Failed")
                        } finally {
                            file.delete()
                        }
                    }.start()
                }
            }
            step(0)
        }
    }

    /**
     * One piece of the video as an audio-only file. The sound track is copied as it is (no re-encoding, so
     * it is quick and cannot get stuck); only when that is not possible (a streaming playlist, an unusual
     * sound format) does the slower Media3 export take over, with a time limit.
     */
    private fun extract(
        context: Context, url: String, headers: Map<String, String>, fromMs: Long, toMs: Long,
        onDuration: (Long) -> Unit, onFrac: (Float) -> Unit, done: (File?, Boolean, String?) -> Unit,
    ) {
        Thread {
            try {
                val r = extractFast(context, url, headers, fromMs, toMs, onDuration, onFrac)
                if (r != null) { done(r.first, r.second, null); return@Thread }
            } catch (t: java.util.concurrent.TimeoutException) {
                done(null, false, t.message)
                return@Thread
            } catch (_: Exception) {
            }
            extractAudio(context, url, fromMs, toMs, onDuration, onFrac) { f, err -> done(f, f != null && f.length() < FULL_CHUNK_BYTES, err) }
        }.start()
    }

    private fun extractFast(
        context: Context, url: String, headers: Map<String, String>, fromMs: Long, toMs: Long,
        onDuration: (Long) -> Unit, onFrac: (Float) -> Unit,
    ): Pair<File, Boolean>? {
        if (url.substringBefore('?').endsWith(".m3u8")) return null
        val ex = MediaExtractor()
        var muxer: MediaMuxer? = null
        // If the video host stops answering for 2 minutes, stop instead of waiting forever.
        val tick = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())
        val alive = java.util.concurrent.atomic.AtomicBoolean(true)
        val stalled = java.util.concurrent.atomic.AtomicBoolean(false)
        Thread {
            while (alive.get()) {
                try { Thread.sleep(5000) } catch (_: InterruptedException) { return@Thread }
                if (alive.get() && System.currentTimeMillis() - tick.get() > 120_000) {
                    stalled.set(true)
                    try { ex.release() } catch (_: Exception) {}
                    return@Thread
                }
            }
        }.apply { isDaemon = true }.start()
        val out = File(context.cacheDir, "asr_${System.nanoTime()}.m4a")
        try {
            ex.setDataSource(url, headers)
            var track = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) { track = i; fmt = f; break }
            }
            if (track < 0 || fmt == null) return null
            if (fmt.containsKey(MediaFormat.KEY_DURATION)) onDuration(fmt.getLong(MediaFormat.KEY_DURATION) / 1000)
            val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
            val container = when (mime) {
                "audio/mp4a-latm" -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                "audio/opus", "audio/vorbis" -> MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                else -> return null
            }
            val target = if (container == MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM) File(out.path.removeSuffix(".m4a") + ".webm") else out
            ex.selectTrack(track)
            if (fromMs > 0) ex.seekTo(fromMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            muxer = MediaMuxer(target.path, container)
            val idx = muxer.addTrack(fmt)
            muxer.start()
            val buf = ByteBuffer.allocate(1 shl 18)
            val info = MediaCodec.BufferInfo()
            val fromUs = fromMs * 1000
            val toUs = toMs * 1000
            var wrote = 0
            var reachedEnd = false
            var lastReport = 0L
            while (true) {
                buf.clear()
                val size = ex.readSampleData(buf, 0)
                tick.set(System.currentTimeMillis())
                if (size < 0) { reachedEnd = true; break }
                val ts = ex.sampleTime
                if (ts >= toUs) break
                if (ts >= fromUs) {
                    info.set(0, size, ts - fromUs, if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    muxer.writeSampleData(idx, buf, info)
                    wrote++
                    val now = System.currentTimeMillis()
                    if (now - lastReport > 700) { lastReport = now; onFrac(((ts - fromUs).toFloat() / (toUs - fromUs)).coerceIn(0f, 1f)) }
                }
                ex.advance()
            }
            muxer.stop()
            muxer.release()
            muxer = null
            if (wrote == 0) { target.delete(); return Pair(target, true) }
            return Pair(target, reachedEnd)
        } catch (e: Exception) {
            try { muxer?.release() } catch (_: Exception) {}
            out.delete()
            if (stalled.get()) throw java.util.concurrent.TimeoutException("The video host stopped answering. Try again later.")
            throw e
        } finally {
            alive.set(false)
            try { ex.release() } catch (_: Exception) {}
        }
    }

    /** Slower fallback: Media3 re-encodes the sound of one piece (works for streaming playlists). Gives up after 3 minutes. */
    private fun extractAudio(
        context: Context, url: String, fromMs: Long, toMs: Long, onDuration: (Long) -> Unit, onFrac: (Float) -> Unit,
        finished: (File?, String?) -> Unit,
    ) {
        val once = java.util.concurrent.atomic.AtomicBoolean(false)
        val done: (File?, String?) -> Unit = { f, err -> if (once.compareAndSet(false, true)) finished(f, err) else f?.delete() }
        main.postDelayed({ done(null, "Reading the sound took too long. Try an .mp4 link, which is much faster.") }, 180_000)
        main.post {
            val out = File(context.cacheDir, "asr_${System.nanoTime()}.m4a")
            try {
                val encoder = DefaultEncoderFactory.Builder(context)
                    .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(48_000).build()).build()
                val transformer = Transformer.Builder(context)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoder)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) { done(out, null) }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            out.delete()
                            done(null, exportException.message)
                        }
                    }).build()
                val clip = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(fromMs).setEndPositionMs(toMs).build()
                val media = MediaItem.Builder().setUri(url).setClippingConfiguration(clip).build()
                transformer.start(EditedMediaItem.Builder(media).setRemoveVideo(true).build(), out.absolutePath)
                val holder = androidx.media3.transformer.ProgressHolder()
                val poll = object : Runnable {
                    override fun run() {
                        if (once.get()) return
                        if (transformer.getProgress(holder) != Transformer.PROGRESS_STATE_NOT_STARTED) onFrac(holder.progress / 100f)
                        main.postDelayed(this, 700)
                    }
                }
                main.postDelayed(poll, 700)
            } catch (ex: Exception) {
                done(null, ex.message)
            }
        }
    }

    private class Seg(val start: Double, val end: Double, val text: String, val words: List<Triple<String, Double, Double>>)

    private fun transcribe(cfg: Config, file: File, offset: Double): List<Seg> {
        // No "prompt": with music or silence the service tends to print the prompt back as if it were speech.
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody((if (file.name.endsWith(".webm")) "audio/webm" else "audio/mp4").toMediaType()))
            .addFormDataPart("model", cfg.model)
            .addFormDataPart("language", "tl")
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("timestamp_granularities[]", "word")
            .addFormDataPart("timestamp_granularities[]", "segment")
            .build()
        val req = Request.Builder().url(cfg.endpoint).header("Authorization", "Bearer ${cfg.key}").post(body).build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IllegalStateException("Service said ${r.code}: ${text.take(160)}")
            val json = JSONObject(text)
            val arr = json.optJSONArray("segments") ?: return emptyList()
            val wordsArr = json.optJSONArray("words")
            val words = ArrayList<Triple<String, Double, Double>>()
            if (wordsArr != null) for (i in 0 until wordsArr.length()) {
                val w = wordsArr.getJSONObject(i)
                words.add(Triple(w.optString("word").trim(), w.optDouble("start") + offset, w.optDouble("end") + offset))
            }
            val out = ArrayList<Seg>()
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val t = s.optString("text").trim()
                if (t.isEmpty()) continue
                val noSpeech = s.optDouble("no_speech_prob", 0.0)
                val logprob = s.optDouble("avg_logprob", 0.0)
                val ratio = s.optDouble("compression_ratio", 1.0)
                // Invented lines over silence, music and effects have these tell-tale signs. Only the
                // clear cases go, so real speech is not lost.
                if (noSpeech > 0.85) continue
                if (noSpeech > 0.6 && logprob < -1.0) continue
                if (ratio > 2.6 || logprob < -1.8) continue
                val st = s.optDouble("start") + offset
                val en = s.optDouble("end") + offset
                val inside = words.filter { it.second >= st - 0.08 && it.third <= en + 0.4 }
                out.add(Seg(st, en, t, inside))
            }
            return out
        }
    }

    private val junk = listOf(
        "ito ay tagalog na dub", "mga pangungusap sa filipino", "halong english", "pag-ibig na tayo ng mga pangungusap",
        "salamat sa panonood", "thanks for watching", "thank you for watching", "subtitles by", "subtitle by", "amara.org",
        "please subscribe", "pakisubscribe", "like and subscribe", "www.", ".com",
    )

    /** Removes made-up lines from captions that were saved earlier (so they need no redo to look right). */
    fun scrub(vtt: String): String =
        vtt.split("\n\n").filter { b -> val l = b.lowercase(Locale.ROOT); junk.none { l.contains(it) } }.joinToString("\n\n")

    /** Drops lines the service makes up and repeats; keeps the rest in time order. */
    private fun clean(segs: List<Seg>): List<Seg> {
        val sorted = segs.sortedBy { it.start }
        val out = ArrayList<Seg>()
        var last = ""
        var repeats = 0
        for (s in sorted) {
            val low = s.text.lowercase(Locale.ROOT)
            if (junk.any { low.contains(it) }) continue
            if (s.end - s.start < 0.2 && s.text.length < 3) continue
            if (low == last) { if (++repeats >= 2) continue } else { last = low; repeats = 0 }
            out.add(s)
        }
        return out
    }

    private fun stamp(t: Double): String {
        val ms = (t * 1000).toLong().coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000)
    }

    /**
     * Captions start when the first word is said and end when the last one ends (from the word times), so
     * they don't hang over music or silence. Long lines become two short lines, or several captions.
     */
    private fun toVtt(segs: List<Seg>): String? {
        if (segs.isEmpty()) return null
        val sb = StringBuilder("WEBVTT\n\n")
        var prevEnd = 0.0
        for (s in segs) {
            val tokens = s.text.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) continue
            // Words with times, when the service gave them for this line and they match its text.
            val timed = s.words.size == tokens.size
            val groups = ArrayList<IntRange>()
            var from = 0
            var len = 0
            for (i in tokens.indices) {
                len += tokens[i].length + 1
                val gap = timed && i + 1 < tokens.size && s.words[i + 1].second - s.words[i].third > 1.2
                if (len > 80 || gap || i == tokens.size - 1) { groups.add(from..i); from = i + 1; len = 0 }
            }
            for (g in groups) {
                val line = g.joinToString(" ") { tokens[it] }
                var a: Double
                var b: Double
                if (timed) {
                    a = s.words[g.first].second
                    b = s.words[g.last].third + 0.25
                } else {
                    val dur = s.end - s.start
                    a = s.start + dur * g.first / tokens.size
                    b = s.start + dur * (g.last + 1) / tokens.size
                }
                if (a < prevEnd) a = prevEnd
                if (b < a + 0.8) b = a + 0.8
                prevEnd = b
                val wrapped = if (line.length > 42) {
                    val cut = line.lastIndexOf(' ', line.length / 2 + 6).takeIf { it > 10 } ?: line.indexOf(' ', line.length / 2)
                    if (cut > 0) line.substring(0, cut) + "\n" + line.substring(cut + 1) else line
                } else line
                sb.append(stamp(a)).append(" --> ").append(stamp(b)).append('\n').append(wrapped).append("\n\n")
            }
        }
        return sb.toString()
    }
}
