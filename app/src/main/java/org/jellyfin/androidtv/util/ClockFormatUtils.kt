package org.jellyfin.androidtv.util

import android.content.Context
import android.provider.Settings
import androidx.preference.PreferenceManager
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.ClockFormat

/**
 * JellyChan: decides between a 12 and 24 hour clock from the app setting.
 * Read straight from the shared preferences so it also works from plain Java/Views and the screensaver.
 */
object ClockFormatUtils {
	@JvmStatic
	fun is24Hour(context: Context): Boolean {
		val key = UserPreferences.clockFormat.key
		val stored = PreferenceManager.getDefaultSharedPreferences(context).getString(key, null)
		val setting = ClockFormat.entries.firstOrNull { it.name == stored } ?: ClockFormat.AUTO

		return when (setting) {
			ClockFormat.HOURS_12 -> false
			ClockFormat.HOURS_24 -> true
			// The device value is "12" or "24", or missing when it follows the locale: use 24 hours then
			ClockFormat.AUTO -> Settings.System.getString(context.contentResolver, Settings.System.TIME_12_24) != "12"
		}
	}

	/** Short clock pattern (the same ones the toolbar uses). */
	@JvmStatic
	fun pattern(context: Context): String = if (is24Hour(context)) "k:mm" else "h:mm"
}
