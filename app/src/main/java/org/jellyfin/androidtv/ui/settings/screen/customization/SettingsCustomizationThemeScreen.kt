package org.jellyfin.androidtv.ui.settings.screen.customization

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.AppTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.RadioButton
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.jellyfin.androidtv.util.applyTheme
import org.koin.compose.koinInject

@Composable
fun SettingsCustomizationThemeScreen() {
	val router = LocalRouter.current
	val activity = LocalActivity.current
	val userPreferences = koinInject<UserPreferences>()
	var appTheme by rememberPreference(userPreferences, UserPreferences.appTheme)

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_customization).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_app_theme)) },
			)
		}

		items(AppTheme.entries) { entry ->
			ListButton(
				headingContent = { Text(stringResource(entry.nameRes)) },
				trailingContent = { RadioButton(checked = appTheme == entry) },
				onClick = {
					appTheme = entry
					// JellyChan: the settings are an overlay on the running activity, so apply the theme now
					// (it would otherwise wait for the next resume); this recreates the activity once
					userPreferences[UserPreferences.appTheme] = entry
					(activity as? FragmentActivity)?.applyTheme() ?: router.back()
				},
				modifier = Modifier
					.focusKey("app_theme_${entry.name}", initialFocus = appTheme == entry)
			)
		}
	}
}
