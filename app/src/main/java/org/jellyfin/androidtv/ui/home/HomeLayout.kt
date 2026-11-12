package org.jellyfin.androidtv.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarHeight

/**
 * JellyChan home layout. The home is split in pages: the carousel fills the first one, the rows below are grouped
 * in pages of their own (My media, Next up and Continue watching; then Recently added) that replace each other
 * as a whole while moving up and down.
 */

/** Where the home rows start, right under the toolbar band. */
val HomeToolbarHeight = MainToolbarHeight

/** Height (dp) of the cards in the home pages below the carousel, so three rows fit on one page. */
const val HOME_TILE_HEIGHT = 72

/** Height (dp) of the Next up and Continue watching cards, on the page where My media is a row of pills. */
const val HOME_TILE_HEIGHT_LARGE = 98

/** Arrows shown on the current home page: up when a page sits above it, down when one sits below. */
data class HomePageArrows(val up: Boolean = false, val down: Boolean = true)

/** Arrow inviting to scroll to the next (or, pointing up, the previous) home page. */
@Composable
fun HomeScrollHint(visible: Boolean, modifier: Modifier = Modifier, up: Boolean = false) {
	val alpha by animateFloatAsState(if (visible) 0.85f else 0f, tween(250), label = "HomeScrollHintAlpha")
	val bounce by rememberInfiniteTransition(label = "HomeScrollHint").animateFloat(
		initialValue = 0f,
		targetValue = if (up) -6f else 6f,
		animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
		label = "HomeScrollHintBounce",
	)

	// The layer properties are read here rather than in a graphicsLayer block: back from another screen the block may no
	// longer be invalidated, leaving the arrows stuck on their old state
	val translation = with(LocalDensity.current) { bounce.dp.toPx() }
	Image(
		painter = painterResource(R.drawable.jc_ic_chevron_down),
		contentDescription = null,
		colorFilter = ColorFilter.tint(androidx.compose.ui.graphics.Color.White),
		modifier = modifier
			.size(30.dp)
			.graphicsLayer(
				alpha = alpha,
				rotationZ = if (up) 180f else 0f,
				translationY = translation,
			),
	)
}
