package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

/**
 * JellyChan: the theme can't be chosen for now, the app always uses [AppTheme.DARK]. Set to true to bring the
 * choice (settings entry and theme application) back.
 */
const val APP_THEME_SELECTION_ENABLED = false

enum class AppTheme(
	override val nameRes: Int,
) : PreferenceEnum {
	/**
	 * The default dark theme
	 */
	DARK(R.string.pref_theme_dark),

	/**
	 * A theme with a more muted accent color, inspired by CTalvio's Monochromic CSS theme for Jellyfin Web
	 */
	MUTED_PURPLE(R.string.pref_theme_muted_purple),
}
