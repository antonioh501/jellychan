package org.jellyfin.androidtv.ui.search.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults

/**
 * JellyChan: horizontal row of chips above the search results. With an empty query it lists the recent searches (with a
 * "clear history" chip), otherwise title completions taken from the current results.
 */
@Composable
fun SearchSuggestions(
	suggestions: List<String>,
	isHistory: Boolean,
	onSuggestionClick: (suggestion: String) -> Unit,
	onClearHistory: () -> Unit,
	modifier: Modifier = Modifier,
) {
	LazyRow(
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		contentPadding = PaddingValues(horizontal = 4.dp),
		modifier = modifier
			.height(40.dp)
			.focusRestorer(),
	) {
		items(suggestions, key = { it }) { suggestion ->
			SuggestionChip(onClick = { onSuggestionClick(suggestion) }) {
				if (isHistory) {
					Icon(ImageVector.vectorResource(R.drawable.ic_time), contentDescription = null, modifier = Modifier.size(16.dp))
					Spacer(Modifier.width(6.dp))
				}
				Text(text = suggestion, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
		}

		if (isHistory && suggestions.isNotEmpty()) {
			item(key = "clear-history") {
				SuggestionChip(onClick = onClearHistory) {
					Text(text = stringResource(R.string.jc_search_clear_history), fontSize = 15.sp)
				}
			}
		}
	}
}

@Composable
private fun SuggestionChip(
	onClick: () -> Unit,
	content: @Composable () -> Unit,
) = Button(
	onClick = onClick,
	colors = ButtonDefaults.colors(
		containerColor = Color.Transparent,
		contentColor = JellyfinTheme.colorScheme.onBackground,
	),
	contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
) {
	content()
}
