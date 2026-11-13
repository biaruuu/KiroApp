package com.biaruuu.kiro.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/** One anime as returned by AniList. Immutable. */
public final class Anime {

    private final int id;
    private final String titleRomaji;
    private final String titleEnglish;
    private final String titleNative;
    private final String coverLarge;
    private final String coverExtraLarge;
    private final String coverColor;
    private final String bannerImage;
    private final String description;
    private final int averageScore;
    private final int episodes;
    private final String format;
    private final int seasonYear;
    private final List<String> genres;
    private final String status;

    public Anime(
            int id,
            @Nullable String titleRomaji,
            @Nullable String titleEnglish,
            @Nullable String titleNative,
            @Nullable String coverLarge,
            @Nullable String coverExtraLarge,
            @Nullable String coverColor,
            @Nullable String bannerImage,
            @Nullable String description,
            int averageScore,
            int episodes,
            @Nullable String format,
            int seasonYear,
            @NonNull List<String> genres,
            @Nullable String status) {
        this.id = id;
        this.titleRomaji = titleRomaji;
        this.titleEnglish = titleEnglish;
        this.titleNative = titleNative;
        this.coverLarge = coverLarge;
        this.coverExtraLarge = coverExtraLarge;
        this.coverColor = coverColor;
        this.bannerImage = bannerImage;
        this.description = description;
        this.averageScore = averageScore;
        this.episodes = episodes;
        this.format = format;
        this.seasonYear = seasonYear;
        this.genres = Collections.unmodifiableList(genres);
        this.status = status;
    }

    public int getId() {
        return id;
    }

    /** English title if there is one, otherwise romaji, otherwise native. */
    @NonNull
    public String getDisplayTitle() {
        if (notBlank(titleEnglish)) return titleEnglish;
        if (notBlank(titleRomaji)) return titleRomaji;
        if (notBlank(titleNative)) return titleNative;
        return "Untitled";
    }

    /** Medium poster for cards. */
    @Nullable
    public String getPosterUrl() {
        return coverLarge != null ? coverLarge : coverExtraLarge;
    }

    /** Large portrait cover for the hero on narrow screens. */
    @Nullable
    public String getHeroUrl() {
        return coverExtraLarge != null ? coverExtraLarge : coverLarge;
    }

    /** Wide banner, or null when AniList has none. */
    @Nullable
    public String getBannerUrl() {
        return bannerImage;
    }

    /** Dominant cover colour as "#RRGGBB", or null. */
    @Nullable
    public String getCoverColor() {
        return coverColor;
    }

    /** Raw AniList description (contains HTML), or null. */
    @Nullable
    public String getDescription() {
        return description;
    }

    /** AniList score 0-100, or 0 when unrated. */
    public int getAverageScore() {
        return averageScore;
    }

    /** Episode count, or 0 when unknown. */
    public int getEpisodes() {
        return episodes;
    }

    @Nullable
    public String getFormat() {
        return format;
    }

    /** Year, or 0 when unknown. */
    public int getSeasonYear() {
        return seasonYear;
    }

    @NonNull
    public List<String> getGenres() {
        return genres;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    private static boolean notBlank(@Nullable String s) {
        return s != null && !s.trim().isEmpty();
    }
}
