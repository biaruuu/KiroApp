package com.biaruuu.kiro.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps My List, ratings and watch history in Cloud Firestore (users/{uid}/data/library).
 * The phone keeps using its local copy for the UI; every local change is pushed up a moment
 * later, and a snapshot listener pulls changes made on another device in real time.
 * Firestore's offline cache queues writes while there is no connection.
 */
object CloudSync {

    private const val PREFS = "kiro_user_data"
    private const val MERGED_UID = "cloud_merged_uid"

    private val main = Handler(Looper.getMainLooper())
    private val _version = MutableStateFlow(0)

    /** Bumps whenever the library changed because of the cloud (not because of this phone). */
    val version: StateFlow<Int> = _version.asStateFlow()

    private var appContext: Context? = null
    private var doc: DocumentReference? = null
    private var listener: ListenerRegistration? = null
    private var uid: String? = null
    private var seq = 0
    private var dirty = false
    private val push = Runnable { pushNow() }

    @JvmStatic
    fun start(context: Context, userId: String) {
        if (uid == userId && listener != null) return
        stop()
        val app = context.applicationContext
        appContext = app
        uid = userId
        val ref = FirebaseFirestore.getInstance().collection("users").document(userId)
            .collection("data").document("library")
        doc = ref
        listener = ref.addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            if (!snap.exists()) {
                if (!snap.metadata.isFromCache) {
                    // First time on this account: upload what this phone already has.
                    markMerged(app, userId)
                    schedulePush(0)
                }
                return@addSnapshotListener
            }
            if (snap.metadata.hasPendingWrites() || dirty) return@addSnapshotListener
            applyRemote(app, userId, snap.getString("myList"), snap.getString("ratings"),
                snap.getString("ratedMeta"), snap.getString("history"), snap.getString("watched"), snap.getString("status"), snap.getString("stats"))
        }
    }

    /** Saving to the library needs an account (unless this build has no accounts at all). */
    @JvmStatic
    fun allowed(context: Context): Boolean =
        uid != null || !com.biaruuu.kiro.data.auth.AuthRepository.isConfigured(context)

    /** Tells a logged-out user why nothing was saved and opens the login page. */
    @JvmStatic
    fun requireLogin(context: Context) {
        com.biaruuu.kiro.ui.i18n.I18nToast.make(context, "Log in to save to your library", android.widget.Toast.LENGTH_SHORT).show()
        com.biaruuu.kiro.data.auth.AuthRepository.requestLogin()
    }

    @JvmStatic
    fun stop() {
        main.removeCallbacks(push)
        listener?.remove()
        listener = null
        doc = null
        uid = null
        dirty = false
    }

    /** Signing out: forget the account's library on this phone. */
    @JvmStatic
    fun clearLocal(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().remove("my_list").remove("ratings").remove("rated_meta").remove("watch_history").remove("watched").remove("status").remove("stats")
            .remove(MERGED_UID).apply()
        _version.value++
        WatchedStore.notifyChanged()
    }

    /** Called by the local stores after every write. */
    @JvmStatic
    fun libraryChanged(context: Context) {
        if (uid == null) return
        appContext = context.applicationContext
        seq++
        dirty = true
        schedulePush(700)
    }

    private fun schedulePush(delay: Long) {
        main.removeCallbacks(push)
        main.postDelayed(push, delay)
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun markMerged(c: Context, id: String) {
        prefs(c).edit().putString(MERGED_UID, id).apply()
    }

    private fun pushNow() {
        val ref = doc ?: return
        val c = appContext ?: return
        val p = prefs(c)
        val sent = seq
        val data = hashMapOf<String, Any>(
            "myList" to p.getString("my_list", "{}")!!,
            "ratings" to p.getString("ratings", "{}")!!,
            "ratedMeta" to p.getString("rated_meta", "{}")!!,
            "history" to p.getString("watch_history", "[]")!!,
            "watched" to p.getString("watched", "{}")!!,
            "status" to p.getString("status", "{}")!!,
            "stats" to p.getString("stats", "{}")!!,
            "updatedAt" to System.currentTimeMillis(),
        )
        ref.set(data).addOnCompleteListener { t ->
            if (t.isSuccessful && sent == seq) dirty = false
            else if (!t.isSuccessful) main.postDelayed(push, 5000)
        }
    }

    private fun applyRemote(c: Context, id: String, list: String?, ratings: String?, meta: String?, history: String?, watched: String?, status: String?, stats: String?) {
        val p = prefs(c)
        val firstOnDevice = p.getString(MERGED_UID, null) != id
        var l = list ?: "{}"
        var r = ratings ?: "{}"
        var m = meta ?: "{}"
        var h = history ?: "[]"
        var w = watched ?: "{}"
        var st = status ?: "{}"
        var sx = stats ?: "{}"
        if (firstOnDevice) {
            // Signed in on this phone for the first time: keep what was saved here as a guest too.
            l = mergeObjects(p.getString("my_list", "{}"), l, "at")
            r = mergeObjects(p.getString("ratings", "{}"), r, null)
            m = mergeObjects(p.getString("rated_meta", "{}"), m, null)
            h = mergeHistory(p.getString("watch_history", "[]"), h)
            w = mergeObjects(p.getString("watched", "{}"), w, "at")
            st = mergeObjects(p.getString("status", "{}"), st, "at")
            sx = WatchStats.merge(p.getString("stats", "{}"), sx)
        }
        val changed = l != p.getString("my_list", "{}") || r != p.getString("ratings", "{}") ||
            m != p.getString("rated_meta", "{}") || h != p.getString("watch_history", "[]") ||
            w != p.getString("watched", "{}") || st != p.getString("status", "{}") ||
            sx != p.getString("stats", "{}")
        if (changed) {
            p.edit().putString("my_list", l).putString("ratings", r)
                .putString("rated_meta", m).putString("watch_history", h).putString("watched", w).putString("status", st).putString("stats", sx).apply()
            _version.value++
            WatchStats.notifyChanged()
            WatchedStore.notifyChanged()
        }
        if (firstOnDevice) {
            markMerged(c, id)
            if (changed) { seq++; dirty = true; schedulePush(0) }
        }
    }

    /**
     * Merges a backup (the "remote" side: it wins ties) into this phone's library and syncs it
     * when signed in. Returns false when nothing changed.
     */
    fun mergeBackup(c: Context, b: Backup.Data): Boolean {
        val p = prefs(c)
        val l = mergeObjects(p.getString("my_list", "{}"), b.myList, "at")
        val r = mergeObjects(p.getString("ratings", "{}"), b.ratings, null)
        val m = mergeObjects(p.getString("rated_meta", "{}"), b.ratedMeta, null)
        val h = mergeHistory(p.getString("watch_history", "[]"), b.history)
        val w = mergeObjects(p.getString("watched", "{}"), b.watched, "at")
        val st = mergeObjects(p.getString("status", "{}"), b.status, "at")
        val sx = WatchStats.merge(p.getString("stats", "{}"), b.stats)
        val changed = l != p.getString("my_list", "{}") || r != p.getString("ratings", "{}") ||
            m != p.getString("rated_meta", "{}") || h != p.getString("watch_history", "[]") ||
            w != p.getString("watched", "{}") || st != p.getString("status", "{}") ||
            sx != p.getString("stats", "{}")
        if (!changed) return false
        p.edit().putString("my_list", l).putString("ratings", r).putString("rated_meta", m)
            .putString("watch_history", h).putString("watched", w).putString("status", st).putString("stats", sx).apply()
        _version.value++
        WatchStats.notifyChanged()
        WatchedStore.notifyChanged()
        libraryChanged(c)
        return true
    }

    /** Union of two JSON objects keyed by anime id; for a clash the newer [timeKey] wins. */
    internal fun mergeObjects(localJson: String?, remoteJson: String, timeKey: String?): String = try {
        val out = JSONObject(remoteJson)
        val local = JSONObject(localJson ?: "{}")
        val keys = local.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (!out.has(k)) {
                out.put(k, local.get(k))
            } else if (timeKey != null) {
                val a = local.optJSONObject(k)?.optLong(timeKey) ?: 0L
                val b = out.optJSONObject(k)?.optLong(timeKey) ?: 0L
                if (a > b) out.put(k, local.get(k))
            }
        }
        out.toString()
    } catch (e: Exception) {
        remoteJson
    }

    internal fun mergeHistory(localJson: String?, remoteJson: String): String = try {
        val byAnime = LinkedHashMap<Int, JSONObject>()
        for (src in listOf(remoteJson, localJson ?: "[]")) {
            val arr = JSONArray(src)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val a = o.getInt("a")
                val old = byAnime[a]
                if (old == null || o.optLong("u") > old.optLong("u")) byAnime[a] = o
            }
        }
        val sorted = byAnime.values.sortedByDescending { it.optLong("u") }.take(20)
        JSONArray().apply { sorted.forEach { put(it) } }.toString()
    } catch (e: Exception) {
        remoteJson
    }
}
