package org.jellyfin.androidtv.ui.itemdetail

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarOverlay

/**
 * JellyChan: shows the main toolbar on top of the item detail screens.
 */
fun FullDetailsFragment.setupToolbar(view: ComposeView) {
	view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
	view.setContent {
		JellyfinTheme {
			val scrolledDown by jcScrolledDown.collectAsState()
			MainToolbarOverlay(showVeil = scrolledDown)
		}
	}
}
