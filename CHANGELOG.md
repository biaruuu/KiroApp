# Changelog

All notable changes to Kiro are documented here. Every GitHub release publishes the
matching section below as its release notes, so add an entry for each new version
**before** tagging it.

## [1.6.0] - 2026-11-14

Anime details page.

### Added
- **Details page** (tap any poster or the hero Play button):
  - Auto-playing, looping, muted **trailer** as the hero with a speaker toggle. The trailer comes from AniList (YouTube); when AniList has none, it is looked up on **MyAnimeList** (via Jikan). If a trailer can't play, the banner image is shown instead.
  - Rating, title, genres, a purple Play button, and **My List / Rating / Share** actions (My List and 1-5 star ratings are saved on the device; Share sends the AniList link).
  - Synopsis with a **More** link that opens the full **Detail** page: poster, format | start date | status, synopsis, cast, credits (director, original creator, composition, character design, music, studio) and the AniList source.
  - Season dropdown with prequels and sequels, an Oldest/Newest sort, an episode count and episode rows with thumbnails.
  - A "Streaming is coming soon" banner; streaming itself is not available yet.
- System back button closes details pages one step at a time.

## [1.5.0] - 2026-11-14

Search redesigned to match the reference.

### Changed
- **Search screen revamped**:
  - Flat grey search bar ("Search by title, tag") under a black status strip, with a divider below it.
  - Genre filters are now text tabs (All, Action, Romance, Comedy, Fantasy, Adventure...) with a white underline on the selected one. "All" clears the filter.
  - A "N Results" count (the real number of matches from AniList) with a sort selector on the right.
  - Sort bottom sheet with **Popularity** and **Recently updated** (newest released titles first), a check mark on the current choice and a Cancel row.
  - **Three posters per row** on phones; wider screens get more columns automatically.
- Posters everywhere (Home rows, Trending Now, Search) use the reference proportions, slightly wider than 2:3, with 21sp title lines.
- The AIRING / UPCOMING tag is a smaller pill. In Search it sits at the bottom-right of the poster; on Home it stays in the top-right corner.

### Added
- Fluent chevron and check icons.

## [1.4.0] - 2026-11-14

### Added
- **Trending Now** row under the hero: the 10 most-trending anime, each with a big outlined rank number (1-10) beside its poster, like the reference. It uses live AniList trending data.

### Fixed
- **Notification bell** is now a plain white icon with no dark circle behind it, matching the reference. It keeps a very faint shadow so it stays visible on bright artwork.

## [1.3.0] - 2026-11-14

### Changed
- **The whole app now uses Netflix Sans** (Light, Regular, Medium and Bold) in place of Roboto. The bold wordmark and splash screen use the Bold weight.

### Removed
- Roboto font files and license (no longer bundled).

### Notes
- Netflix Sans is a proprietary font; see `licenses/FONTS.md` before redistributing the repository or the APK.

## [1.2.0] - 2026-11-14

New look that follows the Laftel-style reference, with Fluent icons and smoother scrolling.

### Changed
- **Fluent icons**: every icon is now from Microsoft's Fluent UI System Icons (MIT). The outline version is used normally and the filled version when selected.
- **Bottom navigation redesigned** to match the reference: flat dark bar with a thin top divider, 28dp outline icons that fill and turn purple when selected, no pill indicator, and a purple dot on Peek.
- **No gradients anywhere.** The hero uses a flat, even dim over the artwork plus a text shadow, and on tablets and landscape a solid text panel beside the artwork.
- Poster cards are cleaner: smaller corners, tighter spacing like the reference, and a purple tag in the corner for airing and upcoming anime (instead of a score badge).
- Hero is taller on phones to match the reference, with a smaller bold title, Play button resized to the design, and a round dimmed button behind the notification bell.
- Search uses the same Fluent icons and slightly tighter corners.
- Side navigation rail on tablets and landscape uses the same flat style as the bottom bar.

### Improved
- **Smoother scrolling**: a larger in-memory image cache plus an on-disk cache so posters never reload, the next hero banner is preloaded, list items are typed so Compose can reuse them, and the hero no longer auto-advances while you are swiping it.

### Fixed
- Launcher icon is now generated from an SVG source (`design/icons/ic_launcher_foreground.svg`).

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
