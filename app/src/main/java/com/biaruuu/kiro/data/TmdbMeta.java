package com.biaruuu.kiro.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Title logo, score and age rating of an anime, from TMDB. Any part can be missing. */
public final class TmdbMeta {

    public static final TmdbMeta EMPTY = new TmdbMeta(null, 0.0, null, null);

    @Nullable private final String logoUrl;
    private final double rating;
    @Nullable private final String certification;
    @Nullable private final String posterUrl;

    public TmdbMeta(@Nullable String logoUrl, double rating, @Nullable String certification, @Nullable String posterUrl) {
        this.posterUrl = posterUrl;
        this.logoUrl = logoUrl;
        this.rating = rating;
        this.certification = certification;
    }

    /** Transparent PNG of the title artwork, or null. */
    @Nullable
    public String getLogoUrl() {
        return logoUrl;
    }

    /** Full-resolution poster, or null. */
    @Nullable
    public String getPosterUrl() {
        return posterUrl;
    }

    /** Community score out of 10 (0 when unknown). */
    public double getRating() {
        return rating;
    }

    /** Age rating such as "U/A 13+", or null. */
    @Nullable
    public String getCertification() {
        return certification;
    }

    @NonNull
    public String getRatingLabel() {
        return rating > 0 ? String.format(java.util.Locale.US, "%.1f", rating) : "";
    }
}
