package com.biaruuu.kiro.data

import android.content.Context

/** "Because you watched X" rows for Home, from AniList's recommendations for what you watched lately. */
object BecauseYouWatched {
    class Row(val title: String, val items: List<Anime>)

    /** Blocking; run off the main thread. [seeds] are (anime id, title) of recently watched anime. */
    fun load(c: Context, seeds: List<Pair<Int, String>>): List<Row> {
        val known = HashSet<Int>()
        MyListStore.all(c).forEach { known.add(it.id) }
        WatchHistory.all(c).forEach { known.add(it.animeId) }
        WatchedStore.completed(c).forEach { known.add(it.id) }
        MyListStore.allRated(c).forEach { known.add(it.id) }
        val rows = ArrayList<Row>()
        for ((id, title) in seeds) {
            val recs = AniListExtra.recommendations(id).filter { it.id !in known && it.id != id }
            if (recs.size >= 3) rows.add(Row("Because you watched $title", recs.take(12)))
        }
        return rows
    }
}
