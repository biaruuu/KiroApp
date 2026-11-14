package com.biaruuu.kiro.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** One row of the episode list. */
public final class EpisodeInfo {

    private final int number;
    private final String title;
    private final String thumbnailUrl;
    private final int durationMinutes;
    private final String airDate;
    private final String overview;

    public EpisodeInfo(
            int number,
            @Nullable String title,
            @Nullable String thumbnailUrl,
            int durationMinutes,
            @Nullable String airDate) {
        this(number, title, thumbnailUrl, durationMinutes, airDate, null);
    }

    public EpisodeInfo(
            int number,
            @Nullable String title,
            @Nullable String thumbnailUrl,
            int durationMinutes,
            @Nullable String airDate,
            @Nullable String overview) {
        this.overview = overview;
        this.number = number;
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
        this.durationMinutes = durationMinutes;
        this.airDate = airDate;
    }

    /** Short plot of the episode, or null. */
    @Nullable
    public String getOverview() {
        return overview;
    }

    public int getNumber() {
        return number;
    }

    /** Episode name, or null when AniList does not have one. */
    @Nullable
    public String getTitle() {
        return title;
    }

    /** "3. The Red-Nosed Reindeer", or "Episode 3" when there is no name. */
    @NonNull
    public String getHeading() {
        return title != null ? number + ". " + title : "Episode " + number;
    }

    @Nullable
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    /** Minutes, or 0 when unknown. */
    public int getDurationMinutes() {
        return durationMinutes;
    }

    /** "2012.07.08", or null when the air date is unknown. */
    @Nullable
    public String getAirDate() {
        return airDate;
    }

    /** "23m · 2012.07.08", skipping whichever part is unknown. */
    @NonNull
    public String getSubtitle() {
        StringBuilder sb = new StringBuilder();
        if (durationMinutes > 0) {
            sb.append(durationMinutes).append("m");
        }
        if (airDate != null) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(airDate);
        }
        return sb.toString();
    }
}
