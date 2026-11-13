package com.biaruuu.kiro;

import android.app.Application;

import androidx.annotation.NonNull;

import com.biaruuu.kiro.data.HttpClientProvider;

import java.io.File;

import coil.ImageLoader;
import coil.ImageLoaderFactory;
import coil.disk.DiskCache;
import coil.memory.MemoryCache;

/**
 * Application entry point. Provides the app-wide Coil image loader, which shares our OkHttp
 * client and keeps posters in memory and on disk so scrolling never waits on the network
 * for something it has already shown.
 */
public class KiroApplication extends Application implements ImageLoaderFactory {

    @NonNull
    @Override
    public ImageLoader newImageLoader() {
        return new ImageLoader.Builder(this)
                .okHttpClient(HttpClientProvider.get())
                .memoryCache(() -> new MemoryCache.Builder(this)
                        .maxSizePercent(0.25)
                        .build())
                .diskCache(() -> new DiskCache.Builder()
                        .directory(new File(getCacheDir(), "images"))
                        .maxSizePercent(0.02)
                        .build())
                // AniList artwork never changes for a given URL, so ignore its cache headers.
                .respectCacheHeaders(false)
                .crossfade(150)
                .build();
    }
}
