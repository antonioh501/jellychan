package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Height of [MainToolbar]; screens that show the overlay place their content starting below it. */
val MainToolbarHeight = 95.dp

private val VeilFadeHeight = 35.dp

// Same shape as the gradient at the top of the home carousel: dark at the very top, fading out downwards
private val VeilBrush = Brush.verticalGradient(
	0.0f to Color(0xF2111827),
	0.41f to Color(0xCC111827),
	1.0f to Color(0x00111827),
)

/**
 * JellyChan: the toolbar drawn on top of everything. A dark navy gradient covers both the background and the
 * content scrolling underneath it, fading out downwards. The gradient only shows once the screen has been
 * scrolled down ([showVeil]).
 */
@Composable
fun MainToolbarOverlay(
	activeButton: MainToolbarActiveButton = MainToolbarActiveButton.None,
	showVeil: Boolean = true,
) {
	val veilAlpha by animateFloatAsState(if (showVeil) 1f else 0f, tween(250), label = "ToolbarVeil")

	Box(modifier = Modifier.fillMaxWidth()) {
		Box(
			modifier = Modifier
				.matchParentSize()
				.graphicsLayer { alpha = veilAlpha }
				.background(VeilBrush)
		)
		Column(modifier = Modifier.fillMaxWidth()) {
			MainToolbar(activeButton)
			Box(modifier = Modifier.height(VeilFadeHeight))
		}
	}
}
