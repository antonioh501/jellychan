package org.jellyfin.androidtv.ui.home

import androidx.compose.ui.unit.dp

/**
 * JellyChan home layout. A narrow icon rail sits on the left; the carousel fills the screen at first, with the first
 * row peeking in at the bottom. Further down the rows scroll continuously: the selected row always sits on the same
 * line, the rows above it are hidden and the space above shows the selected card (backdrop, logo, info).
 */

/** Width of the icon rail on the left of the home. */
val HomeRailWidth = 80.dp

/** Where the home content (carousel info, row titles and cards) starts, right of the rail. */
val HomeContentStart = 88.dp

/** How much of the first row shows under the carousel when the home opens. */
val HomeSpotlightPeek = 104.dp

/**
 * Width of the zone left of the backdrop where the info about the selected card (logo, details, plot) is drawn. The
 * backdrop starts at 230dp, but its vignette keeps the image transparent next to the zone.
 */
val HomeInfoWidth = 316.dp

/** Distance from the top of the screen to the selected row (its title, cards right below). */
val HomeFocusLine = 336.dp

/** Height (dp) of the 16:9 cards in the home rows. */
const val HOME_TILE_HEIGHT = 120

/** Size (dp) of the round genre cards. */
const val HOME_GENRE_SIZE = 92
