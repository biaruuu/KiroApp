package com.biaruuu.kiro;

import android.app.Application;

import androidx.annotation.NonNull;

import com.biaruuu.kiro.data.HttpClientProvider;

import coil.ImageLoader;
import coil.ImageLoaderFactory;

/**
 * Application entry point. Provides the app-wide Coil image loader that shares
 * our OkHttp client.
 */
public class KiroApplication extends Application implements ImageLoaderFactory {

    @NonNull
    @Override
    public ImageLoader newImageLoader() {
        return new ImageLoader.Builder(this)
                .okHttpClient(HttpClientProvider.get())
                .crossfade(true)
                .build();
    }
}
