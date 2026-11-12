package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.ClockBehavior
import org.koin.compose.koinInject
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.Dp
import org.jellyfin.androidtv.ui.composable.rememberCurrentTime

private val overscanHorizontal = 48.dp
private val overscanVertical = 27.dp

@Composable
fun Logo(modifier: Modifier = Modifier) {
	Image(
		painter = painterResource(R.drawable.app_logo),
		contentDescription = stringResource(R.string.app_name),
		modifier = modifier,
	)
}

/**
 * JellyChan: [raise] moves the toolbar content up and takes the same amount off the bottom too, so the toolbar is a
 * thinner band with its content still vertically centered.
 */
@Composable
fun Toolbar(
	modifier: Modifier = Modifier,
	start: @Composable () -> Unit = { Logo() },
	center: @Composable () -> Unit = {},
	end: @Composable () -> Unit = { ToolbarClock() },
	raise: Dp = 0.dp,
) {
	ToolbarLayout(
		modifier = modifier
			.height(95.dp - raise * 2)
			.padding(horizontal = overscanHorizontal, vertical = overscanVertical - raise),
		start = start,
		center = center,
		end = end,
	)
}

@Composable
fun ToolbarClock(
	modifier: Modifier = Modifier,
) {
	// The toolbar is shown in menus only, so "during playback" and "never" hide the clock here
	val clockBehavior = koinInject<UserPreferences>()[UserPreferences.clockBehavior]
	if (clockBehavior != ClockBehavior.ALWAYS && clockBehavior != ClockBehavior.IN_MENUS) return

	val currentTime by rememberCurrentTime()
	Text(
		text = currentTime,
		fontSize = 15.sp, // JellyChan
		color = Color.White,
		modifier = modifier,
	)
}

@Composable
fun ToolbarLayout(
	modifier: Modifier = Modifier,
	start: @Composable () -> Unit = {},
	center: @Composable () -> Unit = {},
	end: @Composable () -> Unit = {},
) = SubcomposeLayout(modifier = modifier) { constraints ->
	val sideConstraints = constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth / 3, minHeight = 0)
	val startPlaceables = subcompose("start", content = start).map { it.measure(sideConstraints) }
	val endPlaceables = subcompose("end", content = end).map { it.measure(sideConstraints) }

	val sideWidth = maxOf(
		startPlaceables.maxOfOrNull { it.width } ?: 0,
		endPlaceables.maxOfOrNull { it.width } ?: 0,
	)

	val centerWidth = (constraints.maxWidth - 2 * sideWidth).coerceAtLeast(0)
	val centerPlaceables = subcompose("center", content = center)
		.map { it.measure(constraints.copy(minWidth = 0, maxWidth = centerWidth)) }

	val height = listOf(
		startPlaceables.maxOfOrNull { it.height } ?: 0,
		centerPlaceables.maxOfOrNull { it.height } ?: 0,
		endPlaceables.maxOfOrNull { it.height } ?: 0
	).maxOrNull() ?: 0

	layout(constraints.maxWidth, height) {
		startPlaceables.forEach { it.placeRelative(0, (height - it.height) / 2) }
		centerPlaceables.forEach { it.place((constraints.maxWidth - it.width) / 2, (height - it.height) / 2) }
		endPlaceables.forEach { it.placeRelative(constraints.maxWidth - it.width, (height - it.height) / 2) }
	}
}

@Composable
fun ToolbarButtons(
	modifier: Modifier = Modifier,
	content: @Composable RowScope.() -> Unit,
) {
	Row(
		modifier = modifier
			.focusRestorer()
			.focusGroup(),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		JellyfinTheme(
			colorScheme = JellyfinTheme.colorScheme.copy(
				button = Color.Transparent
			)
		) {
			content()
		}
	}
}
