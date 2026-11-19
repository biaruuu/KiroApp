# Changelog

All notable changes to Kiro are documented here. Every GitHub release publishes the
matching section below as its release notes, so add an entry for each new version
**before** tagging it.

## [1.43.5] - 2026-11-19

### Changed
- Minimalist profile card: flat dark card with a thin border, outlined buttons, plain level label, and a quiet badge strip. The card sits higher on Me, and the streak text in the strip is gone.
- Watch stats cards are flat and calmer too (no tinted gradient or coloured border).

## [1.43.4] - 2026-11-19

### Fixed
- Build fix for the 1.43.3 release; same changes as 1.43.3 (smaller responsive profile buttons, diary-style watch stats).

## [1.43.3] - 2026-11-19

### Changed
- Profile card: Edit Profile and Share are now small pill buttons, and the whole card (avatar, name, buttons, badge strip, flame) scales with the screen width so it keeps its proportions on any phone.
- Watch stats redesigned in a diary style: heavy uppercase headings, an Activity Streak block with a 28-day block bar and best streak, Recent Activity (filter by Watched, Finished, Added, Rated; tap to open the show) and Your Library (counts, statuses, and poster strips of Completed and My List, with View all).

## [1.43.2] - 2026-11-19

### Changed
- Profile card is now compact (smaller avatar, name, buttons and badge strip) so it reads as a neat box that scales with the screen width, and the streak flame's glow no longer shows a square edge.

## [1.43.1] - 2026-11-19

### Fixed
- Build fix for the 1.43.0 release (watch stats chart); same features as 1.43.0.

## [1.43.0] - 2026-11-19

### Added
- Watch stats (Me > Watch stats): hours watched this week and this month, total time, total episodes, a daily chart (week or month, tap a bar for that day) and your most-watched genres. Time is counted while a video really plays.
- Streak badge: an animated flame with a day counter, like a daily-streak badge. It changes colour and animation as the streak grows: Spark, Flame, Blaze, Inferno, Violet Fire, Blue Flame, Golden Blaze and Legend (a shimmering rainbow). It dims when you haven't watched yet today, and shows a Monday to Sunday week row.
- XP and levels: watching, finishing episodes and series, daily activity, ratings, streak milestones and badges earn XP. Your level shows on your profile card and on the stats page, with progress to the next level.
- Profile card: Me now opens with a new card (avatar, bold display name, level, XP bar, your streak flame, Edit Profile, Share and a Badges strip with View All).

### Changed
- Avatar decorations are now earned with level (levels 3, 6, 10, 15, 20, 25, 30, 40, 50, then every 10). Locked ones show the level they need; the one you already wear stays selectable.
- Search sorting: Highest rated and Oldest first are back (only the Filters button was removed).
- Watch time and stats sync with your account and are included in backups.

## [1.42.1] - 2026-11-19

### Removed
- Search filters (the Filters button, and the Highest rated / Oldest first sorting added with it). Search is back to the genre tabs and the original sort options.

## [1.42.0] - 2026-11-19

### Added
- Library statuses: mark a show Watching, Plan to watch, On hold or Dropped with the new Status button on its page. Each status has its own tab in Library. Setting a status also adds the show to My List, and statuses sync with your account.
- Smart search filters: a Filters button under the genre tabs combines genre (several at once), year, season, format and airing status. Sorting now also offers Highest rated and Oldest first.
- Download queue manager: Library > Download shows a queue banner (count and total speed). Manage it to pause or resume one or all downloads, move a download up or down, or download it first, and see each download's speed and time left. Downloads run one at a time in the order you set.
- Backup and restore (Me > Backup and restore): export your list, ratings, progress and statuses to a file, and import it on a new phone or another account. Importing merges, so nothing is deleted.

## [1.41.1] - 2026-11-19

### Removed
- Franchise watch order (added in 1.41.0) is removed again; the app behaves as in 1.40.1.

## [1.40.1] - 2026-11-19

### Changed
- Filipino now carries a BETA badge in Me > Language, with a note that some text may still show in English.

## [1.40.0] - 2026-11-19

### Added
- Filipino (Tagalog) interface: pick it in Me > Language. Menus, buttons, messages, toasts and notifications are translated; anything not translated yet stays in English.
- Share card: the Share button on a show now makes a neat image with the poster, score and your rating, ready for social apps.
- App icon shortcuts: long-press the Kiro icon for Continue watching, Schedule and Downloads.

## [1.39.2] - 2026-11-18

### Changed
- Report broken episode now only appears when an episode really is broken: when no provider has it, every source failed, or it stays stuck loading for 25 seconds. It no longer sits in the player menu.
- The report finds the cause by itself (which provider, the error and HTTP code of each failed source, or the stall) and can be sent once per episode.

## [1.39.1] - 2026-11-18

### Changed
- Notification control redesigned: white small switches, grouped cards, quiet-hours time chips and a layout that fits every phone width.

