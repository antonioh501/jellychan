package org.jellyfin.androidtv.util

import android.content.Context
import android.os.Build
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Suppress("DEPRECATION")
val Context.locale: Locale
	get() = when {
		Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> resources.configuration.getLocales().get(0)
		else -> resources.configuration.locale
	}

@JvmOverloads
fun Context.getDateFormatter(
	style: FormatStyle = FormatStyle.SHORT
): DateTimeFormatter = DateTimeFormatter
	.ofLocalizedDateTime(style)
	.withLocale(locale)

@JvmOverloads
fun Context.getTimeFormatter(
	style: FormatStyle = FormatStyle.SHORT
): DateTimeFormatter = when {
	// JellyChan: 12/24 hours comes from the app setting, not from the interface language
	style == FormatStyle.SHORT -> DateTimeFormatter.ofPattern(
		if (ClockFormatUtils.is24Hour(this)) "HH:mm" else "h:mm a",
		locale,
	)
	else -> DateTimeFormatter.ofLocalizedTime(style).withLocale(locale)
}
