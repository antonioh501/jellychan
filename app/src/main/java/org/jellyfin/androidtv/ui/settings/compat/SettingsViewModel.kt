package org.jellyfin.androidtv.ui.settings.compat

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel : ViewModel() {
	private val _visible = MutableStateFlow(false)
	val visible get() = _visible.asStateFlow()

	// JellyChan: route to open on top of the main settings screen (e.g. the Seerr connection page)
	private val _pendingRoute = MutableStateFlow<String?>(null)
	val pendingRoute get() = _pendingRoute.asStateFlow()

	fun show(route: String? = null) {
		_pendingRoute.value = route
		_visible.value = true
	}

	fun consumePendingRoute() {
		_pendingRoute.value = null
	}

	fun hide() {
		_visible.value = false
	}
}
