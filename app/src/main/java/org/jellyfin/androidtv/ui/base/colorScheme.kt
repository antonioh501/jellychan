package org.jellyfin.androidtv.ui.base

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// JellyChan palette (see lscambo13/JellyChan :root variables)
fun colorScheme(): ColorScheme = ColorScheme(
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
	recording = Color(0xB3D95454),
	onRecording = Color(0xFFFFFFFF),
	popover = Color(0xF21E2836), // --drawerColor
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

	val recording: Color,
	val onRecording: Color,

	val popover: Color,
)

val LocalColorScheme = staticCompositionLocalOf { colorScheme() }
