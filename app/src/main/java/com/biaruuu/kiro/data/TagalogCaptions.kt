package com.biaruuu.kiro.data

import android.content.Context
import android.os.Handler
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

    private const val CHUNK_MS = 8L * 60 * 1000
    /** A full 8 minute piece at 48 kbps is about 2.9 MB; anything clearly smaller was the end of the episode. */
    private const val FULL_CHUNK_BYTES = 2_400_000L

    /** Runs the whole job. [progress] gets short status lines; [done] gets null on success or an error. */
    fun generate(context: Context, e: TagalogDub.Entry, progress: (String) -> Unit, done: (String?) -> Unit) {
        fun finish(err: String?) { main.post { done(err) } }
        fun say(s: String) { main.post { progress(s) } }
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
            // The episode is listened to in 8 minute pieces: long audio makes the service skip speech and drift.
            fun step(i: Int) {
                say(if (i == 0) "Getting the sound…" else "Listening… part ${i + 1}")
                extractAudio(context, e.url, i * CHUNK_MS, (i + 1) * CHUNK_MS) { file, err ->
                    if (file == null || file.length() < 15_000) {
                        file?.delete()
                        if (i == 0) return@extractAudio finish(err ?: "Could not read the sound of this video.")
                        return@extractAudio complete()
                    }
                    val wasLast = file.length() < FULL_CHUNK_BYTES
                    Thread {
                        try {
                            say("Listening… part ${i + 1}")
                            all.addAll(transcribe(cfg, file, i * CHUNK_MS / 1000.0))
                            main.post { if (wasLast || i >= 60) complete() else step(i + 1) }
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

    /** One piece of the video -> a small AAC file with only the sound, made on this phone. */
    private fun extractAudio(context: Context, url: String, fromMs: Long, toMs: Long, done: (File?, String?) -> Unit) {
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
            } catch (ex: Exception) {
                done(null, ex.message)
            }
        }
    }

    private class Seg(val start: Double, val end: Double, val text: String, val words: List<Triple<String, Double, Double>>)

    private fun transcribe(cfg: Config, file: File, offset: Double): List<Seg> {
        // No "prompt": with music or silence the service tends to print the prompt back as if it were speech.
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "audio.m4a", file.asRequestBody("audio/mp4".toMediaType()))
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
