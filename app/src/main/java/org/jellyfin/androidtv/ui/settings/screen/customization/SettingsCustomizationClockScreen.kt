package org.jellyfin.androidtv.ui.settings.screen.customization

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.ClockBehavior
import org.jellyfin.androidtv.preference.constant.ClockFormat
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.RadioButton
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

@Composable
fun SettingsCustomizationClockScreen() {
	val userPreferences = koinInject<UserPreferences>()
	var clockBehavior by rememberPreference(userPreferences, UserPreferences.clockBehavior)
	var clockFormat by rememberPreference(userPreferences, UserPreferences.clockFormat)

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_customization).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_clock_display)) },
			)
		}

		items(ClockBehavior.entries) { entry ->
			ListButton(
				headingContent = { Text(stringResource(entry.nameRes)) },
				trailingContent = { RadioButton(checked = clockBehavior == entry) },
				onClick = { clockBehavior = entry },
				modifier = Modifier
					.focusKey("clock_behavior_${entry.name}", initialFocus = clockBehavior == entry)
			)
		}

		// JellyChan: 12/24 hours, independent of the interface language
		item {
			ListSection(
				headingContent = { Text(stringResource(R.string.jc_pref_clock_format)) },
			)
		}

		items(ClockFormat.entries) { entry ->
			ListButton(
				headingContent = { Text(stringResource(entry.nameRes)) },
				trailingContent = { RadioButton(checked = clockFormat == entry) },
				onClick = { clockFormat = entry },
				modifier = Modifier
					.focusKey("clock_format_${entry.name}")
			)
		}
	}
}
