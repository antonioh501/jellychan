package org.jellyfin.androidtv

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import org.jellyfin.androidtv.telemetry.TelemetryService
import org.jellyfin.androidtv.util.AppLanguage

class JellyfinApplication : Application() {
	override fun attachBaseContext(base: Context?) {
		super.attachBaseContext(base?.let(AppLanguage::wrap))
		TelemetryService.init(this)
	}

	override fun onConfigurationChanged(newConfig: Configuration) {
		super.onConfigurationChanged(newConfig)

		// The system resets the default locale to the device one
		AppLanguage.applyDefault(this)
	}
}
