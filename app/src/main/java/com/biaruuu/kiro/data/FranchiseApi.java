package com.biaruuu.kiro.data;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Finds every season of a franchise ("Season 3 Part 1", "Season 3 Part 2", movies...) by walking
 * the PREQUEL / SEQUEL links on AniList in both directions, one batched request per level.
 */
public final class FranchiseApi {

    private static final String ENDPOINT = "https://graphql.anilist.co";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final ExecutorService POOL = Executors.newCachedThreadPool();
    private static final int MAX_ENTRIES = 40;
    private static final int MAX_ROUNDS = 12;

    private FranchiseApi() {
    }

    @NonNull
    public static AniListApi.Cancelable load(
            int id, @NonNull AniListApi.Callback<List<SeasonEntry>> callback) {
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        POOL.execute(() -> {
            try {
                List<SeasonEntry> result = walk(id, cancelled);
                if (!cancelled.get()) {
                    callback.onSuccess(result);
                }
            } catch (Exception e) {
                if (!cancelled.get()) {
                    callback.onError(e);
                }
            }
        });
        return () -> cancelled.set(true);
    }

    private static List<SeasonEntry> walk(int startId, AtomicBoolean cancelled) throws Exception {
        Map<Integer, SeasonEntry> shown = new HashMap<>();
        Set<Integer> seen = new LinkedHashSet<>();
        List<Integer> frontier = new ArrayList<>();
        frontier.add(startId);
        seen.add(startId);

        for (int round = 0; round < MAX_ROUNDS && !frontier.isEmpty() && !cancelled.get(); round++) {
            StringBuilder q = new StringBuilder("query {");
            for (int i = 0; i < frontier.size(); i++) {
                q.append(" a").append(i).append(": Media(id: ").append(frontier.get(i))
                        .append(", type: ANIME) { id format title { romaji english }")
                        .append(" startDate { year month day }")
                        .append(" relations { edges { relationType(version: 2) node { id type } } } }");
            }
            q.append(" }");
            JSONObject data = post(q.toString()).optJSONObject("data");
            List<Integer> next = new ArrayList<>();
            for (int i = 0; data != null && i < frontier.size(); i++) {
                JSONObject media = data.optJSONObject("a" + i);
                if (media == null) {
                    continue;
                }
                int id = media.optInt("id");
                String format = media.optString("format", "");
                boolean wanted = id == startId || format.equals("TV") || format.equals("TV_SHORT")
                        || format.equals("ONA") || format.equals("MOVIE");
                if (wanted) {
                    shown.put(id, new SeasonEntry(id, titleOf(media), sortKeyOf(media)));
                }
                JSONObject relations = media.optJSONObject("relations");
                JSONArray edges = relations != null ? relations.optJSONArray("edges") : null;
                for (int e = 0; edges != null && e < edges.length(); e++) {
                    JSONObject edge = edges.optJSONObject(e);
                    JSONObject node = edge != null ? edge.optJSONObject("node") : null;
                    if (node == null || !"ANIME".equals(node.optString("type"))) {
                        continue;
                    }
                    String type = edge.optString("relationType");
                    int nodeId = node.optInt("id");
                    if (("PREQUEL".equals(type) || "SEQUEL".equals(type))
                            && seen.size() < MAX_ENTRIES && seen.add(nodeId)) {
                        next.add(nodeId);
                    }
                }
            }
            frontier = next;
        }

        List<SeasonEntry> list = new ArrayList<>(shown.values());
        Collections.sort(list, (a, b) -> {
            int ka = a.getSortKey() == 0 ? Integer.MAX_VALUE : a.getSortKey();
            int kb = b.getSortKey() == 0 ? Integer.MAX_VALUE : b.getSortKey();
            return Integer.compare(ka, kb);
        });
        return list;
    }

    private static String titleOf(JSONObject media) {
        JSONObject title = media.optJSONObject("title");
        String name = title != null && !title.isNull("english") ? title.optString("english") : "";
        if (name.isEmpty() && title != null && !title.isNull("romaji")) {
            name = title.optString("romaji");
        }
        return name.isEmpty() ? "Untitled" : name;
    }

    private static int sortKeyOf(JSONObject media) {
        JSONObject d = media.optJSONObject("startDate");
        if (d == null || d.isNull("year")) {
            return 0;
        }
        return d.optInt("year") * 10000 + d.optInt("month") * 100 + d.optInt("day");
    }

    private static JSONObject post(String query) throws Exception {
        JSONObject body = new JSONObject();
        body.put("query", query);
        Request request = new Request.Builder()
                .url(ENDPOINT)
                .header("Accept", "application/json")
                .post(RequestBody.create(body.toString(), JSON_TYPE))
                .build();
        try (Response r = HttpClientProvider.get().newCall(request).execute()) {
            ResponseBody b = r.body();
            String text = b != null ? b.string() : "{}";
            // AniList answers 404 with partial data when one id is missing; the data is still usable.
            if (!r.isSuccessful() && !text.contains("\"data\"")) {
                throw new IOException("AniList error (HTTP " + r.code() + ")");
            }
            return new JSONObject(text);
        }
    }
}
