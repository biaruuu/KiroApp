plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.biaruuu.kiro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.biaruuu.kiro"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "1.6.3"
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
            applicationIdSuffix = ".debug"
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
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
