# Kiro

An anime streaming app for Android built with **Kotlin** (Jetpack Compose), **Java** and **Rust**,
powered by the [AniList GraphQL API](https://anilist.gitbook.io/anilist-apiv2-docs/).
Typography is Netflix Sans (proprietary, see `licenses/FONTS.md`).

**Version 1.6.7** - Home, Search and anime details screens, responsive layouts, Fluent icons.

## Download

Grab the latest `Kiro-x.y.z.apk` from the [Releases](../../releases) page and install it
(you may need to allow installs from your browser or file manager).

## What's inside

| Part | Language | What it does |
| --- | --- | --- |
| `app/.../ui`, `core`, `data/AniListRepository.kt` | Kotlin | Jetpack Compose UI, view models, coroutine adapter |
| `app/.../data/*.java`, `KiroApplication.java` | Java | AniList GraphQL client, networking, models, error messages |
| `rust-core/` | Rust (JNI) | HTML cleanup of descriptions, score formatting, season detection |
| `design/icons/` | SVG | [Fluent UI System Icons](https://github.com/microsoft/fluentui-system-icons) (MIT), converted by `scripts/svg2vector.py` |

If the Rust library is missing, the app falls back to Kotlin implementations automatically.

## Building

Requirements: JDK 17, Android SDK (API 35), Rust with the Android targets, `cargo-ndk`, Android NDK.

```bash
python3 scripts/svg2vector.py # only after editing design/icons/*.svg
bash scripts/build-rust.sh    # builds the native library into app/src/main/jniLibs
./gradlew assembleRelease     # APK in app/build/outputs/apk/release/
```

## Releasing a new version

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add a `## [x.y.z] - YYYY-MM-DD` section to `CHANGELOG.md` describing what is new.
3. Commit and push to `main`.

The **Release** workflow builds the APK and publishes a GitHub release `vx.y.z` whose notes are that
changelog section. It skips versions that already have a release, and fails on purpose if the
changelog entry is missing. (Pushing a tag such as `v1.0.1` also triggers it.)

## Signing key

`keystore/kiro-release.jks` is a shared key committed so every release APK can update the previous
one. It is fine for a hobby project, but move it to GitHub secrets before publishing to the Play Store.
