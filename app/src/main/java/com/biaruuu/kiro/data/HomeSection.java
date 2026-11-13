package com.biaruuu.kiro.data;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

/** A titled row of anime on the Home screen. */
public final class HomeSection {

    private final String title;
    private final List<Anime> items;

    public HomeSection(@NonNull String title, @NonNull List<Anime> items) {
        this.title = title;
        this.items = Collections.unmodifiableList(items);
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
