package com.biaruuu.kiro.data;

import androidx.annotation.NonNull;

/** A prequel or sequel of an anime, used by the season picker on the details page. */
public final class RelatedAnime {

    private final int id;
    private final String title;
    private final String relationType;

    public RelatedAnime(int id, @NonNull String title, @NonNull String relationType) {
        this.id = id;
        this.title = title;
        this.relationType = relationType;
    }

    public int getId() {
        return id;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    /** AniList relation: "PREQUEL" or "SEQUEL". */
    @NonNull
    public String getRelationType() {
        return relationType;
    }

    public boolean isPrequel() {
        return "PREQUEL".equals(relationType);
    }
}
