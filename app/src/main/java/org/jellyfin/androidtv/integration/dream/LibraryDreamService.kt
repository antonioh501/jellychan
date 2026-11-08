package org.jellyfin.androidtv.integration.dream

import android.content.Context
import android.service.dreams.DreamService
import org.jellyfin.androidtv.integration.dream.composable.DreamHost
import org.jellyfin.androidtv.util.AppLanguage

/**
 * An Android [DreamService] (screensaver) that shows TV series and movies from all libraries.
 * Use `adb shell am start -n "com.android.systemui/.Somnambulator"` to start after changing the
 * default screensaver in the device settings.
 */
class LibraryDreamService : DreamServiceCompat() {
	// JellyChan: interface language chosen in the app
	override fun attachBaseContext(newBase: Context) {
		super.attachBaseContext(AppLanguage.wrap(newBase))
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()

		isInteractive = false
		isFullscreen = true

		setContent {
			DreamHost()
		}
	}
}
