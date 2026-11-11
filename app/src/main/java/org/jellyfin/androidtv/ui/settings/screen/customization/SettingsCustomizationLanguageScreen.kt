package org.jellyfin.androidtv.ui.settings.screen.customization

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.RadioButton
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.jellyfin.androidtv.util.AppLanguage

/**
 * JellyChan: interface language independent of the device, applied by restarting the app.
 */
@Composable
fun SettingsCustomizationLanguageScreen() {
	val context = LocalContext.current
	val activity = LocalActivity.current
	val current = AppLanguage.get(context)

	// "" follows the device language
	val options = listOf("") + AppLanguage.supported

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_customization).uppercase()) },
				headingContent = { Text(stringResource(R.string.jc_pref_app_language)) },
			)
		}

		items(options) { tag ->
			ListButton(
				headingContent = {
					Text(if (tag.isEmpty()) stringResource(R.string.jc_pref_app_language_system) else AppLanguage.displayName(tag))
				},
				trailingContent = { RadioButton(checked = current == tag) },
				onClick = {
					if (tag != current && activity != null) {
						AppLanguage.set(context, tag)
						AppLanguage.restart(activity)
					}
				},
				modifier = Modifier
					.focusKey("app_language_${tag.ifEmpty { "system" }}", initialFocus = current == tag)
			)
		}
	}
}
