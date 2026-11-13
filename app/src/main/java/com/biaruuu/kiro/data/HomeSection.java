package com.biaruuu.kiro.data;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

/** A titled row of anime on the Home screen. */
public final class HomeSection {

    private final String title;
    private final List<Anime> items;
    private final boolean ranked;

    public HomeSection(@NonNull String title, @NonNull List<Anime> items) {
        this(title, items, false);
    }

    /**
     * @param ranked true for a "Top 10" style row that shows 1, 2, 3... beside each poster
     */
    public HomeSection(@NonNull String title, @NonNull List<Anime> items, boolean ranked) {
        this.title = title;
        this.items = Collections.unmodifiableList(items);
        this.ranked = ranked;
    }

    public boolean isRanked() {
        return ranked;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public List<Anime> getItems() {
        return items;
    }
}
