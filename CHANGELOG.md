# Changelog

All notable changes to Kiro are documented here. Every GitHub release publishes the
matching section below as its release notes, so add an entry for each new version
**before** tagging it.

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