### Removed
- Home layout (Me > Home layout) is removed; Home is back to its fixed order.

## [1.39.0] - 2026-11-18

### Added
- **Storage** (Me > Storage): a bar and list of what takes space (downloads, cache, app data), downloads by show, and one-tap Clear cache, Delete watched episodes and Delete all downloads.
- **Notification control** (Me > Notification control, was Episode alerts): switch alerts off per show, and set quiet hours; episodes that air meanwhile are announced when quiet hours end.
- **Home layout** (Me > Home layout): move Home rows up or down, hide any of them, or reset.
- **Offline banner**: a banner says when you have no internet and jumps to Downloads; opening Kiro offline starts on the Download tab.
- **Report broken episode** (player menu): one tap sends the show, episode, provider and quality to the developer's admin panel (or opens email when signed out). New Reports tab in the admin panel.
- Haptic taps on tab switches and the player's play, pause and 10 s buttons; smoother fades between tabs and a slide-in for pages.

### Note
- Publish the updated firestore.rules so broken-episode reports can be saved.

## [1.38.2] - 2026-11-18

### Removed
- The Anime enhancer (4K upscale and line sharpener) and its Anime4K shaders are removed; the player is back to how it was in 1.37.3.

## [1.37.3] - 2026-11-18

### Changed
- Watch party is tighter again: a guest reacts the instant the host's update arrives (and rechecks every 0.25 s) instead of waiting for the next check, the time the update spent travelling is added to the host's position, drift is corrected from 0.7 s (0.25 s while paused), and the host sends its position every 1.5 s.

## [1.37.2] - 2026-11-18

### Fixed
- Watch party now stays in sync in real time: a guest no longer starts playing while the host is paused, and the host's play, pause, seek and buffering reach everyone immediately. Guests check the host every 0.4 s and correct any drift over 1.5 s (0.5 s while paused); the host also sends its position every 2 s while playing.

### Added
- A chip at the top of the player in a watch party shows the avatars (with their decorations) of everyone in the party. Tap it to see everybody, each with their username in their chosen font, and who the host is.

### Removed
- The Picture in picture row in the three dots menu. Picture in picture still opens automatically when you press Home while a video plays.

## [1.37.1] - 2026-11-18

### Changed
- Home: one recommendation row instead of two. It is called "Recommended for you" and is based on the single latest anime you watched or finished.
- Trending Now now sits directly under Continue Watching, with Recommended for you right below it.

## [1.37.0] - 2026-11-18

### Added
- **Watch party (real time):** in the player, open the three dots menu > Watch party > Start, then share the 6 letter code. Friends enter it in Me > Watch party and their player opens on the same episode and follows the host: play, pause, seeking and next episode, with the list of who is watching. The host ends the party, guests can leave any time. You need to be logged in.

### Needs one step from the developer
- Publish the updated `firestore.rules` file (Firebase console > Firestore Database > Rules > paste the file > Publish). It adds the `parties` rules. Until then, starting or joining a party shows a message that it isn't switched on yet.

## [1.36.0] - 2026-11-18

### Added
- **Schedule tab:** a new bottom tab with the weekly airing schedule. One page per day for the next 7 days, in your own time zone, with the Your Library style tab strip, the air time, a live "in 2h 10m" countdown, an "In your list" tag, and a My list only filter.
- **New episode alerts:** a notification when an anime in My List or Continue Watching airs a new episode (checked every 30 minutes). Tap it to open the anime. Turn it on or off in Me > Episode alerts, which also lists what from your list airs this week.
- **Because you watched rows** on Home: recommendations based on what you watched lately, without anime already in your library.

## [1.35.0] - 2026-11-18

### Added
- **Picture in picture:** pressing Home while a video plays shrinks it into a floating window, or use Menu > Picture in picture in the player. Closing the window stops playback.
- **Remembered choices:** the player now remembers your subtitle choice (off or language), playback speed and quality between episodes and app launches (the sub/dub choice was already remembered).
- **Storage limit:** Me > Download > Storage limit (No limit, 2, 5, 10, 20 or 50 GB), with the space your downloads use shown below it. New downloads are blocked, and running ones paused, when the limit is reached. Wi-Fi only was already there.
- **Achievements:** a new page in Me with 20 badges (episodes watched, series completed, ratings, My List, downloads, watch streaks, night owl, watch parties). A message appears the moment you earn one.

## [1.34.3] - 2026-11-18

### Fixed
- Playback no longer sits loading at 4K: Auto now adapts to the real connection speed and tops out at 1080p. Choose 1440p or 2160p manually in Quality when you want them.
- Faster start and recovery: playback begins after 1.5 s of data, rebuffers resume after 3 s, and a deeper buffer (up to 60 s) is kept on good connections.

## [1.34.2] - 2026-11-18

### Changed
- Player: 4K (2160p), 1440p and higher resolutions are no longer capped. Auto starts from the best quality the phone can decode instead of the screen size, and every available quality shows in the Quality list.
- Back to the previous loading spinner and the previous 10-second back and forward icons.

