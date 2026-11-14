package com.biaruuu.kiro.data;

import androidx.annotation.NonNull;

/** One entry of the season dropdown: a TV season, part, ONA or movie of the same franchise. */
public final class SeasonEntry {

    private final int id;
    private final String title;
    private final int sortKey;

    public SeasonEntry(int id, @NonNull String title, int sortKey) {
        this.id = id;
        this.title = title;
        this.sortKey = sortKey;
    }

    public int getId() {
        return id;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    /** yyyymmdd of the first air date; 0 when unknown. */
    public int getSortKey() {
        return sortKey;
    }
}
