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
import androidx.media3.transformer.ProgressHolder
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
    private const val MAX_UPLOAD = 24L * 1024 * 1024
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

    /** Runs the whole job. [progress] gets short status lines; [done] gets null on success or an error. */
    fun generate(context: Context, e: TagalogDub.Entry, progress: (String) -> Unit, done: (String?) -> Unit) {
        fun finish(err: String?) = main.post { done(err) }
        fun say(s: String) = main.post { progress(s) }
        loadConfig { cfg ->
            if (cfg == null) return@loadConfig finish("Save your transcription key first.")
            say("Getting the sound…")
            extractAudio(context, e.url, { say("Getting the sound… $it%") }) { file, err ->
                if (file == null) return@extractAudio finish(err ?: "Could not read the sound of this video.")
                Thread {
                    try {
                        if (file.length() > MAX_UPLOAD) {
                            finish("The sound is too long for one request (${file.length() / 1_000_000} MB). Use a shorter episode.")
                            return@Thread
                        }
                        say("Listening… this takes a minute or two")
                        val vtt = transcribe(cfg, file)
                        if (vtt == null) { finish("The service heard no speech."); return@Thread }
                        say("Saving…")
                        FirebaseFirestore.getInstance().collection("tagdub").document(e.id).update("captions", vtt)
                            .addOnSuccessListener { finish(null) }.addOnFailureListener { finish(it.message ?: "Could not save") }
                    } catch (ex: Exception) {
                        finish(ex.message ?: "Failed")
                    } finally {
                        file.delete()
                    }
                }.start()
            }
        }
    }

    /** Video address -> a small mono-ish AAC file with only the sound, made on this phone. */
    private fun extractAudio(context: Context, url: String, onPercent: (Int) -> Unit, done: (File?, String?) -> Unit) {
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
                val item = EditedMediaItem.Builder(MediaItem.fromUri(url)).setRemoveVideo(true).build()
                transformer.start(item, out.absolutePath)
                val holder = ProgressHolder()
                val poll = object : Runnable {
                    override fun run() {
                        if (transformer.getProgress(holder) != Transformer.PROGRESS_STATE_NOT_STARTED) onPercent(holder.progress)
                        if (out.exists() && out.length() > 0 && holder.progress >= 100) return
                        main.postDelayed(this, 1500)
                    }
                }
                main.postDelayed(poll, 1500)
            } catch (ex: Exception) {
                done(null, ex.message)
            }
        }
    }

    private class Seg(val start: Double, val end: Double, val text: String)

    private fun transcribe(cfg: Config, file: File): String? {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "audio.m4a", file.asRequestBody("audio/mp4".toMediaType()))
            .addFormDataPart("model", cfg.model)
            .addFormDataPart("language", "tl")
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("temperature", "0")
            .addFormDataPart("prompt", "Ito ay Tagalog na dub ng isang anime. Mga pangungusap sa Filipino, may halong English paminsan-minsan.")
            .build()
        val req = Request.Builder().url(cfg.endpoint).header("Authorization", "Bearer ${cfg.key}").post(body).build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IllegalStateException("Service said ${r.code}: ${text.take(160)}")
            val arr = JSONObject(text).optJSONArray("segments") ?: return null
            val keep = ArrayList<Seg>()
            var last = ""
            var repeats = 0
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val t = s.optString("text").trim()
                if (t.isEmpty()) continue
                val noSpeech = s.optDouble("no_speech_prob", 0.0)
                val logprob = s.optDouble("avg_logprob", 0.0)
                val ratio = s.optDouble("compression_ratio", 1.0)
                // Whisper invents lines over silence, music and effects; these three tell-tale signs drop them.
                if (noSpeech > 0.6 && logprob < -0.8) continue
                if (logprob < -1.3 || ratio > 2.4) continue
                if (t.equals(last, ignoreCase = true)) { if (++repeats >= 2) continue } else { last = t; repeats = 0 }
                keep.add(Seg(s.optDouble("start"), s.optDouble("end"), t))
            }
            if (keep.isEmpty()) return null
            return toVtt(keep)
        }
    }

    private fun stamp(t: Double): String {
        val ms = (t * 1000).toLong().coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000)
    }

    /** Long lines are cut into readable captions (two short lines at most), sharing the time by words. */
    private fun toVtt(segs: List<Seg>): String {
        val sb = StringBuilder("WEBVTT\n\n")
        for (s in segs) {
            val words = s.text.split(Regex("\\s+"))
            val chunks = ArrayList<List<String>>()
            var cur = ArrayList<String>()
            var len = 0
            for (w in words) {
                if (len + w.length > 80 && cur.isNotEmpty()) { chunks.add(cur); cur = ArrayList(); len = 0 }
                cur.add(w); len += w.length + 1
            }
            if (cur.isNotEmpty()) chunks.add(cur)
            val total = words.size.coerceAtLeast(1)
            var from = s.start
            for (c in chunks) {
                val to = (from + (s.end - s.start) * c.size / total).coerceAtLeast(from + 0.8)
                val line = c.joinToString(" ")
                val wrapped = if (line.length > 42) {
                    val cut = line.lastIndexOf(' ', line.length / 2 + 6).takeIf { it > 10 } ?: line.indexOf(' ', line.length / 2).takeIf { it > 0 } ?: -1
                    if (cut > 0) line.substring(0, cut) + "\n" + line.substring(cut + 1) else line
                } else line
                sb.append(stamp(from)).append(" --> ").append(stamp(to)).append('\n').append(wrapped).append("\n\n")
                from = to
            }
        }
        return sb.toString()
    }
}
