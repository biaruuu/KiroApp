package com.biaruuu.kiro.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.biaruuu.kiro.data.stream.StreamSource
import com.biaruuu.kiro.data.stream.SubtitleTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Episodes saved on the phone for offline viewing. The video itself (a plain file or every HLS
 * segment of the chosen quality) is stored by Media3's download manager in one cache; the player
 * reads that same cache, so a downloaded episode plays without any network.
 */
object Downloads {

    class Item(
        val id: String,
        val animeId: Int,
        val episode: Int,
        val title: String,
        val poster: String,
        val episodeTitle: String,
        val thumb: String,
        val overview: String,
        val url: String,
        val headers: Map<String, String>,
        val hls: Boolean,
        val quality: String,
        val state: Int,
        val percent: Float,
        val bytes: Long,
        val waitingForNetwork: Boolean,
        val subtitles: List<SubtitleTrack>,
        val error: String = "",
        val stopReason: Int = 0,
        /** Current speed in bytes per second (0 when not downloading). */
        val speed: Long = 0,
    ) {
        val completed get() = state == Download.STATE_COMPLETED
        val failed get() = state == Download.STATE_FAILED
        val active get() = !completed && !failed
        /** Paused by the user (as opposed to held back by the queue). */
        val paused get() = state == Download.STATE_STOPPED && stopReason == STOP_USER
        val downloading get() = state == Download.STATE_DOWNLOADING
        /** Waiting for its turn behind other downloads. */
        val queued get() = state == Download.STATE_STOPPED && stopReason == STOP_HOLD

        /** Seconds left at the current speed, or -1 when unknown. */
        val secondsLeft: Long
            get() {
                if (!downloading || speed <= 0 || percent < 0.5f) return -1
                val total = bytes * 100.0 / percent
                return ((total - bytes) / speed).toLong().coerceAtLeast(0)
            }
    }

    /** Stop reasons: the user paused it / the queue is holding it back until its turn. */
    const val STOP_USER = 1
    const val STOP_HOLD = 2

    private const val PREFS = "kiro_downloads"
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    private var database: DatabaseProvider? = null
    private var cache: SimpleCache? = null
    private var manager: DownloadManager? = null

    private val errors = HashMap<String, String>()
    private val attempts = HashMap<String, Int>()
    private val maxHeights = HashMap<String, Int>()

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items

    /** Everything unfinished (and failed), in the order it will download. */
    private val _queue = MutableStateFlow<List<Item>>(emptyList())
    val queue: StateFlow<List<Item>> = _queue

    private val lastBytes = HashMap<String, Long>()
    private val lastAt = HashMap<String, Long>()
    private val speeds = HashMap<String, Long>()
    private var orderIds: List<String>? = null

    private fun speedFor(id: String, bytes: Long, state: Int, now: Long): Long {
        if (state != Download.STATE_DOWNLOADING) {
            lastBytes.remove(id); lastAt.remove(id); speeds.remove(id)
            return 0
        }
        val pb = lastBytes[id]
        val pt = lastAt[id]
        if (pb == null || pt == null) {
            lastBytes[id] = bytes; lastAt[id] = now
            return 0
        }
        val dt = now - pt
        if (dt < 800) return speeds[id] ?: 0
        val inst = ((bytes - pb) * 1000 / dt).coerceAtLeast(0)
        val old = speeds[id] ?: 0
        val smooth = if (old == 0L) inst else (old * 0.6 + inst * 0.4).toLong()
        lastBytes[id] = bytes; lastAt[id] = now; speeds[id] = smooth
        return smooth
    }

    // ---- queue order ----