## [1.34.1] - 2026-11-18

### Changed
- Player: the 10-second back and forward buttons now use a Laftel-style icon: a nearly closed ring with an arrowhead and a bold 10 inside, slightly larger.
- Smoother loading spinner: its animation is now drawn frame by frame without recomposing the screen, so it no longer stutters.
- Source ranking now puts resolution first, so the highest-quality working source is chosen, with speed as the tie-breaker.

## [1.34.0] - 2026-11-18

### Added
- More streaming providers from the animestream app: **AnimePahe** (Kwik HLS, sub and dub, several qualities), **AniZone** and **Gojo**. They run alongside the existing providers, so more servers appear in the source list.
- Accurate matching for providers that search by name: every result is scored against all of the anime's titles, season number, year and episode count, and is only used when it is a confident match. The episode is chosen by its real number, never by list position, so the wrong season or episode is not played.

### Removed
- The gold trophy next to the COMPLETED stamp.

## [1.33.5] - 2026-11-17

### Changed
- Completed-anime trophy redrawn: realistic polished-gold cup with a lid and star finial, engraved laurel band, sculpted handles, black marble base and gold nameplate. Still static.

## [1.33.4] - 2026-11-17

### Changed
- The green COMPLETED stamp is straight now, and a gold trophy (still image, no animation) sits to its right.

### Fixed
- The loading spinner in the player now really spins. It was Material's spinner, which freezes when the phone has animations turned off or scaled down; Kiro now draws its own, driven by the screen's frame clock.

## [1.33.3] - 2026-11-17

### Changed
- Removed the blue Completed badge from the top of the details page. A green "COMPLETED" rubber stamp now sits to the right of the award medal instead.

### Fixed
- The loading spinner in the video player is now white and clearly visible (it was drawn under the dimming layer and looked grey and stuck). It shows "Finding the best source..." while the episode is being looked up.

## [1.33.2] - 2026-11-17

### Changed
- The Award medal is no longer a button and has no label. It appears by itself, to the right of Share, once you have finished an anime.
- New Completed badge: a blue seal with a banner reading "COMPLETED" and curved lettering, at the top right of the details hero with no background.

## [1.33.1] - 2026-11-17

### Changed
- Redrawn badges. The Award medal is now an animated PNG: a gold medal with a star and laurel wreath that has a soft shimmer sweeping across it and little sparkles. The Completed stamp is a new, static design with a check mark, tick ring and "Finished watching / Well done" text.

## [1.33.0] - 2026-11-17

### Added
- **Award**: a gold medal button on the details page, just left of Share and styled like My List, Rate and Share. Tap it to give an anime your award; it stays dim until you do.
- **Completed stamp**: once you have finished an anime, a "Completed" stamp (no background) shows at the top right of its details hero.

## [1.32.0] - 2026-11-17

### Changed
- Library > Download now shows a poster card per series, like My List. As soon as you download an episode, its series card appears (with an episode count and a dot while it is still downloading). Long-press a card to delete the series' downloads.
- Tapping a series opens its own downloaded-episodes page: every episode on its own card with a thumbnail, number, title, description, size, and live progress. Tap an episode to watch it offline, or the cross to delete it.

## [1.31.2] - 2026-11-17

### Fixed
- Download progress now updates live: the percentage and size refresh every second in Library > Download, and the episode's download icon shows the percent inside its ring.

### Changed
- Library > Download has a new layout: each series is a card with its episodes under it, a progress bar and percentage per episode, and larger tap areas.
- Audio & Subtitles is back to the earlier style. When an episode has both, the Audio list simply shows Japanese and English (English is the dub); pick one and press Apply.

## [1.31.1] - 2026-11-17

### Added
- Switch between Japanese (sub) and English (dub) while watching: open Audio & Subtitles and pick the language at the top. It keeps your place, and Kiro remembers your choice for the next episode.

## [1.31.0] - 2026-11-17

### Added
- More streaming providers, the same sources the Nivio app uses: **Animetsu** (Bee, Koto, Wave, GG), **Animex** and **Miruro**. Each is found by the anime's AniList id, checked before it is offered, and shown in the source list with sub or dub.
- Sources that need their own Referer / Origin now play, and download for offline viewing, with the right headers.

### Changed
- Episodes start as soon as the first provider answers instead of waiting for all of them.

## [1.30.1] - 2026-11-17

### Fixed
- Downloads that fail are now retried automatically a few times, keeping what was already saved, and you can tap a failed download in Library > Download to retry it.
- A failed download now says why, so a real cause can be found.

## [1.30.0] - 2026-11-17

### Added
- **Downloads and offline viewing.** Tap the download icon on any episode to save it to your phone. It shows progress while saving and a tick once it is saved, and downloading continues in the background with a notification.
- The Library > Download tab lists your downloaded episodes with progress and size. Tap one to watch it with no internet; subtitles are saved with it.
- Settings: Download quality limits the picture quality, Wi-Fi only holds downloads until you are on Wi-Fi, and Delete all downloads clears them.

