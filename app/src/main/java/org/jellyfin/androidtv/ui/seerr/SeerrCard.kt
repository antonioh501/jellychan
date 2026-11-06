package org.jellyfin.androidtv.ui.seerr

import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.seerr.SeerrAvailability
import org.jellyfin.androidtv.data.seerr.SeerrMedia
import org.jellyfin.androidtv.ui.base.Text

val SeerrCardWidth = 96.dp

private val CardShape = RoundedCornerShape(8.dp)
private const val POSTER_ASPECT_RATIO = 2f / 3f

// The same feedback as the cards of the library: a zoom on focus, a small shrink while OK is held
private const val FOCUSED_SCALE = 1.06f
private const val PRESSED_SCALE = 0.95f
private const val SHRINK_DURATION_MS = 80
private const val RELEASE_DURATION_MS = 120

@StringRes
fun SeerrAvailability.label(): Int? = when (this) {
	SeerrAvailability.UNKNOWN -> null
	SeerrAvailability.PENDING -> R.string.jc_seerr_availability_pending
	SeerrAvailability.PROCESSING -> R.string.jc_seerr_availability_processing
	SeerrAvailability.PARTIAL -> R.string.jc_seerr_availability_partial
	SeerrAvailability.AVAILABLE -> R.string.jc_seerr_availability_available
}

/** The label for places with room to spell it out. */
@StringRes
fun SeerrAvailability.longLabel(): Int? = when (this) {
	SeerrAvailability.PARTIAL -> R.string.jc_seerr_availability_partial_long
	else -> label()
}

private val SeerrAvailability.color
	get() = when (this) {
		SeerrAvailability.AVAILABLE -> Color(0xFF16A34A)
		SeerrAvailability.PARTIAL -> Color(0xFF0D9488)
		SeerrAvailability.PENDING -> Color(0xFFB45309)
		else -> Color(0xFF5D55E7)
	}

@Composable
fun SeerrStatusBadge(
	availability: SeerrAvailability,
	modifier: Modifier = Modifier,
	long: Boolean = false,
	fontSize: Int = 8,
) {
	val label = (if (long) availability.longLabel() else availability.label()) ?: return

	Text(
		text = stringResource(label),
		color = Color.White,
		fontSize = fontSize.sp,
		fontWeight = FontWeight.SemiBold,
		maxLines = 1,
		modifier = modifier
			.background(availability.color, RoundedCornerShape(percent = 50))
			.padding(horizontal = (fontSize * 3 / 4).dp, vertical = (fontSize / 4).dp),
	)
}

/**
 * The look of a card without any interaction, so it can be used inside a leanback row too.
 */
@Composable
fun SeerrCardContent(
	media: SeerrMedia,
	focused: Boolean,
	modifier: Modifier = Modifier,
	width: Dp = SeerrCardWidth,
	titleSize: TextUnit = 10.sp,
	centered: Boolean = false,
) {
	Column(modifier = modifier.width(width)) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(POSTER_ASPECT_RATIO)
				.clip(CardShape)
				.background(Color(0xFF1D2635))
				.border(2.dp, if (focused) Color.White else Color.Transparent, CardShape),
		) {
			if (media.posterUrl != null) {
				Image(
					painter = rememberAsyncImagePainter(media.posterUrl),
					contentDescription = null,
					contentScale = ContentScale.Crop,
					modifier = Modifier.fillMaxSize(),
				)
			} else {
				Text(
					text = media.title,
					fontSize = 11.sp,
					textAlign = TextAlign.Center,
					modifier = Modifier
						.align(Alignment.Center)
						.padding(8.dp),
				)
			}

			SeerrStatusBadge(
				availability = media.availability,
				modifier = Modifier
					.align(Alignment.TopEnd)
					.padding(4.dp),
			)
		}

		Spacer(Modifier.height(4.dp))

		Text(
			text = media.title,
			fontSize = titleSize,
			color = if (focused) Color.White else Color(0xFF9CA3AF),
			textAlign = if (centered) TextAlign.Center else TextAlign.Start,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.fillMaxWidth(),
		)
	}
}

@Composable
fun SeerrCard(
	media: SeerrMedia,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	onFocused: () -> Unit = {},
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val pressed by interactionSource.collectIsPressedAsState()
	val focusScale by animateFloatAsState(if (focused) FOCUSED_SCALE else 1f, label = "SeerrCardFocus")
	val pressScale by animateFloatAsState(
		targetValue = if (pressed) PRESSED_SCALE else 1f,
		animationSpec = tween(if (pressed) SHRINK_DURATION_MS else RELEASE_DURATION_MS, easing = LinearOutSlowInEasing),
		label = "SeerrCardPress",
	)

	LaunchedEffect(focused) {
		if (focused) onFocused()
	}

	// The focus zoom is on the card and the shrink on its content, so the two never fight
	Box(
		modifier = modifier
			.scale(focusScale)
			.clickable(
				interactionSource = interactionSource,
				indication = null,
				onClick = onClick,
			),
	) {
		SeerrCardContent(
			media = media,
			focused = focused,
			modifier = Modifier.scale(pressScale),
		)
	}
}
