package org.jellyfin.androidtv.ui.base

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.APP_THEME_SELECTION_ENABLED
import org.jellyfin.androidtv.preference.constant.AppTheme
import org.koin.compose.koinInject

/**
 * JellyChan: the color scheme follows the app theme chosen in the settings (the activity is recreated when it changes).
 */
@Composable
private fun rememberAppColorScheme(): ColorScheme {
	val userPreferences = koinInject<UserPreferences>()
	val theme = remember {
		if (APP_THEME_SELECTION_ENABLED) userPreferences[UserPreferences.appTheme] else AppTheme.DARK
	}
	return remember(theme) { colorScheme(theme) }
}

@Composable
fun JellyfinTheme(
	colorScheme: ColorScheme = rememberAppColorScheme(),
	shapes: Shapes = JellyfinTheme.shapes,
	typography: Typography = JellyfinTheme.typography,
	content: @Composable () -> Unit
) {
	CompositionLocalProvider(
		LocalColorScheme provides colorScheme,
		LocalShapes provides shapes,
		LocalTypography provides typography,
		LocalTextStyle provides LocalTextStyle.current.merge(typography.default),
		// Disable overscroll
		LocalOverscrollFactory provides null,
	) {
		content()
	}
}

object JellyfinTheme {
	val colorScheme: ColorScheme
		@Composable @ReadOnlyComposable get() = LocalColorScheme.current

	val typography: Typography
		@Composable @ReadOnlyComposable get() = LocalTypography.current

	val shapes: Shapes
		@Composable @ReadOnlyComposable get() = LocalShapes.current
}