## [1.29.5] - 2026-11-17

### Fixed
- Continue with Google no longer stops at "No Google account found on this device." If the phone's account picker can't be used, Kiro opens Google's own sign-in page instead.
- Login errors now say what Firebase actually reported, so a real cause can be found instead of "Something went wrong".

## [1.29.4] - 2026-11-17

### Changed
- Contact us has a new layout: the topic is picked from a clear list instead of tab buttons, so nothing is cut off on any screen size.
- Sharper artwork: posters and cards use the high-resolution cover, and the Home hero and the details page hero now use the full-resolution poster when one is available.

## [1.29.3] - 2026-11-17

### Changed
- Removed library effects, library decorations and the GTA font import. Avatar decorations and the built-in name fonts stay.
- Notifications show only the latest two versions.
- HDR & Dolby Vision is back to the earlier behaviour: "On" turns on hardware tunneling and the HDR display mode.

### Fixed
- Skip Intro / Recap / Outro: the lookup no longer restarts when the video length changes while loading, and it accepts stored cuts up to 12 seconds off the playing length, so the button appears more reliably. Episodes with no matching timestamps still show no button.

## [1.29.2] - 2026-11-17

### Fixed
- Library effects are now a soft tint behind the header instead of a photo-like picture, so the header looks like the normal one.
- Adding the GTA font: there is now an "Add GTA font file" button, and if a file can't be used the message says why.

## [1.29.1] - 2026-11-17

### Fixed
- Library effects and decorations no longer make the Your Library header bigger. They now play inside the same slim header you get with "None".

### Added
- **GTA** name style (Pricedown Bl). Tap it in Customize profile and pick your Pricedown Bl.otf file once; it stays on your phone.

## [1.29.0] - 2026-11-17

### Added
- **Aurora Night**, the first Library Effect: slow, glowing northern-lights ribbons and twinkling stars behind your library card.
- **Starlight Frame**, the first Library Decoration: a light that travels around the card's edge, with twinkling sparkles and drifting particles.
- Both are in Customize profile. Pick one or both to see them on the card at the top of Your Library.

## [1.28.0] - 2026-11-17

### Added
- **Library Effect** and **Library Decoration**: pick an animated effect and a decoration for the profile card at the top of Your Library. The card also shows your avatar decoration and name font. Tap it to change them.

### Changed
- Customize profile is back to the same style as the other rows in the Me tab.
- Nameplates and profile effects are gone, replaced by the library looks above.

### Fixed
- Animated avatar decorations now keep moving on the Notifications page and everywhere else.
- Larger animated images (up to 5 MB) are shrunk to fit so they play smoothly.

## [1.27.1] - 2026-11-17

### Fixed
- Animated PNG decorations now really move, on every Android version. Before, many phones showed them as a still picture.

### Added
- **Import a font file** under Display Name Style: pick a .ttf or .otf from your phone (for example Pricedown) and use it for your name. It stays on your phone.
- Customize profile has its own card on the Me tab, and tapping your name at the top of Your Library opens it too.

## [1.27.0] - 2026-11-17

### Added
- **Customize profile** (Me tab): choose an animated avatar decoration, a profile effect, a nameplate and a font for your display name, with a live preview. Your picks show on your picture in Your Library, the Me tab and notifications.
- **Cat Ears**, the first animated avatar decoration.
- **Name styles**: Satoshi, Cinzel, Roboto Mono, Comic Neue, Poppins and Montserrat for your display name.
- New decorations, effects and nameplates can now be added to Kiro without an app update.

## [1.26.1] - 2026-11-17

### Changed
- **Contact us** is redesigned: pick what it's about (Bug, Suggestion, Question or Other), write in a roomy box, and see a confirmation when it is sent.
- Notifications show the developer's own username and profile photo with a short "Dev" tag.

### Fixed
- Admin panel tabs use white instead of purple, and problems saving or reading announcements are now shown instead of failing silently.

## [1.26.0] - 2026-11-17

### Added
- **Messages from the developer**: announcements now appear in your Notifications, and as a phone notification right away while Kiro is open. Every notification shows the developer's profile photo with a blue verified tick and a Developer badge.

### Changed
- Privacy Policy now mentions that, while you are signed in and Kiro is open, the developer can see that you are online and what you are watching.

## [1.25.0] - 2026-11-17

### Added
- **Notifications page**: tap the bell on Home or Notification in the Me tab to see every Kiro update. Tap one to read the full notes and update. A red dot shows when something new is waiting.
- **Next Episode prompt**: 15 seconds before an episode ends, a Next Episode button appears and fills up as the end gets closer. Tap it to jump ahead, or dismiss it with the X.

