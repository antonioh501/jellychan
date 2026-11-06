package org.jellyfin.androidtv.ui.preference.screen

import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.checkbox
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen
import org.jellyfin.preference.store.PreferenceStore
import org.koin.android.ext.android.inject

/**
 * JellyChan: the home sections are plain on/off switches. The order is fixed by the home screen,
 * the stored slots (shared with the other Jellyfin clients) are filled and emptied behind the scenes.
 */
class HomePreferencesScreen : OptionsFragment() {
	private val userSettingPreferences: UserSettingPreferences by inject()

	override val stores: Array<PreferenceStore<*, *>>
		get() = arrayOf(userSettingPreferences)

	private fun isEnabled(types: Set<HomeSectionType>) =
		userSettingPreferences.activeHomesections.any { it in types }

	private fun setEnabled(types: Set<HomeSectionType>, primary: HomeSectionType, enabled: Boolean) {
		val slots = userSettingPreferences.homesections
		if (enabled) {
			if (isEnabled(types)) return
			val free = slots.firstOrNull { userSettingPreferences[it] == HomeSectionType.NONE } ?: slots.last()
			userSettingPreferences[free] = primary
		} else {
			slots.filter { userSettingPreferences[it] in types }
				.forEach { userSettingPreferences[it] = HomeSectionType.NONE }
		}
	}

	override val screen by optionsScreen {
		setTitle(R.string.home_prefs)

		category {
			setTitle(R.string.home_sections)

			listOf(
				R.string.home_section_next_up to HomeSectionType.NEXT_UP,
				R.string.home_section_resume to HomeSectionType.RESUME,
				R.string.home_section_resume_audio to HomeSectionType.RESUME_AUDIO,
				R.string.home_section_library to HomeSectionType.LIBRARY_TILES_SMALL,
				R.string.home_section_latest_media to HomeSectionType.LATEST_MEDIA,
				R.string.home_section_livetv to HomeSectionType.LIVE_TV,
				R.string.home_section_active_recordings to HomeSectionType.ACTIVE_RECORDINGS,
			).forEach { (title, type) ->
				// "My media" has a small variant, both count as the same switch
				val types = if (type == HomeSectionType.LIBRARY_TILES_SMALL) {
					setOf(type, HomeSectionType.LIBRARY_BUTTONS)
				} else {
					setOf(type)
				}

				checkbox {
					setTitle(title)
					bind {
						get { isEnabled(types) }
						set { value -> setEnabled(types, type, value) }
						default { false }
					}
				}
			}
		}
	}
}
