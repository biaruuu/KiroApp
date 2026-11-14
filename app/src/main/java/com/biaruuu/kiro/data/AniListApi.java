package com.biaruuu.kiro.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Client for the public AniList GraphQL API (https://graphql.anilist.co).
 *
 * All calls are asynchronous: they return immediately with a {@link Cancelable} and report
 * back through a {@link Callback} on a background thread.
 */
public final class AniListApi {

    /** Receives the outcome of an API call. Exactly one of the two methods is called. */
    public interface Callback<T> {
        void onSuccess(@NonNull T result);

        void onError(@NonNull Exception error);
    }

    /** Handle used to abort an in-flight request. */
    public interface Cancelable {
        void cancel();
    }

    private interface Parser<T> {
        T parse(JSONObject data) throws JSONException, IOException;
    }

    private static final String ENDPOINT = "https://graphql.anilist.co";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int SEARCH_PAGE_SIZE = 24;
    private static final int HERO_COUNT = 8;
    private static final int TRENDING_COUNT = 10;
    private static final int ROW_SIZE = 16;

    private static final String MEDIA_FIELDS =
            "fragment M on Media {"
                    + " id"
                    + " title { romaji english native }"
                    + " coverImage { extraLarge large color }"
                    + " bannerImage"
                    + " description"
                    + " averageScore"
                    + " episodes"
                    + " format"
                    + " seasonYear"
                    + " genres"
                    + " status"
                    + " }";

    private static String rowQuery(String alias, int perPage, String mediaArgs) {
        return alias + ": Page(page: 1, perPage: " + perPage + ") {"
                + " media(type: ANIME, " + mediaArgs + ", isAdult: false) { ...M } }";
    }

    private static final String HOME_QUERY =
            "query Home($season: MediaSeason, $seasonYear: Int) { "
                    + rowQuery("trending", TRENDING_COUNT, "sort: TRENDING_DESC") + " "
                    + rowQuery("season", ROW_SIZE,
                    "season: $season, seasonYear: $seasonYear, sort: POPULARITY_DESC") + " "
                    + rowQuery("popular", ROW_SIZE, "sort: POPULARITY_DESC") + " "
                    + rowQuery("topRated", ROW_SIZE, "sort: SCORE_DESC") + " "
                    + rowQuery("sports", ROW_SIZE, "genre: \"Sports\", sort: POPULARITY_DESC") + " "
                    + rowQuery("romance", ROW_SIZE, "genre: \"Romance\", sort: TRENDING_DESC") + " "
                    + rowQuery("scifi", ROW_SIZE, "genre: \"Sci-Fi\", sort: POPULARITY_DESC") + " "
                    + rowQuery("fantasy", ROW_SIZE, "genre: \"Fantasy\", sort: POPULARITY_DESC") + " "
                    + rowQuery("upcoming", ROW_SIZE,
                    "status: NOT_YET_RELEASED, sort: POPULARITY_DESC")
                    + " } "
                    + MEDIA_FIELDS;

    private static final String SEARCH_QUERY =
            "query Search($search: String, $genre: String, $page: Int, $perPage: Int,"
                    + " $sort: [MediaSort], $statusIn: [MediaStatus]) {"
                    + " Page(page: $page, perPage: $perPage) {"
                    + " pageInfo { hasNextPage total }"
                    + " media(type: ANIME, search: $search, genre: $genre, sort: $sort,"
                    + " status_in: $statusIn, isAdult: false) { ...M } } } "
                    + MEDIA_FIELDS;

    private static final String DETAIL_QUERY =
            "query Detail($id: Int) { Media(id: $id, type: ANIME) {"
                    + " id idMal"
                    + " title { romaji english native }"
                    + " coverImage { extraLarge large color }"
                    + " bannerImage description averageScore episodes duration format status"
                    + " seasonYear genres"
                    + " startDate { year month day }"
                    + " trailer { id site }"
                    + " studios(isMain: true) { nodes { name } }"
                    + " stats { scoreDistribution { amount } }"
                    + " nextAiringEpisode { episode }"
                    + " streamingEpisodes { title thumbnail }"
                    + " airingSchedule(perPage: 100) { nodes { episode airingAt } }"
                    + " relations { edges { relationType node { id type title { romaji english } } } }"
                    + " characters(sort: [ROLE, RELEVANCE], perPage: 6) {"
                    + " edges { node { name { full } }"
                    + " voiceActors(language: JAPANESE, sort: RELEVANCE) { name { full } } } }"
                    + " staff(sort: RELEVANCE, perPage: 25) { edges { role node { name { full } } } }"
                    + " } }";

    private static final Pattern EPISODE_TITLE =
            Pattern.compile("^(?:Episode|Ep\\.?|E)\\s*(\\d+)\\s*[-\u2013:.]*\\s*(.*)$",
                    Pattern.CASE_INSENSITIVE);

    private AniListApi() {
    }

    /**
     * Loads the Home screen content: trending anime for the hero plus the poster rows.
     *
     * @param season     AniList season name, e.g. "FALL"
     * @param seasonYear calendar year of that season
     */
    @NonNull
    public static Cancelable loadHome(
            @NonNull String season, int seasonYear, @NonNull Callback<HomeContent> callback) {
        JSONObject variables = new JSONObject();
        try {
            variables.put("season", season);
            variables.put("seasonYear", seasonYear);
        } catch (JSONException e) {
            callback.onError(e);
            return () -> { };
        }

        return post(HOME_QUERY, variables, data -> {
            List<Anime> trending = mediaList(data, "trending");
            List<Anime> hero = new ArrayList<>();
            for (Anime anime : trending) {
                if (anime.getHeroUrl() != null && hero.size() < HERO_COUNT) {
                    hero.add(anime);
                }
            }

            List<HomeSection> sections = new ArrayList<>();
            // Ranked "Top 10" row, right under the hero.
            if (!trending.isEmpty()) {
                sections.add(new HomeSection("Trending Now", trending, true));
            }
            addSection(sections, "Airing This Season", mediaList(data, "season"));
            addSection(sections, "Popular Right Now", mediaList(data, "popular"));
            addSection(sections, "Can't Hide My Feelings for You!", mediaList(data, "romance"));
            addSection(sections, "Fierce Competition in a Match", mediaList(data, "sports"));
            addSection(sections, "It's Beyond Imagination", mediaList(data, "scifi"));
            addSection(sections, "Magical Worlds You Can't Resist", mediaList(data, "fantasy"));
            addSection(sections, "Top Rated of All Time", mediaList(data, "topRated"));
            addSection(sections, "Coming Soon", mediaList(data, "upcoming"));

            if (hero.isEmpty() && sections.isEmpty()) {
                throw new IOException("AniList returned no anime. Please try again.");
            }
            return new HomeContent(hero, sections);
        }, callback);
    }

    /**
     * Searches anime. An empty query lists everything, so the screen is never blank.
     *
     * @param query text to search for (may be empty)
     * @param genre optional AniList genre name, e.g. "Sci-Fi"
     * @param sort  result order
     * @param page  1-based page number
     */
    @NonNull
    public static Cancelable search(
            @NonNull String query,
            @Nullable String genre,
            @NonNull SortOrder sort,
            int page,
            @NonNull Callback<SearchResult> callback) {
        String text = query.trim();
        JSONObject variables = new JSONObject();
        try {
            if (!text.isEmpty()) {
                variables.put("search", text);
            }
            if (genre != null && !genre.isEmpty()) {
                variables.put("genre", genre);
            }
            variables.put("page", page);
            variables.put("perPage", SEARCH_PAGE_SIZE);
            if (sort == SortOrder.RECENT) {
                variables.put("sort", new JSONArray().put("START_DATE_DESC"));
                // Skip announced-but-unreleased titles so the newest entries are real releases.
                variables.put("statusIn", new JSONArray().put("RELEASING").put("FINISHED"));
            } else {
                variables.put("sort", new JSONArray()
                        .put(text.isEmpty() ? "POPULARITY_DESC" : "SEARCH_MATCH"));
            }
        } catch (JSONException e) {
            callback.onError(e);
            return () -> { };
        }

        return post(SEARCH_QUERY, variables, data -> {
            JSONObject pageObject = data.optJSONObject("Page");
            if (pageObject == null) {
                return new SearchResult(new ArrayList<>(), false, 0);
            }
            List<Anime> items = parseMedia(pageObject.optJSONArray("media"));
            JSONObject pageInfo = pageObject.optJSONObject("pageInfo");
            boolean hasNext = pageInfo != null && pageInfo.optBoolean("hasNextPage", false);
            int total = pageInfo != null ? pageInfo.optInt("total", 0) : 0;
            return new SearchResult(items, hasNext, total);
        }, callback);
    }


    /**
     * Loads everything for the details page. If AniList has no YouTube trailer, MyAnimeList is
     * asked for one before the result is returned.
     */
    @NonNull
    public static Cancelable loadDetail(int id, @NonNull Callback<AnimeDetail> callback) {
        JSONObject variables = new JSONObject();
        try {
            variables.put("id", id);
        } catch (JSONException e) {
            callback.onError(e);
            return () -> { };
        }

        return post(DETAIL_QUERY, variables, AniListApi::parseDetail, callback);
    }

    // ---------------------------------------------------------------------------------------
    // HTTP
    // ---------------------------------------------------------------------------------------

    @NonNull
    private static <T> Cancelable post(
            @NonNull String query,
            @NonNull JSONObject variables,
            @NonNull Parser<T> parser,
            @NonNull Callback<T> callback) {
        final String payload;
        try {
            JSONObject body = new JSONObject();
            body.put("query", query);
            body.put("variables", variables);
            payload = body.toString();
        } catch (JSONException e) {
            callback.onError(e);
            return () -> { };
        }

        Request request = new Request.Builder()
                .url(ENDPOINT)
                .header("Accept", "application/json")
                .post(RequestBody.create(payload, JSON_TYPE))
                .build();

        final Call call = HttpClientProvider.get().newCall(request);
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call c, @NonNull IOException e) {
                if (!c.isCanceled()) {
                    callback.onError(e);
                }
            }

            @Override
            public void onResponse(@NonNull Call c, @NonNull Response response) {
                final T result;
                try (Response r = response) {
                    result = handle(r, parser);
                } catch (Exception e) {
                    if (!c.isCanceled()) {
                        callback.onError(e);
                    }
                    return;
                }
                callback.onSuccess(result);
            }
        });
        return call::cancel;
    }

    private static <T> T handle(Response response, Parser<T> parser) throws IOException {
        ResponseBody body = response.body();
        String text = body != null ? body.string() : "";

        if (response.code() == 429) {
            throw new IOException("AniList is rate limiting requests. Wait a moment and retry.");
        }

        JSONObject root = null;
        try {
            root = new JSONObject(text);
        } catch (JSONException ignored) {
            // handled below
        }

        if (!response.isSuccessful()) {
            String message = root != null ? firstError(root) : null;
            throw new IOException(message != null
                    ? message
                    : "AniList error (HTTP " + response.code() + ")");
        }
        if (root == null) {
            throw new IOException("Unexpected response from AniList");
        }

        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            String message = firstError(root);
            throw new IOException(message != null ? message : "Empty response from AniList");
        }

        try {
            return parser.parse(data);
        } catch (JSONException e) {
            throw new IOException("Unexpected data from AniList", e);
        }
    }

    @Nullable
    private static String firstError(JSONObject root) {
        JSONArray errors = root.optJSONArray("errors");
        if (errors == null || errors.length() == 0) {
            return null;
        }
        return str(errors.optJSONObject(0), "message");
    }

    // ---------------------------------------------------------------------------------------
    // JSON parsing
    // ---------------------------------------------------------------------------------------

    private static void addSection(List<HomeSection> out, String title, List<Anime> items) {
        if (!items.isEmpty()) {
            out.add(new HomeSection(title, items));
        }
    }

    private static List<Anime> mediaList(JSONObject data, String key) {
        JSONObject page = data.optJSONObject(key);
        if (page == null) {
            return new ArrayList<>();
        }
        return parseMedia(page.optJSONArray("media"));
    }

    private static List<Anime> parseMedia(@Nullable JSONArray array) {
        List<Anime> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null && !item.isNull("id")) {
                list.add(parseAnime(item));
            }
        }
        return list;
    }


    private static AnimeDetail parseDetail(JSONObject data) throws JSONException, IOException {
        JSONObject m = data.optJSONObject("Media");
        if (m == null) {
            throw new IOException("This anime could not be found.");
        }
        AnimeDetail.Builder b = new AnimeDetail.Builder();
        b.anime = parseAnime(m);
        b.malId = intOrZero(m, "idMal");
        b.durationMinutes = intOrZero(m, "duration");
        b.startDate = dateLabel(m.optJSONObject("startDate"));

        JSONObject trailer = m.optJSONObject("trailer");
        if (trailer != null && "youtube".equalsIgnoreCase(str(trailer, "site"))) {
            b.trailerYoutubeId = str(trailer, "id");
        }

        JSONObject studios = m.optJSONObject("studios");
        if (studios != null) {
            JSONArray nodes = studios.optJSONArray("nodes");
            for (int i = 0; nodes != null && i < nodes.length(); i++) {
                String name = str(nodes.optJSONObject(i), "name");
                if (name != null) {
                    b.studios.add(name);
                }
            }
        }

        JSONObject stats = m.optJSONObject("stats");
        JSONArray distribution = stats != null ? stats.optJSONArray("scoreDistribution") : null;
        for (int i = 0; distribution != null && i < distribution.length(); i++) {
            JSONObject row = distribution.optJSONObject(i);
            if (row != null) {
                b.ratingCount += row.optInt("amount", 0);
            }
        }

        JSONObject next = m.optJSONObject("nextAiringEpisode");
        b.nextEpisode = next != null ? intOrZero(next, "episode") : 0;

        // Streaming sites often list several copies of each episode; keep the first of each.
        JSONArray streaming = m.optJSONArray("streamingEpisodes");
        List<Integer> seen = new ArrayList<>();
        for (int i = 0; streaming != null && i < streaming.length(); i++) {
            JSONObject row = streaming.optJSONObject(i);
            String title = str(row, "title");
            if (title == null) {
                continue;
            }
            int number = i + 1;
            String name = title;
            Matcher matcher = EPISODE_TITLE.matcher(title.trim());
            if (matcher.matches()) {
                number = Integer.parseInt(matcher.group(1));
                name = matcher.group(2).trim();
            }
            if (seen.contains(number)) {
                continue;
            }
            seen.add(number);
            b.streamingEpisodes.add(new EpisodeInfo(
                    number, name.isEmpty() ? null : name, str(row, "thumbnail"), 0, null));
        }

        JSONObject schedule = m.optJSONObject("airingSchedule");
        JSONArray scheduleNodes = schedule != null ? schedule.optJSONArray("nodes") : null;
        Map<Integer, Long> airTimes = new HashMap<>();
        for (int i = 0; scheduleNodes != null && i < scheduleNodes.length(); i++) {
            JSONObject node = scheduleNodes.optJSONObject(i);
            if (node != null && node.optInt("episode", 0) > 0) {
                airTimes.put(node.optInt("episode"), node.optLong("airingAt", 0L));
            }
        }
        b.airTimes = airTimes;

        JSONObject relations = m.optJSONObject("relations");
        JSONArray edges = relations != null ? relations.optJSONArray("edges") : null;
        for (int i = 0; edges != null && i < edges.length(); i++) {
            JSONObject edge = edges.optJSONObject(i);
            JSONObject node = edge != null ? edge.optJSONObject("node") : null;
            String type = str(edge, "relationType");
            if (node == null || type == null || !"ANIME".equals(str(node, "type"))) {
                continue;
            }
            if (!"PREQUEL".equals(type) && !"SEQUEL".equals(type)) {
                continue;
            }
            JSONObject title = node.optJSONObject("title");
            String name = str(title, "english");
            if (name == null) {
                name = str(title, "romaji");
            }
            if (name != null && !node.isNull("id")) {
                b.related.add(new RelatedAnime(node.optInt("id"), name, type));
            }
        }

        JSONObject characters = m.optJSONObject("characters");
        JSONArray characterEdges = characters != null ? characters.optJSONArray("edges") : null;
        for (int i = 0; characterEdges != null && i < characterEdges.length(); i++) {
            JSONObject edge = characterEdges.optJSONObject(i);
            if (edge == null) {
                continue;
            }
            JSONObject node = edge.optJSONObject("node");
            String character = node != null ? str(node.optJSONObject("name"), "full") : null;
            JSONArray actors = edge.optJSONArray("voiceActors");
            String actor = (actors != null && actors.length() > 0)
                    ? str(actors.optJSONObject(0).optJSONObject("name"), "full")
                    : null;
            if (actor != null && character != null) {
                b.cast.add(actor + " as " + character);
            }
        }

        JSONObject staff = m.optJSONObject("staff");
        JSONArray staffEdges = staff != null ? staff.optJSONArray("edges") : null;
        for (int i = 0; staffEdges != null && i < staffEdges.length(); i++) {
            JSONObject edge = staffEdges.optJSONObject(i);
            if (edge == null) {
                continue;
            }
            JSONObject node = edge.optJSONObject("node");
            String role = str(edge, "role");
            String name = node != null ? str(node.optJSONObject("name"), "full") : null;
            if (role != null && name != null) {
                b.credits.add(new CreditEntry(role, name));
            }
        }
        return b.build();
    }

    /** "2012.07.08", or just "2012" / "2012.07" when AniList only knows part of the date. */
    private static String dateLabel(@Nullable JSONObject d) {
        if (d == null) {
            return "";
        }
        int year = intOrZero(d, "year");
        int month = intOrZero(d, "month");
        int day = intOrZero(d, "day");
        if (year == 0) {
            return "";
        }
        if (month == 0) {
            return String.valueOf(year);
        }
        if (day == 0) {
            return String.format(java.util.Locale.US, "%d.%02d", year, month);
        }
        return String.format(java.util.Locale.US, "%d.%02d.%02d", year, month, day);
    }

    private static Anime parseAnime(JSONObject o) {
        JSONObject title = o.optJSONObject("title");
        JSONObject cover = o.optJSONObject("coverImage");

        List<String> genres = new ArrayList<>();
        JSONArray genreArray = o.optJSONArray("genres");
        if (genreArray != null) {
            for (int i = 0; i < genreArray.length(); i++) {
                String genre = genreArray.optString(i, "");
                if (!genre.isEmpty()) {
                    genres.add(genre);
                }
            }
        }

        Anime anime = new Anime(
                o.optInt("id"),
                str(title, "romaji"),
                str(title, "english"),
                str(title, "native"),
                str(cover, "large"),
                str(cover, "extraLarge"),
                str(cover, "color"),
                str(o, "bannerImage"),
                str(o, "description"),
                intOrZero(o, "averageScore"),
                intOrZero(o, "episodes"),
                str(o, "format"),
                intOrZero(o, "seasonYear"),
                genres,
                str(o, "status"));
        AnimeCache.put(anime);
        return anime;
    }

    /** Null-safe string read that treats JSON null as null (org.json would give "null"). */
    @Nullable
    private static String str(@Nullable JSONObject o, String key) {
        if (o == null || o.isNull(key)) {
            return null;
        }
        String value = o.optString(key, "");
        return value.isEmpty() ? null : value;
    }

    private static int intOrZero(JSONObject o, String key) {
        return o.isNull(key) ? 0 : o.optInt(key, 0);
    }
}