### Changed
- New versions now reach you much faster. While Kiro is open it checks every minute, and in the background it checks every 15 minutes, instead of every few hours.

## [1.24.1] - 2026-11-17

### Added
- **Satoshi subtitle font**, now the default caption font for new installs. Pick it any time under Subtitle style.

### Fixed
- HDR detection now reads every display mode and format your screen reports (HDR10, HLG, HDR10+, Dolby Vision), and the HDR & Dolby Vision panel lists what your screen supports.

## [1.24.0] - 2026-11-17

### Added
- **True HDR playback**: when a video is HDR10, HLG or Dolby Vision and your screen supports HDR, Kiro now switches the screen into HDR mode for that video and back to normal afterwards. The HDR & Dolby Vision row in the player menu shows which format is playing (for example "HDR10") and explains if your screen can't show it.

### Changed
- HDR no longer needs to be turned on by hand. The "On" option now only adds hardware tunneling, which is off by default because it doesn't work with every video.

## [1.23.1] - 2026-11-16

### Changed
- **Auto Skip Intro/Outro** is now a row in the player menu, just like HDR & Dolby Vision: tap it to choose On or Off.
- The 3-dots menu slides in and sizes itself to your screen, so it fits phones and tablets.
- Subtitle style shows just the font name (for example Trebuchet MS).

### Fixed
- Skip buttons are now accurate: Kiro only uses timestamps confirmed for the exact version of the episode you are watching. If none exist, no skip button or marker is shown instead of a guess.

## [1.23.0] - 2026-11-16

### Added
- **Auto Skip Intro/Outro**: a new switch in the player's 3-dots menu. When it is on, Kiro skips the intro, outro and recap for you after a short countdown. Turn it off to skip by hand.
- **Skip markers on the seek bar**: the parts you can skip (intro, outro, recap) are highlighted in amber on the progress line, so you can see where a skip will happen.

### Changed
- The skip button is bigger and easier to see: a white button that fills while auto skip counts down, and it moves up out of the way of the controls when they are showing.

### Fixed
- Tapping Next Episode many times in a row no longer ends in "Couldn't play this video". Kiro now loads only the episode you land on.
- Trailers on the details page appear more reliably, even when a source is slow or temporarily unavailable.
- The update notification now shows up reliably: Kiro also checks when you open the app, and the notification is shown with high priority.

## [1.22.0] - 2026-11-16

### Added
- **Skip Intro, Skip Outro and Skip Recap** buttons in the player. They appear only while the opening, ending or recap is playing, and tapping one jumps straight to the end of it. Timestamps come from Aniskip and are matched to the exact length of the episode you are watching, so they line up with your version of the episode.

## [1.21.0] - 2026-11-16

### Added
- **Update notifications**: Kiro checks for a new version in the background and sends a notification. Tapping it opens a What's new page with everything that changed and an Update Now button that downloads and installs the update inside the app.
- **What's new after updating**: the first time you open Kiro after an update, a full page shows what's new and what was fixed.
- **Help Center** with answers to common questions, and **Contact us** to send a message from inside the app.

### Changed
- The login page now plays one trailer across the top of the screen under a black status bar and fades into the dark page before the buttons, and it no longer switches shows by itself.
- Terms of Use and Privacy Policy have their own icons, and the License entry is removed from the Me tab.

### Fixed
- YouTube's player buttons (previous, pause, next) no longer appear over trailers on the login and details pages.

## [1.20.0] - 2026-11-16

