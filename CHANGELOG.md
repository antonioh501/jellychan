# Changelog

All notable changes to JellyChan are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project follows
[Semantic Versioning](https://semver.org).

## [Unreleased]

## [0.3.0-beta.1] - 2026-11-11

### Changed
- Rebased on upstream jellyfin-androidtv 0.20.0 with the JellyChan interface ported on top.
- New logo (line-art jellyfish with a pink-to-violet gradient) on the splash screen, toolbar, launcher icon and banner.
- The toolbar floats over the content: after scrolling down, a dark veil keeps the buttons readable over the
  rows and the background.
- Details screens: smaller episode thumbnail with more room from the toolbar, more compact information boxes
  under the synopsis, and the non-clickable IMDb/TheTVDB/TMDB chips are gone.
- The home carousel has a denser top gradient.
- The focused user name on the user selection screen is readable again.
- The splash screen is shown for at least 1.5 seconds and no longer flashes a stale background.
- The theme selection is hidden (only the default theme is available).

## [0.2.0-beta.1] - 2026-11-10

### Changed
- First beta. The app is the same as 0.1.0-alpha.5, built from the same code, and installs over any alpha.
- The repository is now a real GitHub fork of jellyfin-androidtv, based on upstream 0.19.x.

## [0.1.0-alpha.5] - 2026-11-09

### Added
- Settings, Personalization: "Language" chooses the interface language independently of the TV: device language,
  English, Italiano, Français, Español or Deutsch. The app restarts to apply it; Seerr content follows the chosen
  language too. The new JellyChan texts are translated into French, Spanish and German.
- Settings, Personalization: "Clock format" (automatic, 12-hour or 24-hour) for the clock and the "ends at" times.
  Automatic follows the TV setting, or 24 hours when the TV has none, whatever the interface language.
- Settings, Libraries: each library has its own view type again (poster, thumbnail or banner).
- Person pages: a "More on Seerr" row with the movies and series of Seerr the person appears in and that are
  not in the library yet.

## [0.1.0-alpha.4] - 2026-11-07

### Changed
- Settings, Personalization: removed options that had no effect or that broke the new design (emerald theme,
  backdrops switch, series thumbnails, stars/tomatoes choice, season premieres, smart screen, image type and grid
  direction, screensaver age-required switch, audio/subtitle remote shortcuts). Backdrops and series thumbnails are
  always on.
- Settings: "Show ratings" is a simple switch, and the home sections are plain on/off switches. The order of the
  home sections is fixed.

### Fixed
- Seerr: the title and poster on a title page are no longer cut off after moving between the buttons and the
  recommendations; the page now scrolls to clean positions.
- Seerr: the rows above the focused one no longer bob up and down on every left/right move in Discover.
- Settings: the clock setting now also applies to the toolbar of the home and library pages.
- Italian typos in the screensaver settings; shorter title for the watched indicators setting.

## [0.1.0-alpha.3] - 2026-11-06

### Added
- Seerr: "Le mie richieste" page, opened from the Discover tab, with the requests of the signed in user, their status
  and a way to cancel them.
- Movie and episode pages: external ids, director, writer, studio and genres, plus video, audio and subtitle
  details below the header. Audio and subtitle tracks can be picked there before starting playback.
- Settings: connect to a Seerr server with the server account or a Seerr account. Only the session is kept on the
  device, never the password.
- Discover tab, shown once Seerr is connected: trending, popular and upcoming movies and series with their
  availability, plus a search. Titles already in the library open their item page.
- Seerr page for titles that are not in the library, with the details, the status and a request button. A series can be
  requested by season; seasons that are already requested or available are marked.
- Seerr page: a status badge (requested, processing, partially available, available), the status of every request with
  who made it, and a button to cancel a request. A title that is already requested or present cannot be requested again.
- Suggestions from Seerr at the bottom of the Seerr page and of the movie and series pages of the library. Titles that
  the library already suggests are not repeated.

### Changed
- Discover cards shrink while OK is held, like the other cards of the app.
- Rows across the app use the compact spacing of the Discover page: smaller gap between cards, smaller row titles and
  less space between rows.

## [0.1.0-alpha.2] - 2026-11-05

### Changed
- Item pages for episodes, movies and series: title in the original language under the logo, single metadata
  line (date or years, runtime, rating badge, community and critic score, end time) without video quality and
  subtitle badges. The end time no longer depends on the clock setting.
- Item page actions: icon-only buttons with the label shown under the focused one, restart button on started items, green
  check on watched items, outlined heart. Go to series, previous episode, versions and delete moved to the menu.
- Thinner and shorter progress bar on cards.
- Pressing OK on a card shrinks it briefly instead of showing a ripple.
- Home carousel info line: only parental rating, community score and critic score.
- Series with a next up episode: Play all moved to the menu to avoid two Play icons.
- Home toolbar shows the full app logo instead of the server name.

### Fixed
- Square focus highlight around round cast & crew photos.
- The back arrow on library pages is now a working button.
- Focused row of popup menus no longer overflows the rounded menu shape.
- The back arrow in the player is now a working button: press up from the top controls to reach it.
- In library grids you can now move down to a shorter last row from any column.
- Logo in the toolbar no longer looks grainy.

## [0.1.0-alpha.1] - 2026-11-04

First public pre-release. Based on Jellyfin for Android TV 0.19.10.

### Added
- Home spotlight carousel: random movies and series with backdrop, clear logo, info and Play/Details
  buttons; auto-advances, plays a muted local trailer when the server has one; left/right switches item.
- Server logo and name in the home toolbar; Home and Search pills; selectable avatar to switch user.
- Library tabs (Library, Suggested, Favorites, Genres), shuffle, sort and unwatched filter, A–Z quick index.
- Person page with fixed portrait, biography, birth date and place; round cast & crew photos.
- Player: previous/next episode always available on episodes, favorite toggle, remaining time and end
  time, trickplay thumbnail on seek (enabled by default).
- JellyChan launcher icon and Android TV banner.

### Changed
- New visual design: navy palette, Inter typeface, rounded cards with thin focus outline and 105% zoom,
  pill buttons, more spacing between cards.
- Item pages: full backdrop with centered clear logo, metadata, actions and overview; compact episode
  header with thumbnail.
- Continue watching uses landscape artwork for movies and episodes; episode cards show series name and
  "S1:E2 - Title".
- Player controls on a single line with smaller rounded icons and a larger play button; subtitles and
  audio buttons always visible.
- Library grid: 8 columns, vertical scrolling by default.
- The app identifies itself to the server as "JellyChan".

### Removed
- Rating badges on cards.
- Profile picture in the player overlay.

[Unreleased]: https://github.com/antonioh501/jellychan/compare/v0.3.0-beta.1...HEAD
[0.3.0-beta.1]: https://github.com/antonioh501/jellychan/compare/v0.2.0-beta.1...v0.3.0-beta.1
[0.2.0-beta.1]: https://github.com/antonioh501/jellychan/compare/v0.1.0-alpha.5...v0.2.0-beta.1
[0.1.0-alpha.5]: https://github.com/antonioh501/jellychan/compare/v0.1.0-alpha.4...v0.1.0-alpha.5
[0.1.0-alpha.4]: https://github.com/antonioh501/jellychan/compare/v0.1.0-alpha.3...v0.1.0-alpha.4
[0.1.0-alpha.3]: https://github.com/antonioh501/jellychan/compare/v0.1.0-alpha.2...v0.1.0-alpha.3
[0.1.0-alpha.2]: https://github.com/antonioh501/jellychan/compare/v0.1.0-alpha.1...v0.1.0-alpha.2
[0.1.0-alpha.1]: https://github.com/antonioh501/jellychan/releases/tag/v0.1.0-alpha.1
