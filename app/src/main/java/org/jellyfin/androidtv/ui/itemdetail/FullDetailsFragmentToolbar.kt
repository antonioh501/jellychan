package org.jellyfin.androidtv.ui.itemdetail

import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.jellyfin.androidtv.ui.base.JellyfinTheme

/**
 * JellyChan: shows the main toolbar on top of the item detail screens.
 */
fun FullDetailsFragment.setupToolbar(view: ComposeView) {
	view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
	view.setContent {
		// JellyChan: the app rail replaces the top bar, nothing is drawn here anymore
		JellyfinTheme {}
	}
}
