package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum

/**
 * JellyChan: 12/24 hour clock, independent of the interface language.
 */
enum class ClockFormat(
	override val nameRes: Int,
) : PreferenceEnum {
	/**
	 * Follow the device setting, 24 hours when the device has none.
	 */
	AUTO(R.string.jc_pref_clock_format_auto),

	/**
	 * 12 hours (am/pm).
	 */
	HOURS_12(R.string.jc_pref_clock_format_12),

	/**
	 * 24 hours.
	 */
	HOURS_24(R.string.jc_pref_clock_format_24),
}
