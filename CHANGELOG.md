# Changelog

All notable changes to Kiro are documented here. Every GitHub release publishes the
matching section below as its release notes, so add an entry for each new version
**before** tagging it.

## [1.1.0] - 2026-11-13

Responsive design, SVG icons and a Java network layer.

### Added
- **SVG icons**: all icons (Home, Search, Peek, Library, Me, notifications, play, star, close) are now SVG files in `design/icons`, converted to Android vector drawables by `scripts/svg2vector.py`.
- **Responsive layouts** for phones, tablets and landscape:
  - Card sizes, spacing, gutters and text sizes scale with the screen width.
  - On tablets and in landscape the hero becomes a wide banner with the title, rating, year, genres and Play button on the left.
  - The bottom bar becomes a side navigation rail on wide screens.
  - Search is centred with a maximum width on large screens, and its grid fits as many columns as the width allows.
- Hero shows rating, format, year and genres on wide screens.

### Changed
- **Roboto is now bundled** from the font package you supplied (Regular, Medium, Bold, Black) instead of being downloaded at build time. Its OFL license is in `licenses/`.
- **The API and network layer is now written in Java** (`AniListApi`, models, error messages). Kotlin keeps the UI and a thin coroutine adapter; Rust keeps the native helpers.

### Removed
- The font download script and the unused Kotlin serialization and Material icons dependencies.

## [1.0.0] - 2026-11-13

First public release of Kiro.

### Added
- **Home screen**
  - Full-screen hero carousel of this week's trending anime with title, tagline and Play button, auto-advancing every 5 seconds with page dots.
  - Poster rows: Airing This Season, Popular Right Now, Can't Hide My Feelings for You!, Fierce Competition in a Match, It's Beyond Imagination, Magical Worlds You Can't Resist, Top Rated of All Time and Coming Soon.
  - Score badge on every poster card.
  - Loading and error states with a Retry button.
- **Search screen**
  - Live search as you type (debounced) against the AniList catalogue.
  - Genre filter chips that combine with the search text.
  - Trending anime shown when the search box is empty.
  - Infinite scroll for more results.
- Bottom navigation (Home, Search, Peek, Library, Me). Peek, Library and Me are placeholders for now.
- Dark theme with the Kiro purple accent and Roboto typography.
- Splash screen with the Kiro wordmark.
- Data from the AniList GraphQL API (adult titles are filtered out).
- Rust native core (JNI) for description cleanup, score formatting and season detection, with automatic Kotlin fallbacks.
- GitHub Actions workflows that build the APK and publish releases with these changelog notes.

### Known limitations
- Tapping an anime shows a "details coming soon" message; the details page and video playback are planned for a later release.
