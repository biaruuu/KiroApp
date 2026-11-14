# Changelog

All notable changes to Kiro are documented here. Every GitHub release publishes the
matching section below as its release notes, so add an entry for each new version
**before** tagging it.

## [1.8.0] - 2026-11-15

Multi-source playback and subtitle styling.

### Added
- **Multiple sources with automatic best pick.** Every episode asks all providers at once, measures how fast each video address answers, drops the ones that don't, and ranks the rest by resolution and speed. The ranking runs in the native Rust library (with unit tests). The player starts the best one and, if it fails, **fails over to the next** automatically. The menu's Source page shows the list (provider, quality, speed) and lets you pick one.
- **Subtitle style.** Menu > Subtitle style: choose the font (Netflix Sans, Sans, Serif, Monospace, Casual), size (50-200%), color, background (none / dim / black) and edge (none / outline / shadow), with a live preview. Saved on the device and applied to captions straight away; Reset puts the defaults back.
- **Load subtitle file.** Menu > Load subtitle file picks a local .srt, .vtt or .ass file and plays it with your style.
- **My sources.** Menu > Source > Add my own source: type video addresses you have the right to watch (your own server or a licensed service), one per line, using {id} and {episode}; they are ranked and failed over like any other source.
- Settings menu on the top-right three-dot button (Source, Subtitle style, Load subtitle file, Playback speed).

### Notes
- The built-in provider is still a set of public sample videos (so ranking, failover and subtitles can be tried). Real streaming providers are not included.

## [1.7.0] - 2026-11-14

Video player.

### Added
- **Video player** built on Media3 ExoPlayer, styled after the reference: full-screen landscape, close button and episode title at the top, rewind 10 / pause / forward 10 in the centre, a purple seek bar with elapsed and total time, and Audio & Subtitles, Episodes, Next Episode and Lock underneath.
  - Tap to show or hide the controls (they hide by themselves while playing); double-tap the left or right half to skip 10 seconds.
  - Audio & Subtitles lists the tracks the video offers (with subtitles off); Episodes jumps to any aired episode; Next Episode (also automatic at the end); Lock hides every control until unlocked; the top-right menu sets playback speed.
  - Play on the details page and every episode row open the player.
- There is no Go Premium button.

### Changed
- The "Streaming is coming soon" banner is gone.
- Streaming is not connected to a video provider yet, so every episode plays a public sample video for now. The real source plugs in at one place (`PlaybackSource`).
- The app now handles rotation without restarting.

## [1.6.7] - 2026-11-14

### Changed
- **Trailer sound starts on.** The trailer begins muted (so it can autoplay), then the sound switches on by itself as soon as it plays; the speaker button still mutes it.
- **Trailer quality.** The player asks YouTube for 1080p (falling back to what the connection allows), and starts faster by connecting to YouTube early and using the hardware-accelerated view.
- **Countdown is real time with seconds**, e.g. "in 3d 4h 12m 5s", ticking every second, now in white.
- **More work moved to Rust.** The countdown text and the compact rating counts ("619.2K") are produced by the native Rust library (with unit tests), in addition to synopsis cleanup and title matching.

## [1.6.6] - 2026-11-14

Details page styling matched to the reference, plus a next-episode countdown.

### Added
- **Next episode countdown.** Airing anime list only the episodes that have aired, followed by a card such as "Episode 7 - in 3d 4h 12m" that counts down live. Unaired episodes are no longer listed.

### Changed
- **Hero title area** now darkens behind the rating, title and genres and fades into the page, like the reference (the top of the video stays clear).
- Text, icon and button sizes on the details page now follow the reference: smaller rating chip and mute button, 24sp title, 12sp genres, compact My List / Rating / Share row, 11sp synopsis, slimmer season dropdown.

## [1.6.5] - 2026-11-14

Episode details from TMDB, like the website.

### Added
- **Episode details from TMDB**: each episode shows its title, short plot, runtime, air date and screenshot, matched to the right season (including split seasons such as "Part 2"). MyAnimeList and AniList are still used as backups when TMDB has nothing.

## [1.6.4] - 2026-11-14

Trailer now covers the whole hero.

### Fixed
- **The trailer no longer looks cut off.** It only filled the top part of the hero and the rest was black. The player is now sized by the app (16:9, enlarged to cover the hero and clipped), so the video fills the hero from the top down to behind the title and genres, like the reference.

## [1.6.3] - 2026-11-14

Details page redone to match the reference.

### Fixed
- **Trailer fills the hero.** It was stuck in a strip at the top; the video now covers the whole hero (sized from the real view size) and YouTube's own buttons no longer pop up over it.
- **Episode names.** Each episode now shows its title ("1. Cruelty") plus air date and thumbnail, from MyAnimeList.
- **Seasons are complete.** The season dropdown follows the whole franchise in both directions, so every season, part ("Season 3 Part 1", "Season 3 Part 2"), ONA and movie is listed in release order.

### Changed
- Layout now follows the reference: black status bar, rating chip, title and genres over the trailer, Play button, left-aligned My List / Rating / Share, two-line synopsis with an inline "More", muted "Oldest" sort and "N Episodes" with a download icon.

## [1.6.2] - 2026-11-14

Faster details page and trailer fix.

### Fixed
- **The trailer no longer cuts off.** It was drawn in a small strip at the top of the hero; the video now fills the whole hero area.

### Improved
- **Much faster loading.** The page data and the trailer lookup now run at the same time instead of one after the other, and trailers appear as soon as TMDB answers.
- **Instant reopen.** Anime you have already opened show immediately and refresh in the background, so the data (episodes, next episode, ratings) is always the live AniList data.
- **Rust title matching.** The native Rust library scores how closely TMDB results match the anime title, so the right show's trailer is picked and a different show's trailer is never used.

## [1.6.1] - 2026-11-14

Trailers now come from TMDB.

### Fixed
- **Trailers on the details page now actually play.** The trailer is looked up on **TMDB** (the anime is matched by title and year, official trailers first), then AniList, then MyAnimeList. If a video can't be embedded or doesn't start within 12 seconds, the next one is tried before falling back to the banner image.
- The trailer player now loads from a normal web origin, which fixes YouTube's "video unavailable / configuration error" in the in-app player.

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