### Added
- **Login with a trailer background**: trending anime trailers play muted behind the welcome page and rotate, fading into the dark page at the bottom (a still shows first, or if a trailer can't play).
- **Continue as Guest** replaces the X. It opens a full page asking "Are you sure you want to continue as a guest?" and explains that progress, My List, ratings and watched episodes are not saved.
- **Terms of Use and Privacy Policy** pages. The two links on the login page now open them, and both are listed in the Me tab.

### Changed
- The KIRO text is back on Home and on the login page.
- Delete account now also deletes the saved profile and library from the cloud, after checking that the sign-in is recent enough.

## [1.19.3] - 2026-11-16

### Changed
- **New splash screen**: the Kiro logo centered on the dark background and fading in, like Laftel's. The logo is no longer repeated as text on Home or the login page.

## [1.19.2] - 2026-11-16

### Fixed
- Opening Your Library crashed the app: the tab bar only had room for four tabs after the Completed tab was added.

## [1.19.1] - 2026-11-16

### Changed
- **Rating, Laftel style**: a new rating sheet with a close button, a big score, a caption that changes with the score (Disappointing :( ... A MASTERPIECE), five stars you can tap or drag, and a Confirm button that turns purple once you pick a score.
- **Half stars**: tap the left half of a star for .5 and the right half for a whole star. Earlier whole-star ratings are kept.
- The details page shows your score under a white star, and the Rated tab shows it as 4.5 in purple.

## [1.19.0] - 2026-11-16

### Added
- **Completed tab** in Your Library: anime you have watched to the last episode are tracked and listed automatically (long-press to clear).
- **Watched badge** on episode thumbnails (details page and the player's episode list) for episodes you have finished. Watched episodes sync with your account.
- **Rewatch prompt**: tapping a watched episode (or Play on a watched first episode) asks "Are you sure you want to rewatch this again?" with No / Yes, every time.
- Your profile photo and username now appear at the top of the library.

### Changed
- The bottom bar tab is now **Your Library**, and the Peek placeholder tab is removed.

## [1.18.1] - 2026-11-16

### Changed
- My List, ratings and Continue Watching now need an account. Logged-out users who tap My List or rate an anime are asked to log in, and nothing is saved on the phone alone.

## [1.18.0] - 2026-11-16

Profiles and a library that follows you.

### Added
- **Username, bio and profile photo.** New accounts get a full-page setup after verifying: Enter your username, then Next for a bio, then Next to upload and crop a photo. Bio and photo can be skipped.
- **Edit your profile** any time in Me > Account Setting: change the photo (pick, drag and pinch to crop), username and bio. The Me header shows your photo and username.
- **Library saved in the cloud.** My List, ratings and Continue Watching are stored in Cloud Firestore and update in real time across your devices (works offline and syncs when you are back online). Logging out clears this phone's copy.
- `firestore.rules` so each account can only read and write its own data.

### Changed
- Home: smaller section titles and Laftel-sized poster cards; a black strip keeps content from running under the clock once the hero scrolls away.

### Setup
- In the Firebase console create a **Firestore Database** and publish the rules from `firestore.rules`.

## [1.17.0] - 2026-11-16

Search, Laftel style.

### Added
- **Recent Searches**: tapping the search field shows your last searches with Clear and a remove button on each; tap one to search it again.
- **Suggestions** while typing: matching titles with the part you typed in purple.
- **Cancel** button and a clear (x) button in the field while searching; Back also closes the overlay.
- The white line under the genre tabs now slides to the picked genre, and the tab row scrolls to keep it in view.

### Changed
- Search resized to Laftel's proportions: 32 dp field, smaller genre tabs, 52 dp results row, flatter posters with 13 sp titles, and a slimmer sort sheet.

## [1.16.3] - 2026-11-16

### Changed
- Library resized to Laftel's proportions: 48 dp title bar, 44 dp tab row, smaller text, thumbnails and posters, and a centred empty state.
- Bottom navigation is slimmer (50 dp, 22 dp icons, smaller labels) and Library uses an inbox icon.

### Added
- A white line under the selected Library tab that slides between tabs; swipe the page or tap a tab to switch smoothly.

## [1.16.2] - 2026-11-16

### Changed
- Login, Me and settings screens resized to match Laftel's proportions: smaller text, 48 dp top bars and buttons, tighter rows, and a full-width logout dialog.

### Added
- The "Verify your email" screen now reminds you to check your spam folder.

## [1.16.1] - 2026-11-16

### Fixed
- Rebuilt with the updated Firebase config so Google sign-in works (the 1.16.0 build was made before Google was enabled).

### Changed
- The build now warns when `google-services.json` has no Google web client.

## [1.16.0] - 2026-11-16

Login and Me tab redesign.

### Added
- **Welcome screen** with Continue with Email and Continue with Google, a "Last login" hint and a close button to look around as a guest.
- **Continue with email** flow: email step with a live format check, then password (log in or create account), forgot password.
- **Google sign-in** through Credential Manager and Firebase.
- **Me tab**: greeting header, Settings list (Notification, Download, Play, Language) and Help Center, Contact us, License and Version.
- **Account Setting** with Logout confirmation and Delete account.
- **Play setting**: Autoplay previews now turns the details-page trailer on or off.
- **Download setting**: Wi-Fi only and Video quality (saved for when downloads arrive).
- **Version** page checks GitHub for the latest release and opens it from Update Now.

### Changed
- Sign-up no longer asks for a username; your name is taken from your email.

## [1.15.0] - 2026-11-16

Accounts.

### Added
- **Login / Sign up** with email and password on Firebase Authentication, with a username.
- **Email verification**: a link is emailed at sign-up and the app stays locked until it is confirmed (it continues on its own when you come back from your mail app). Resend is available after 30 seconds.
- **Forgot password** reset email.
- **Me tab** shows your account and a Sign out button.

### Setup
- Sign-in turns on only when the build has `google-services.json` (CI writes it from the `GOOGLE_SERVICES_JSON` secret). Without it the app runs with sign-in off.

## [1.14.0] - 2026-11-16

The Library tab.

### Added
- **Library** with four tabs: Recently Watched, My List, Download and Rated.
- **Recently Watched**: your episodes with progress bars; tap to resume, the (i) button opens the anime's details.
- **My List** and **Rated** posters grid with sorting (Recently added, Recently updated, Highest average rating).
- Long-press a title in Recently Watched, My List or Rated to remove it.
- Ratings now remember the title and poster so they can appear in Rated.

### Notes
- Download shows its empty state only; offline downloads are not available yet.

## [1.13.1] - 2026-11-15

Subtitles back to how they were.

### Changed
- Caption style matches Nivio exactly, including the subtle shadow colour.

### Removed
- Subtitle delay, "Load subtitle file" and "Subtitle from web". Subtitles play as before, straight from the provider.

## [1.13.0] - 2026-11-15

Player features from Nivio.

### Added
- **Audio boost (+10 / +20 dB)** for quiet dialogue, using Android's loudness enhancer on the player's audio. Remembered between videos.
- **Subtitle delay** in 250 ms steps (−30 s to +30 s), saved per anime. Works on the API's subtitles and on your own files; times are rewritten and the video resumes at the same spot.
- **Load subtitle file** (.srt / .vtt from the phone) and **Subtitle from web** (paste a link). They appear as "My subtitles" in Audio & Subtitles, follow your caption style and respect the delay.
- **HDR & Dolby Vision** setting: Auto leaves it to the phone; On adds hardware tunneling and an HDR display mode.

### Not included
- Nivio's libmpv engine. HDR10 and Dolby Vision play through the phone's own hardware decoder in Media3, so they depend on the stream, the phone and its screen.

## [1.12.0] - 2026-11-15

### Added
- **FFmpeg native audio decoders** (C/C++ through the NDK, bundled for arm64-v8a, armeabi-v7a and x86_64). When a phone can't decode a stream's audio (AC3, E-AC3, DTS, FLAC, ALAC and others), FFmpeg decodes it in software instead of leaving the video silent. Phone decoders are still tried first, so there is no cost for normal streams.

### Not added (and why)
- **libass (styled ASS subtitles):** the streaming API only provides WebVTT/SRT subtitles, so there is nothing for libass to render yet.
- **dav1d (software AV1):** no prebuilt, published build is available for this player version; it would need a separate native build.

## [1.11.0] - 2026-11-15

Performance release.

### Added
- **Skeleton loaders** instead of spinners: Home (hero and poster rows), Search (the 3-column poster grid), the Details page and More details, and the episode list. They are flat placeholders that pulse, drawn without recomposing, so they cost almost nothing.
- **Instant search while you type (Rust):** matches from the anime already loaded on the device are ranked in Rust (prefix, partial word and reordered-word matches) and shown immediately; the real AniList results replace them a moment later.
- **On-disk API cache (Java):** lists (Home, Search) are reused for 10 minutes and the Details page (with airing times) for 2, with no request and no waiting. If the network drops, the last answer is shown instead of an error. The cache is capped at 24 MB.

### Changed
- Faster networking: HTTP/2, a larger connection pool and up to 16 parallel requests per host, so posters, trailers and episode data load side by side.

## [1.10.1] - 2026-11-15

### Changed
- **Details page is back to how it was**: the anime's title as text, with no title logo, IMDB rating or parental guidance badges. (Continue Watching and the Home hero logo stay.)

## [1.10.0] - 2026-11-15

### Added
- **Continue Watching** on Home (under the hero): the episode you were on, with its thumbnail, a purple progress bar and "Episode N". Tapping it opens the player and **resumes from where you left off**. Progress is saved every 5 seconds, when you leave the player or the app, and a finished episode moves on to the next one (the last episode just disappears from the row).
- **Anime title logos** (TMDB artwork, English first) instead of plain title text on the Home hero and the Details page, with the text as a fallback.
- **IMDB rating and PARENTAL GUIDANCE badges** under the logo on Details, as on the web app: score from TMDB, age rating from TMDB content ratings (India first, then the US, shown as U / U/A 7+ / U/A 13+ / A 18+).
- A soft fade behind the Home hero's title, tagline and Play button, like the Details page.

## [1.9.7] - 2026-11-15

### Changed
- **Episodes** panel redesigned like Netflix: a wide black panel opening on the episode you're watching, each row with its thumbnail (play button, purple progress bar on the current one), "1. Title", length in minutes and the plot summary.
- **Audio & Subtitles** now uses the same pure black as the three-dot menu.
- **Subtitle style** panel is shown entirely in Trebuchet MS, so every option reads clearly instead of each name in its own font.

## [1.9.6] - 2026-11-15

### Changed
- **Audio & Subtitles** is now a dark rounded card with two columns (Audio and Subtitles, with "Off"), check marks, a close X and **Cancel / Apply** buttons. Your picks only take effect when you press Apply.
- **The three-dot menu** is now pure black with compact rows (label, value, chevron) and just a close X, like the reference. Quality, Subtitle style and Playback speed open as black panels too.

## [1.9.5] - 2026-11-15

### Changed
- **Captions now follow the Nivio style exactly:** white text at 1.1x the chosen size (Small 14, Medium 18, Large 24, Extra Large 30), semi-bold, 24dp from the sides and 20dp above the bottom; Transparent / Semi-Transparent / Solid backgrounds; None / Subtle Shadow / Outline text styles.
- **Real Trebuchet MS** (your font file) is bundled and is the default caption font, shown in its own typeface in the list.
- Font list matches the reference: Trebuchet MS, Roboto, Inter, Open Sans, Poppins, Montserrat, Cinzel, Roboto Mono, Comic Neue, and Custom (load your own .ttf / .otf). A custom font gets the same semi-bold look.

## [1.9.4] - 2026-11-15

### Changed
- **Subtitle style is back** in the player menu: font (shown in its own typeface), size, background and text style, saved and applied instantly. Loading your own font file (.ttf / .otf) works again too.
- **Default subtitle font is now Trebuchet MS-style.** Netflix Sans was the wrong choice (Netflix captions use Trebuchet MS). Android doesn't include Trebuchet MS and it can't be bundled, so the default uses Open Sans, the closest bundled match; load the real Trebuchet MS file with Custom Font for an exact match.
- Server names and the source list stay hidden; streams are still picked automatically.

## [1.9.3] - 2026-11-15

### Fixed
- **"Couldn't play this video" on every episode.** The API's stream address (`/api/proxy/hls?url=...`) has no `.m3u8` ending, so the player took it for a plain video file and failed to read the playlist. Streams are now opened explicitly as HLS, which also lets the Quality list show the real resolutions (Auto + each available one).
- If playback still fails, the message now includes the player's error code so the cause can be pinpointed.

## [1.9.2] - 2026-11-15

Videos that wouldn't play, and exact season checks.

### Fixed
- **"Couldn't play this video".** Found in the Anivault API source: its rate limit (60 requests a minute) also counted every video segment, so playback was blocked within seconds. The API now leaves video/subtitle proxying out of the limit (API change, pushed to its repo; it needs a redeploy on Render).
- **Faster start.** MegaPlay is now looked up directly by the exact season's id instead of first searching every other site.
- **Season check no longer rejects good matches.** Each source's episode list carries the MAL id of its season, so it is compared exactly with the season you opened; the name check (Rust) is only the fallback. A source is verified once per episode instead of per audio type.
- Far fewer requests per episode: stream quality and speed are measured while the stream is fetched, so there is no second speed test.

## [1.9.1] - 2026-11-15

Correct season every time, plus a simpler player menu.

### Fixed
- **Season 2 played season 1 episode 1.** Some sources find a show by searching its title and could land on the first season. Each source is now checked against the exact season before it is used: the name of the show it found must be the same season (scored in Rust, with unit tests) and it must really have the requested episode, otherwise it is skipped. MegaPlay is now asked by the MAL id of that exact season, so it can never pick another one.

### Changed
- **Quality picker** in the player menu: Auto (adapts to your network) or any resolution the stream offers (for example 1080p, 720p, 480p).
- Servers are chosen fully automatically (best quality, fastest) and failover on its own. Server and provider names are no longer shown anywhere.
- Subtitles now always use the default Kiro style; the subtitle style editor, subtitle file loader and "My sources" are removed from the player.

## [1.9.0] - 2026-11-15

Real streaming through the Anivault API, with the best server picked automatically.

### Added
- **Anivault provider** (`kiroapp-api.onrender.com`). Each episode asks Anikoto, MegaPlay, DesiDub and AnimeHeaven at the same time, for both sub and dub. Playback starts as soon as the first source answers (a short grace period lets the others join), so one slow server never holds you up.
- **Best quality, fastest server, automatically.** The sharpest resolution is read from each HLS playlist, every address is timed, and the ranking (Rust) puts the best first. Subtitled sources come before dubbed ones. If a server fails, the player **fails over to the next** one by itself.
- **Subtitles from the API** are loaded with the stream (English first) and use your subtitle style.
- **Source list** in the player menu now shows server, Sub/Dub, quality and speed, and lets you pick any of them.
- The streaming server is **woken at app start**, so the first episode doesn't wait for a cold start.

### Changed
- Addresses from Anivault are never dropped for a slow speed test; they are ranked last instead.

## [1.8.1] - 2026-11-15

Subtitle style revamp and no more sample video.

### Changed
- **Subtitle style** is now a list in the same layout as Nivio: FONT, SIZE (Small, Medium, Large, Extra Large), BACKGROUND (Transparent, Semi-Transparent, Solid) and TEXT STYLE (None, Subtle Shadow, Outline). The chosen row is highlighted with a tick, each font previews itself, and changes apply to the captions immediately and are remembered.
- **Multiple fonts**: Netflix Sans, Roboto, Inter, Open Sans, Poppins, Montserrat, Cinzel, Roboto Mono and Comic Neue (bundled, SIL OFL), plus **Custom Font** to load your own .ttf / .otf file.

### Removed
- The built-in sample video. When no streaming source is set up the player now says so and offers **Add my source**.

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
