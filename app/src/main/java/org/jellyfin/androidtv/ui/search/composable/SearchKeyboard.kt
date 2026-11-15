package org.jellyfin.androidtv.ui.search.composable

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.ButtonBase
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults

private const val KEYBOARD_COLUMNS = 6
private val KeySize = 42.dp
private val KeySpacing = 5.dp
private val KeyShape = RoundedCornerShape(10.dp)

/** Italian-friendly layout, six keys per row: letters, accented vowels and digits. */
private val keyboardRows = "abcdefghijklmnopqrstuvwxyzàèéìòù1234567890"
	.chunked(KEYBOARD_COLUMNS)

/**
 * JellyChan: on-screen search keyboard drawn by the app instead of the system IME. Fire OS opens a fullscreen keyboard for text
 * fields that hides the results; this grid keeps the results visible and updates them while typing. Physical keyboards (and
 * `adb shell input text`) keep working through [onPreviewKeyEvent] (before the focused key can treat SPACE as a click).
 */
@Composable
fun SearchKeyboard(
	query: String,
	onType: (text: String) -> Unit,
	onBackspace: () -> Unit,
	onClear: () -> Unit,
	modifier: Modifier = Modifier,
	firstKeyFocusRequester: FocusRequester = FocusRequester.Default,
	voiceInput: (@Composable () -> Unit)? = null,
) {
	Column(
		verticalArrangement = Arrangement.spacedBy(KeySpacing),
		modifier = modifier
			.focusRestorer(firstKeyFocusRequester)
			.focusGroup()
			.onPreviewKeyEvent { event ->
				if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
				val nativeEvent = event.nativeKeyEvent
				when {
					nativeEvent.keyCode == AndroidKeyEvent.KEYCODE_DEL -> {
						onBackspace()
						true
					}

					nativeEvent.unicodeChar != 0 && !Character.isISOControl(nativeEvent.unicodeChar) -> {
						onType(nativeEvent.unicodeChar.toChar().toString())
						true
					}

					else -> false
				}
			},
	) {
		SearchQueryText(query)

		keyboardRows.forEachIndexed { rowIndex, row ->
			Row(horizontalArrangement = Arrangement.spacedBy(KeySpacing)) {
				row.forEachIndexed { columnIndex, char ->
					val keyModifier = if (rowIndex == 0 && columnIndex == 0) Modifier.focusRequester(firstKeyFocusRequester) else Modifier
					KeyboardKey(
						onClick = { onType(char.toString()) },
						modifier = keyModifier,
					) {
						Text(text = char.toString(), fontSize = 20.sp)
					}
				}
			}
		}

		Row(horizontalArrangement = Arrangement.spacedBy(KeySpacing)) {
			KeyboardKey(
				onClick = { onType(" ") },
				width = keyWidth(2),
			) {
				Icon(ImageVector.vectorResource(R.drawable.jc_ic_space), stringResource(R.string.jc_search_space), Modifier.size(26.dp))
			}
			KeyboardKey(
				onClick = onBackspace,
				onLongClick = onClear,
			) {
				Icon(
					ImageVector.vectorResource(R.drawable.jc_ic_backspace),
					stringResource(R.string.jc_search_backspace),
					Modifier.size(24.dp)
				)
			}
			KeyboardKey(
				onClick = onClear,
				width = keyWidth(2),
				containerColor = JellyfinTheme.colorScheme.button,
			) {
				Text(text = stringResource(R.string.jc_search_clear), fontSize = 16.sp)
			}
			voiceInput?.let { Box(Modifier.size(KeySize), contentAlignment = Alignment.Center) { it() } }
		}
	}
}

@Composable
private fun SearchQueryText(query: String) {
	Box(
		contentAlignment = Alignment.CenterStart,
		modifier = Modifier
			.width(keyWidth(KEYBOARD_COLUMNS))
			.height(48.dp)
			.padding(start = 8.dp, bottom = 8.dp),
	) {
		if (query.isEmpty()) {
			Text(
				text = stringResource(R.string.jc_search_placeholder),
				color = JellyfinTheme.colorScheme.onButtonDisabled,
				fontSize = 18.sp,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		} else {
			// Show the end of long queries, like a text field would
			Text(
				text = query,
				color = JellyfinTheme.colorScheme.onBackground,
				fontSize = 30.sp,
				fontWeight = FontWeight.SemiBold,
				maxLines = 1,
				overflow = TextOverflow.StartEllipsis,
				modifier = Modifier.fillMaxWidth(),
			)
		}
	}
}

private fun keyWidth(keys: Int): Dp = KeySize * keys + KeySpacing * (keys - 1)

@Composable
private fun KeyboardKey(
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	onLongClick: (() -> Unit)? = null,
	width: Dp = KeySize,
	containerColor: Color = Color.Transparent,
	content: @Composable () -> Unit,
) {
	ButtonBase(
		onClick = onClick,
		onLongClick = onLongClick,
		shape = KeyShape,
		colors = ButtonDefaults.colors(
			containerColor = containerColor,
			contentColor = JellyfinTheme.colorScheme.onBackground,
		),
		modifier = modifier.size(width = width, height = KeySize),
	) {
		Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) { content() }
	}
}
