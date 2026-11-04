# Changelog

All notable changes to JellyChan are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project follows
[Semantic Versioning](https://semver.org).

## [Unreleased]

### Changed
- Item pages for episodes, movies and series: title in the original language under the logo, single metadata
  line (date or years, runtime, rating badge, community and critic score, end time) without video quality and
  subtitle badges. The end time no longer depends on the clock setting.
- Item page actions: main Play/Resume pill, icon-only secondary actions, restart button on started items, green
  check on watched items, outlined heart. Go to series, previous episode, versions and delete moved to the menu.
- Thinner and shorter progress bar on cards.
- Pressing OK on a card shrinks it briefly instead of showing a ripple.

### Fixed
- Square focus highlight around round cast & crew photos.
- The back arrow on library pages is now a working button.

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

[Unreleased]: https://github.com/antonioh501/jellychan/compare/v0.1.0-alpha.1...HEAD
[0.1.0-alpha.1]: https://github.com/antonioh501/jellychan/releases/tag/v0.1.0-alpha.1
