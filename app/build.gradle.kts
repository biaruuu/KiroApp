plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase is switched on only when app/google-services.json is present (CI writes it from the
// GOOGLE_SERVICES_JSON secret). Without it the app builds and runs with sign-in turned off.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.biaruuu.kiro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.biaruuu.kiro"
        minSdk = 26
        targetSdk = 35
        versionCode = 135
        versionName = "1.68.0"
        // TMDB key: GitHub secret TMDB_API_KEY in CI, or -PTMDB_API_KEY / env var locally.
        val tmdbKey = (System.getenv("TMDB_API_KEY") ?: project.findProperty("TMDB_API_KEY") as String? ?: "").trim()
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbKey\"")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            // Shared open-source signing key so every release APK can update the previous one.
            // Move this to GitHub secrets if you ever publish to the Play Store.
            storeFile = rootProject.file("keystore/kiro-release.jks")
            storePassword = "kirokiro"
            keyAlias = "kiro"
            keyPassword = "kirokiro"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    // FFmpeg (native C/C++) software audio decoders: AC3, E-AC3, DTS, FLAC, Opus, Vorbis, ALAC...
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1")
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    // Google sign-in through Credential Manager
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
