package org.jellyfin.androidtv.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.preference.PreferenceManager
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.startup.StartupActivity
import java.util.Locale
import kotlin.system.exitProcess

/**
 * JellyChan: interface language chosen in the app, independent of the device language.
 *
 * The activities are plain FragmentActivity, so the AppCompat per-app locales don't work before Android 13:
 * every activity, service and the application wrap their base context with [wrap] instead.
 */
object AppLanguage {
	/** Languages offered in the settings (BCP 47 tags); the app is fully translated only in these. */
	val supported = listOf("en", "it", "fr", "es", "de")

	/** The chosen language tag, or an empty string to follow the device. */
	fun get(context: Context): String = PreferenceManager.getDefaultSharedPreferences(context)
		.getString(UserPreferences.appLanguage.key, null)
		.orEmpty()
		.takeIf { it in supported }
		.orEmpty()

	/** Saves the language synchronously, so it survives the [restart] that follows. */
	fun set(context: Context, tag: String) {
		PreferenceManager.getDefaultSharedPreferences(context).edit()
			.putString(UserPreferences.appLanguage.key, tag.takeIf { it in supported }.orEmpty())
			.commit()
	}

	/** The language name written in the language itself, e.g. "Italiano". */
	fun displayName(tag: String): String {
		val locale = Locale.forLanguageTag(tag)
		return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
	}

	/** Returns [base] with the chosen language applied, or [base] itself when following the device. */
	fun wrap(base: Context): Context {
		val tag = get(base)
		if (tag.isEmpty()) return base

		val locale = Locale.forLanguageTag(tag)
		Locale.setDefault(locale)

		val configuration = Configuration(base.resources.configuration)
		configuration.setLocale(locale)
		return base.createConfigurationContext(configuration)
	}

	/** Keeps [Locale.getDefault] on the chosen language after the system resets it on a configuration change. */
	fun applyDefault(context: Context) {
		val tag = get(context)
		if (tag.isNotEmpty()) Locale.setDefault(Locale.forLanguageTag(tag))
	}

	/** Restarts the app so every screen and cached text picks up the new language. */
	fun restart(activity: Activity) {
		val intent = Intent(activity, StartupActivity::class.java)
			.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
		activity.startActivity(intent)
		activity.finishAffinity()
		exitProcess(0)
	}
}
