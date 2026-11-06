# Changelog

All notable changes to JellyChan are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project follows
[Semantic Versioning](https://semver.org).

## [Unreleased]

### Added
- Movie and episode pages: external ids, director, writer, studio and genres, plus video, audio and subtitle
  details below the header. Audio and subtitle tracks can be picked there before starting playback.
- Settings: connect to a Seerr server with the server account or a Seerr account. Only the session is kept on the
  device, never the password.
- Discover tab, shown once Seerr is connected: trending, popular and upcoming movies and series with their
  availability, plus a search. Titles already in the library open their item page.

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

[Unreleased]: https://github.com/antonioh501/jellychan/compare/v0.1.0-alpha.2...HEAD
[0.1.0-alpha.2]: https://github.com/antonioh501/jellychan/compare/v0.1.0-alpha.1...v0.1.0-alpha.2
[0.1.0-alpha.1]: https://github.com/antonioh501/jellychan/releases/tag/v0.1.0-alpha.1