    @Synchronized
    private fun order(context: Context): MutableList<String> {
        orderIds?.let { return it.toMutableList() }
        val out = ArrayList<String>()
        try {
            val arr = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("order", "[]") ?: "[]")
            for (i in 0 until arr.length()) out.add(arr.optString(i))
        } catch (_: Exception) {
        }
        orderIds = out
        return out.toMutableList()
    }

    @Synchronized
    private fun saveOrder(context: Context, ids: List<String>) {
        orderIds = ids.toList()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("order", JSONArray(ids).toString()).apply()
    }

    private fun sendStopReason(app: Context, id: String, reason: Int) {
        try {
            DownloadService.sendSetStopReason(app, DownloadsService::class.java, id, reason, false)
        } catch (_: Exception) {
        }
    }

    /** One download runs at a time: the first of the queue runs, the rest wait their turn. */
    private fun schedule(app: Context, list: List<Item>) {
        val ids = order(app)
        val live = list.filter { it.active }.map { it.id }.toSet()
        val pruned = ids.filter { it in live }
        if (pruned != ids) saveOrder(app, pruned)
        val cands = list.filter { it.active && it.stopReason != STOP_USER }
            .sortedBy { c -> pruned.indexOf(c.id).let { if (it < 0) Int.MAX_VALUE else it } }
        cands.forEachIndexed { i, c ->
            val want = if (i == 0) 0 else STOP_HOLD
            if (c.stopReason != want) sendStopReason(app, c.id, want)
        }
    }

    private fun sortedQueue(app: Context, list: List<Item>): List<Item> {
        val ids = order(app)
        val active = list.filter { it.active }.sortedBy { c -> ids.indexOf(c.id).let { if (it < 0) Int.MAX_VALUE else it } }
        return active + list.filter { it.failed }
    }

    fun pause(context: Context, id: String) = sendStopReason(context.applicationContext, id, STOP_USER)

    fun resume(context: Context, id: String) {
        sendStopReason(context.applicationContext, id, 0)
        main.postDelayed({ refresh(context.applicationContext) }, 400)
    }

    fun pauseAll(context: Context) {
        _queue.value.filter { it.active }.forEach { pause(context, it.id) }
    }

    fun resumeAll(context: Context) {
        _queue.value.filter { it.active && it.paused }.forEach { sendStopReason(context.applicationContext, it.id, 0) }
        main.postDelayed({ refresh(context.applicationContext) }, 400)
    }

    /** Moves a download to the front of the queue (and resumes it when paused). */
    fun prioritize(context: Context, id: String) {
        val app = context.applicationContext
        val ids = _queue.value.filter { it.active }.map { it.id }.toMutableList()
        ids.remove(id); ids.add(0, id)
        saveOrder(app, ids)
        if (_queue.value.firstOrNull { it.id == id }?.paused == true) sendStopReason(app, id, 0)
        refresh(app)
    }

    /** Moves a download one place up (-1) or down (+1) in the queue. */
    fun move(context: Context, id: String, delta: Int) {
        val app = context.applicationContext
        val ids = _queue.value.filter { it.active }.map { it.id }.toMutableList()
        val i = ids.indexOf(id)
        val j = i + delta
        if (i < 0 || j < 0 || j >= ids.size) return
        ids.removeAt(i); ids.add(j, id)
        saveOrder(app, ids)
        refresh(app)
    }

    @Synchronized
    private fun db(context: Context): DatabaseProvider =
        database ?: StandaloneDatabaseProvider(context.applicationContext).also { database = it }

    @Synchronized
    fun cache(context: Context): Cache =
        cache ?: SimpleCache(File(context.applicationContext.filesDir, "downloads"), NoOpCacheEvictor(), db(context)).also { cache = it }

    /** Data source for the player: reads downloaded parts from the cache, everything else from the network. */
    fun dataSourceFactory(context: Context): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(
                DefaultDataSource.Factory(
                    context,
                    com.biaruuu.kiro.data.stream.StreamHeaders.wrap(
                        DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true),
                        com.biaruuu.kiro.data.stream.StreamHeaders.Slot.Playback,
                    ),
                ),
            )
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    @Synchronized
    fun manager(context: Context): DownloadManager {
        manager?.let { return it }
        val app = context.applicationContext
        val dm = DownloadManager(
            app,
            db(app),
            cache(app),
            downloadUpstream(),
            Executors.newFixedThreadPool(4),
        )
        dm.maxParallelDownloads = 1
        dm.requirements = requirements(app)
        dm.addListener(object : DownloadManager.Listener {
            override fun onInitialized(downloadManager: DownloadManager) = refresh(app)
            override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
                val id = download.request.id
                if (download.state == Download.STATE_FAILED) {
                    errors[id] = describe(finalException, download.failureReason)
                    // Streaming servers drop connections now and then: try again a few times
                    // before giving up. Parts already saved are kept.
                    val n = (attempts[id] ?: 0) + 1
                    attempts[id] = n
                    if (n <= 3) {
                        main.postDelayed({
                            try {
                                DownloadService.sendAddDownload(app, DownloadsService::class.java, download.request, true)
                            } catch (_: Exception) {
                            }
                        }, 4000L * n)
                    }
                } else if (download.state == Download.STATE_COMPLETED) {
                    errors.remove(id)
                    attempts.remove(id)
                }
                refresh(app)
            }
            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) = refresh(app)
            override fun onRequirementsStateChanged(downloadManager: DownloadManager, requirements: Requirements, notMetRequirements: Int) = refresh(app)
        })
        manager = dm
        return dm
    }

    private fun describe(e: Exception?, reason: Int): String {
        if (e == null) return "reason $reason"
        var t: Throwable = e
        while (t.cause != null && t.cause !== t) t = t.cause!!
        val code = (t as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
        return if (code != null) "server answered $code" else (t.message ?: t.javaClass.simpleName).take(90)
    }

    private fun downloadUpstream(): DataSource.Factory =
        com.biaruuu.kiro.data.stream.StreamHeaders.wrap(
            DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true),
            com.biaruuu.kiro.data.stream.StreamHeaders.Slot.Download,
        )

    private fun headersJson(h: Map<String, String>) = JSONObject().also { o -> h.forEach { (k, v) -> o.put(k, v) } }

    private fun headersOf(o: JSONObject?): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        o?.keys()?.forEach { k -> out[k] = o.optString(k) }
        return out
    }

    private fun requirements(context: Context) =
        Requirements(if (AppSettings.downloadWifiOnly(context)) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)

    /** While anything is downloading, the percentage and size are re-read every second. */
    private fun startProgressTicker(app: Context) {
        val tick = object : Runnable {
            override fun run() {
                if (_items.value.any { it.active } || manager(app).currentDownloads.isNotEmpty()) {
                    refresh(app)
                    // Over the storage limit: stop what is running so the phone is not filled up.
                    val limit = AppSettings.downloadLimitBytes(app)
                    val over = limit > 0 && _items.value.sumOf { it.bytes } >= limit
                    if (over && manager(app).currentDownloads.isNotEmpty()) {
                        manager(app).pauseDownloads()
                        toast(app, "Download paused: storage limit reached")
                    } else if (!over && manager(app).downloadsPaused) {
                        manager(app).resumeDownloads()
                    }
                }
                main.postDelayed(this, 1000L)
            }
        }
        main.postDelayed(tick, 1000L)
    }

    /** Call at app start so finished downloads are listed and unfinished ones carry on. */
    fun init(context: Context) {
        val app = context.applicationContext
        manager(app)
        refresh(app)
        startProgressTicker(app)
        io.execute {
            try {
                if (manager(app).currentDownloads.isNotEmpty()) {
                    main.post {
                        try {
                            DownloadService.start(app, DownloadsService::class.java)
                        } catch (_: Exception) {
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    /** Applies the Wi-Fi only setting to downloads that are running or waiting. */
    fun applyRequirements(context: Context) {
        manager(context).requirements = requirements(context.applicationContext)
    }

    // ---- state ----

    fun item(animeId: Int, episode: Int): Item? = _items.value.firstOrNull { it.id == idOf(animeId, episode) }

    fun idOf(animeId: Int, episode: Int) = "$animeId:$episode"

    private fun refresh(context: Context) {
        io.execute {
            try {
                val dm = manager(context)
                val metas = loadMeta(context)
                val notMet = dm.notMetRequirements != 0
                val now = System.currentTimeMillis()
                val list = ArrayList<Item>()
                val live = dm.currentDownloads.associateBy { it.request.id }
                dm.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext()) {
                        val d = live[cursor.download.request.id] ?: cursor.download
                        val m = metas.optJSONObject(d.request.id) ?: continue
                        val subs = ArrayList<SubtitleTrack>()
                        val arr = m.optJSONArray("subs")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val s = arr.optJSONObject(i) ?: continue
                                if (File(s.optString("path")).exists()) {
                                    subs.add(SubtitleTrack(Uri.fromFile(File(s.optString("path"))).toString(), s.optString("label"), s.optString("lang"), s.optString("mime")))
                                }
                            }
                        }
                        com.biaruuu.kiro.data.stream.StreamHeaders.remember(m.optString("url"), headersOf(m.optJSONObject("headers")))
                        list.add(
                            Item(
                                id = d.request.id,
                                animeId = m.optInt("animeId"),
                                episode = m.optInt("episode"),
                                title = m.optString("title"),
                                poster = m.optString("poster"),
                                episodeTitle = m.optString("epTitle"),
                                thumb = m.optString("thumb"),
                                overview = m.optString("overview"),
                                url = m.optString("url"),
                                headers = headersOf(m.optJSONObject("headers")),
                                hls = m.optBoolean("hls"),
                                quality = m.optString("quality"),
                                state = d.state,
                                percent = d.percentDownloaded.coerceAtLeast(0f),
                                bytes = d.bytesDownloaded,
                                waitingForNetwork = notMet && d.state == Download.STATE_QUEUED,
                                subtitles = subs,
                                error = errors[d.request.id].orEmpty(),
                                stopReason = d.stopReason,
                                speed = speedFor(d.request.id, d.bytesDownloaded, d.state, now),
                            ),
                        )
                    }
                }
                _items.value = list.sortedWith(compareBy({ it.title }, { it.episode }))
                _queue.value = sortedQueue(context, list)
                schedule(context, list)
            } catch (_: Exception) {
            }
        }
    }

    // ---- starting and removing ----

    /** Saves [source] for offline viewing. [maxHeight] limits the picture quality (0 = best). */
    fun start(
        context: Context,
        animeId: Int,
        title: String,
        poster: String,
        episode: Int,
        episodeTitle: String,
        thumbnail: String,
        overview: String,
        source: StreamSource,
        maxHeight: Int,
        onError: (String) -> Unit,
    ) {
        val app = context.applicationContext
        val limit = AppSettings.downloadLimitBytes(app)
        if (limit > 0 && _items.value.sumOf { it.bytes } >= limit) {
            main.post { onError("Storage limit reached. Raise it in Me > Download > Storage limit.") }
            return
        }
        val id = idOf(animeId, episode)
        errors.remove(id)
        attempts.remove(id)
        val mediaItem = MediaItem.Builder()
            .setUri(source.url)
            .apply { if (source.isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        main.post {
            com.biaruuu.kiro.data.stream.StreamHeaders.activate(
                com.biaruuu.kiro.data.stream.StreamHeaders.Slot.Download, source.url, source.headers,
            )
            val helper = DownloadHelper.forMediaItem(app, mediaItem, DefaultRenderersFactory(app), downloadUpstream())
            helper.prepare(object : DownloadHelper.Callback {
                override fun onPrepared(helper: DownloadHelper) {
                    try {
                        if (source.isHls && maxHeight > 0 && helper.periodCount > 0) {
                            helper.clearTrackSelections(0)
                            helper.addTrackSelection(
                                0,
                                DefaultTrackSelector.Parameters.Builder(app)
                                    .setMaxVideoSize(Int.MAX_VALUE, maxHeight)
                                    .setForceHighestSupportedBitrate(true)
                                    .build(),
                            )
                        }
                        val request = helper.getDownloadRequest(id, null)
                        helper.release()
                        saveMeta(app, id, JSONObject()
                            .put("animeId", animeId).put("episode", episode)
                            .put("title", title).put("poster", poster).put("epTitle", episodeTitle)
                            .put("thumb", thumbnail).put("overview", overview)
                            .put("url", source.url).put("hls", source.isHls)
                            .put("quality", source.qualityLabel)
                            .put("headers", headersJson(source.headers)))
                        order(app).let { o -> if (id !in o) saveOrder(app, o + id) }
                        DownloadService.sendAddDownload(app, DownloadsService::class.java, request, true)
                        saveSubtitles(app, id, source)
                        refresh(app)
                    } catch (e: Exception) {
                        helper.release()
                        onError("Couldn't start the download.")
                    }
                }

                override fun onPrepareError(helper: DownloadHelper, e: java.io.IOException) {
                    helper.release()
                    onError("Couldn't start the download. Check your connection.")
                }
            })
        }
    }

    private fun saveSubtitles(context: Context, id: String, source: StreamSource) {
        if (source.subtitles.isEmpty()) return
        val hdrs = source.headers
        io.execute {
            val dir = File(context.filesDir, "dl_subs").apply { mkdirs() }
            val saved = JSONArray()
            source.subtitles.forEachIndexed { i, t ->
                try {
                    val ext = if (t.mimeType.contains("vtt", true)) "vtt" else "srt"
                    val file = File(dir, "${id.replace(':', '_')}_$i.$ext")
                    HttpClientProvider.get().newCall(Request.Builder().url(t.url).apply { hdrs.forEach { (k, v) -> header(k, v) } }.build()).execute().use { r ->
                        if (!r.isSuccessful) return@use
                        r.body?.byteStream()?.use { input -> file.outputStream().use { input.copyTo(it) } }
                    }
                    if (file.length() > 0) {
                        saved.put(JSONObject().put("path", file.absolutePath).put("label", t.label).put("lang", t.language).put("mime", t.mimeType))
                    }
                } catch (_: Exception) {
                }
            }
            val meta = loadMeta(context)
            meta.optJSONObject(id)?.put("subs", saved)
            storeMeta(context, meta)
            refresh(context)
        }
    }

    /** Tries a failed download again. */
    fun retry(context: Context, item: Item) {
        val source = StreamSource("Saved", item.url, 0, item.hls, emptyList()).withHeaders(item.headers)
        start(context, item.animeId, item.title, item.poster, item.episode, item.episodeTitle, item.thumb, item.overview, source, 0) { toast(context, it) }
    }

    fun remove(context: Context, animeId: Int, episode: Int) {
        val app = context.applicationContext
        val id = idOf(animeId, episode)
        DownloadService.sendRemoveDownload(app, DownloadsService::class.java, id, false)
        dropMeta(app, id)
    }

    /** Deletes every downloaded episode of one series. */
    fun removeSeries(context: Context, animeId: Int) {
        _items.value.filter { it.animeId == animeId }.forEach { remove(context, it.animeId, it.episode) }
    }

    fun removeAll(context: Context) {
        val app = context.applicationContext
        DownloadService.sendRemoveAllDownloads(app, DownloadsService::class.java, false)
        storeMeta(app, JSONObject())
        File(app.filesDir, "dl_subs").deleteRecursively()
        _items.value = emptyList()
    }

    /** A finished download as a playable source, or null. */
    fun playbackSource(context: Context, animeId: Int, episode: Int): StreamSource? {
        val item = item(animeId, episode)?.takeIf { it.completed } ?: return null
        return StreamSource("Offline", item.url, 0, item.hls, item.subtitles).withHeaders(item.headers)
    }

    // ---- small metadata store (title, poster, source address) ----

    @Synchronized
    private fun loadMeta(context: Context): JSONObject = try {
        JSONObject(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("meta", "{}") ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    @Synchronized
    private fun storeMeta(context: Context, meta: JSONObject) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("meta", meta.toString()).apply()
    }

    @Synchronized
    private fun saveMeta(context: Context, id: String, value: JSONObject) {
        val meta = loadMeta(context)
        meta.put(id, value)
        storeMeta(context, meta)
    }

    @Synchronized
    private fun dropMeta(context: Context, id: String) {
        val meta = loadMeta(context)
        meta.optJSONObject(id)?.optJSONArray("subs")?.let { arr ->
            for (i in 0 until arr.length()) File(arr.optJSONObject(i)?.optString("path").orEmpty()).delete()
        }
        meta.remove(id)
        storeMeta(context, meta)
        refresh(context)
    }

    fun toast(context: Context, message: String) {
        main.post { com.biaruuu.kiro.ui.i18n.I18nToast.make(context.applicationContext, message, Toast.LENGTH_LONG).show() }
    }
}
