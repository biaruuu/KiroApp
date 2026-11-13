package com.biaruuu.kiro.data;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

/** One page of search results. */
public final class SearchResult {

    private final List<Anime> items;
    private final boolean hasNext;

    public SearchResult(@NonNull List<Anime> items, boolean hasNext) {
        this.items = Collections.unmodifiableList(items);
        this.hasNext = hasNext;
    }

    @NonNull
    public List<Anime> getItems() {
        return items;
    }

    public boolean hasNext() {
        return hasNext;
    }
}
