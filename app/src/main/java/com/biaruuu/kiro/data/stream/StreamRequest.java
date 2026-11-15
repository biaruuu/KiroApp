package com.biaruuu.kiro.data.stream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** What to play: one episode of one anime. */
public final class StreamRequest {

    private final int animeId;
    private final int episode;
    private final String title;
    private final String titleEnglish;
    private final String titleRomaji;
    private final int year;
    private final int malId;
    private final int totalEpisodes;

    public StreamRequest(
            int animeId,
            int episode,
            @NonNull String title,
            @Nullable String titleEnglish,
            @Nullable String titleRomaji,
            int year,
            int malId,
            int totalEpisodes) {
        this.animeId = animeId;
        this.episode = episode;
        this.title = title;
        this.titleEnglish = titleEnglish;
        this.titleRomaji = titleRomaji;
        this.year = year;
        this.malId = malId;
        this.totalEpisodes = totalEpisodes;
    }

    public int getAnimeId() {
        return animeId;
    }

    public int getEpisode() {
        return episode;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getTitleEnglish() {
        return titleEnglish;
    }

    @Nullable
    public String getTitleRomaji() {
        return titleRomaji;
    }

    public int getYear() {
        return year;
    }

    /** MyAnimeList id of this exact season (0 if unknown). */
    public int getMalId() {
        return malId;
    }

    /** Episode count of this season from AniList (0 if unknown). */
    public int getTotalEpisodes() {
        return totalEpisodes;
    }
}
