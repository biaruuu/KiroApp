package com.biaruuu.kiro.data;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * Shared OkHttp client used for both the AniList GraphQL calls and Coil image loading.
 */
public final class HttpClientProvider {

    private static volatile OkHttpClient client;

    private HttpClientProvider() {
    }

    public static OkHttpClient get() {
        OkHttpClient local = client;
        if (local == null) {
            synchronized (HttpClientProvider.class) {
                local = client;
                if (local == null) {
                    local = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(20, TimeUnit.SECONDS)
                            .callTimeout(30, TimeUnit.SECONDS)
                            .retryOnConnectionFailure(true)
                            .build();
                    client = local;
                }
            }
        }
        return local;
    }
}
