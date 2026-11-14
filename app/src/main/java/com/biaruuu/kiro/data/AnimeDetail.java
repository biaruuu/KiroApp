package com.biaruuu.kiro.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Everything the details page shows. Immutable. */
public final class AnimeDetail {

    /** Collects the fields; keeps the constructor call readable. */
    public static final class Builder {
        public Anime anime;
        public int malId;
        public String trailerYoutubeId;
        public List<String> trailerIds = new ArrayList<>();
        public int ratingCount;
        public int durationMinutes;
        public String startDate = "";
        public int nextEpisode;
        public List<String> studios = new ArrayList<>();
        public List<EpisodeInfo> streamingEpisodes = new ArrayList<>();
        public Map<Integer, Long> airTimes = Collections.emptyMap();
        public List<RelatedAnime> related = new ArrayList<>();
        public List<String> cast = new ArrayList<>();
        public List<CreditEntry> credits = new ArrayList<>();

        public AnimeDetail build() {
            return new AnimeDetail(this);
        }
    }

    private static final int MAX_EPISODES = 2000;

    private final Anime anime;
    private final int malId;
    private final String trailerYoutubeId;
    private final List<String> trailerIds;
    private final int ratingCount;
    private final int durationMinutes;
    private final String startDate;
    private final int nextEpisode;
    private final List<String> studios;
    private final List<EpisodeInfo> streamingEpisodes;
    private final Map<Integer, Long> airTimes;
    private final List<RelatedAnime> related;
    private final List<String> cast;
    private final List<CreditEntry> credits;
    private final List<EpisodeInfo> episodes;

    private AnimeDetail(Builder b) {
        this.anime = b.anime;
        this.malId = b.malId;
        List<String> ids = new ArrayList<>(b.trailerIds);
        if (b.trailerYoutubeId != null && !ids.contains(b.trailerYoutubeId)) {
            ids.add(0, b.trailerYoutubeId);
        }
        this.trailerIds = Collections.unmodifiableList(ids);
        this.trailerYoutubeId = ids.isEmpty() ? null : ids.get(0);
        this.ratingCount = b.ratingCount;
        this.durationMinutes = b.durationMinutes;
        this.startDate = b.startDate;
        this.nextEpisode = b.nextEpisode;
        this.studios = Collections.unmodifiableList(b.studios);
        this.streamingEpisodes = Collections.unmodifiableList(b.streamingEpisodes);
        this.airTimes = b.airTimes;
        this.related = Collections.unmodifiableList(b.related);
        this.cast = Collections.unmodifiableList(b.cast);
        this.credits = Collections.unmodifiableList(b.credits);
        this.episodes = Collections.unmodifiableList(buildEpisodes());
    }

    /** Same details with these trailers, best first (the player tries them in order). */
    @NonNull
    public AnimeDetail withTrailers(@NonNull List<String> youtubeIds) {
        Builder b = new Builder();
        b.anime = anime;
        b.malId = malId;
        b.trailerIds = new ArrayList<>(youtubeIds);
        b.ratingCount = ratingCount;
        b.durationMinutes = durationMinutes;
        b.startDate = startDate;
        b.nextEpisode = nextEpisode;
        b.studios = new ArrayList<>(studios);
        b.streamingEpisodes = new ArrayList<>(streamingEpisodes);
        b.airTimes = airTimes;
        b.related = new ArrayList<>(related);
        b.cast = new ArrayList<>(cast);
        b.credits = new ArrayList<>(credits);
        return b.build();
    }

    /** Same details with a different trailer (used when MyAnimeList supplies one). */
    @NonNull
    public AnimeDetail withTrailer(@Nullable String youtubeId) {
        Builder b = new Builder();
        b.anime = anime;
        b.malId = malId;
        b.trailerYoutubeId = youtubeId;
        b.trailerIds = new ArrayList<>(trailerIds);
        b.ratingCount = ratingCount;
        b.durationMinutes = durationMinutes;
        b.startDate = startDate;
        b.nextEpisode = nextEpisode;
        b.studios = new ArrayList<>(studios);
        b.streamingEpisodes = new ArrayList<>(streamingEpisodes);
        b.airTimes = airTimes;
        b.related = new ArrayList<>(related);
        b.cast = new ArrayList<>(cast);
        b.credits = new ArrayList<>(credits);
        return b.build();
    }

    /**
     * Episode rows. Uses the real episode count when AniList knows it, otherwise the number of
     * episodes aired so far. Names and thumbnails come from streaming sites when available.
     */
    private List<EpisodeInfo> buildEpisodes() {
        int count = anime.getEpisodes();
        if (count <= 0 && nextEpisode > 1) {
            count = nextEpisode - 1;
        }
        if (count <= 0) {
            count = streamingEpisodes.size();
        }
        count = Math.min(count, MAX_EPISODES);

        SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd", Locale.US);
        List<EpisodeInfo> list = new ArrayList<>(count);
        for (int n = 1; n <= count; n++) {
            EpisodeInfo streaming = null;
            for (EpisodeInfo candidate : streamingEpisodes) {
                if (candidate.getNumber() == n) {
                    streaming = candidate;
                    break;
                }
            }
            Long airAt = airTimes.get(n);
            String date = airAt != null ? format.format(new Date(airAt * 1000L)) : null;
            list.add(new EpisodeInfo(
                    n,
                    streaming != null ? streaming.getTitle() : null,
                    streaming != null ? streaming.getThumbnailUrl() : null,
                    durationMinutes,
                    date));
        }
        return list;
    }

    @NonNull
    public Anime getAnime() {
        return anime;
    }

    /** MyAnimeList id, or 0. */
    public int getMalId() {
        return malId;
    }

    /** YouTube video id of the trailer, or null when none is known. */
    @Nullable
    public String getTrailerYoutubeId() {
        return trailerYoutubeId;
    }

    /** Every known trailer (YouTube ids), best first. */
    @NonNull
    public List<String> getTrailerIds() {
        return trailerIds;
    }

    /** How many people scored it on AniList. */
    public int getRatingCount() {
        return ratingCount;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    /** "2012.07.08" (or just the year / year and month when that is all that is known). */
    @NonNull
    public String getStartDate() {
        return startDate;
    }

    @NonNull
    public List<String> getStudios() {
        return studios;
    }

    @NonNull
    public List<RelatedAnime> getRelated() {
        return related;
    }

    /** "Yoshitsugu Matsuoka as Kirito" style lines. */
    @NonNull
    public List<String> getCast() {
        return cast;
    }

    @NonNull
    public List<CreditEntry> getCredits() {
        return credits;
    }

    @NonNull
    public List<EpisodeInfo> getEpisodes() {
        return episodes;
    }
}
