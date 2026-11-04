package org.jellyfin.androidtv.ui.base

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jellyfin.androidtv.R

/** JellyChan uses the Inter font family throughout the interface. */
val InterFontFamily = FontFamily(
	Font(R.font.inter_regular, FontWeight.Normal),
	Font(R.font.inter_medium, FontWeight.Medium),
	Font(R.font.inter_semibold, FontWeight.SemiBold),
	Font(R.font.inter_bold, FontWeight.Bold),
)

object TypographyDefaults {
	val Default: TextStyle = TextStyle.Default.copy(
		fontFamily = InterFontFamily,
		color = Color(0xFFD1D5DB),
	)
}

@Immutable
data class Typography(
	val default: TextStyle = TypographyDefaults.Default,
)

val LocalTypography = staticCompositionLocalOf { Typography() }
