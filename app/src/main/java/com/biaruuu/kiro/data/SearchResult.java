package com.biaruuu.kiro.data;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

/** One page of search results. */
public final class SearchResult {

    private final List<Anime> items;
    private final boolean hasNext;
    private final int total;

    /**
     * @param total number of matches across all pages, or 0 when AniList did not say
     */
    public SearchResult(@NonNull List<Anime> items, boolean hasNext, int total) {
        this.items = Collections.unmodifiableList(items);
        this.hasNext = hasNext;
        this.total = total;
    }

    /** Number of matches across all pages (what the "76 Results" label shows). */
    public int getTotal() {
        return total;
    }

    @NonNull
    public List<Anime> getItems() {
        return items;
    }

    public boolean hasNext() {
        return hasNext;
    }
}
