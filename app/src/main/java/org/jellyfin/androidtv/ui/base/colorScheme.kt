package org.jellyfin.androidtv.ui.base

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import org.jellyfin.androidtv.preference.constant.AppTheme
import org.jellyfin.design.Tokens

fun colorScheme(theme: AppTheme = AppTheme.DARK): ColorScheme = when (theme) {
	AppTheme.DARK -> darkColorScheme()
	AppTheme.MUTED_PURPLE -> mutedPurpleColorScheme()
}

/** JellyChan: the dark scheme with a muted purple tint on backgrounds, panels, selections and accents. */
private fun mutedPurpleColorScheme(): ColorScheme = darkColorScheme().copy(
	background = Color(0xFF1A1626),
	button = Color(0xCC261F38),
	buttonDisabled = Color(0x664A3F6B),
	buttonActive = Color(0xFF4A3F6B),
	input = Color(0xFF4A3F6B),
	rangeControlFill = Color(0xFF9D8CE0),
	badge = Color(0xFF8E7CC3),
	onBadge = Color(0xFFFFFFFF),
	listButtonFocused = Color(0xFF4A3F6B),
	surface = Color(0xF2261F38),
)

private fun darkColorScheme(): ColorScheme = ColorScheme(
	// JellyChan palette (see lscambo13/JellyChan :root variables)
	background = Color(0xFF111827), // --darkerGradientPoint
	onBackground = Color(0xFFD1D5DB), // --textColor
	button = Color(0xCC1E2836), // --headerColor
	onButton = Color(0xFFD1D5DB),
	buttonFocused = Color(0xFFE5E7EB), // light pill, like the "Play" button
	onButtonFocused = Color(0xFF111827),
	buttonDisabled = Color(0x66384252),
	onButtonDisabled = Color(0xFF6B7280),
	buttonActive = Color(0xFF384252), // --selectorBackgroundColor
	onButtonActive = Color(0xFFFFFFFF),
	input = Color(0xFF384252),
	onInput = Color(0xFFD1D5DB),
	inputFocused = Color(0xFFE5E7EB),
	onInputFocused = Color(0xFF111827),
	rangeControlBackground = Tokens.Color.colorBluegrey700,
	rangeControlFill = Tokens.Color.colorCyan500,
	rangeControlKnob = Tokens.Color.colorBluegrey100,
	seekbarBuffer = Tokens.Color.colorBluegrey300,
	recording = Color(0xB3D95454),
	onRecording = Color(0xFFFFFFFF),
	badge = Tokens.Color.colorCyan500,
	onBadge = Tokens.Color.colorBluegrey100,
	listHeader = Tokens.Color.colorGrey50,
	listOverline = Tokens.Color.colorGrey500,
	listHeadline = Tokens.Color.colorGrey25,
	listCaption = Tokens.Color.colorGrey200,
	listButton = Color.Transparent,
	listButtonFocused = Color(0xFF384252), // --selectorBackgroundColor
	surface = Color(0xF21E2836), // --drawerColor
	scrim = Tokens.Color.colorBlack.copy(alpha = 0.67f),
)

@Immutable
data class ColorScheme(
	val background: Color,
	val onBackground: Color,

	val button: Color,
	val onButton: Color,
	val buttonFocused: Color,
	val onButtonFocused: Color,
	val buttonDisabled: Color,
	val onButtonDisabled: Color,
	val buttonActive: Color,
	val onButtonActive: Color,

	val input: Color,
	val onInput: Color,
	val inputFocused: Color,
	val onInputFocused: Color,

	val rangeControlBackground: Color,
	val rangeControlFill: Color,
	val rangeControlKnob: Color,
	val seekbarBuffer: Color,

	val recording: Color,
	val onRecording: Color,

	val badge: Color,
	val onBadge: Color,

	val listHeader: Color,
	val listOverline: Color,
	val listHeadline: Color,
	val listCaption: Color,
	val listButton: Color,
	val listButtonFocused: Color,

	val surface: Color,
	val scrim: Color,
)

val LocalColorScheme = staticCompositionLocalOf { colorScheme() }
