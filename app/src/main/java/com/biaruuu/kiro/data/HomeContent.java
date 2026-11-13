package com.biaruuu.kiro.data;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

/** Everything the Home screen needs: hero carousel items plus the poster rows. */
public final class HomeContent {

    private final List<Anime> hero;
    private final List<HomeSection> sections;

    public HomeContent(@NonNull List<Anime> hero, @NonNull List<HomeSection> sections) {
        this.hero = Collections.unmodifiableList(hero);
        this.sections = Collections.unmodifiableList(sections);
    }

    @NonNull
    public List<Anime> getHero() {
        return hero;
    }

    @NonNull
    public List<HomeSection> getSections() {
        return sections;
    }
}
