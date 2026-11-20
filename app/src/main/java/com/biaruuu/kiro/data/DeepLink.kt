package com.biaruuu.kiro.data

import kotlinx.coroutines.flow.MutableStateFlow

/** A page to open as soon as the app is on screen (from a notification), such as "d:123" for an anime. */
object DeepLink {
    val request = MutableStateFlow<String?>(null)

    /** A friend's recommendation that arrived with a link: shown as a small bar until dismissed. */
    class Recommendation(val animeId: Int, val from: String, val note: String)

    val recommendation = MutableStateFlow<Recommendation?>(null)

    /** Link that opens an anime in Kiro, with an optional sender name and one-line note. */
    fun animeLink(id: Int, from: String = "", note: String = ""): String {
        val q = buildList {
            if (from.isNotBlank()) add("from=" + android.net.Uri.encode(from.trim().take(30)))
            if (note.isNotBlank()) add("note=" + android.net.Uri.encode(note.trim().take(80)))
        }
        return "kiro://anime/$id" + if (q.isEmpty()) "" else "?" + q.joinToString("&")
    }

    fun profileLink(uid: String) = "kiro://profile/$uid"

    /** Handles a kiro:// link. Returns true when it was one of ours. */
    fun handle(uri: android.net.Uri?): Boolean {
        if (uri == null || uri.scheme != "kiro") return false
        when (uri.host) {
            "anime" -> {
                val id = uri.lastPathSegment?.toIntOrNull() ?: return false
                val note = uri.getQueryParameter("note").orEmpty().take(80)
                if (note.isNotBlank()) {
                    recommendation.value = Recommendation(id, uri.getQueryParameter("from").orEmpty().take(30), note)
                }
                request.value = "d:$id"
            }
            "profile" -> {
                val uid = uri.lastPathSegment?.takeIf { it.length in 6..64 && it.all { ch -> ch.isLetterOrDigit() } } ?: return false
                request.value = "s:user-$uid"
            }
            else -> return false
        }
        return true
    }
}
