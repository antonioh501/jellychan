package org.jellyfin.androidtv.ui.search.composable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.seerr.SeerrMedia
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.presentation.BaseRowItemCard
import org.jellyfin.androidtv.ui.search.SearchResultGroup
import org.jellyfin.androidtv.ui.seerr.SeerrCard
import org.jellyfin.sdk.model.api.BaseItemDto
import kotlin.time.Duration.Companion.milliseconds

private const val FOCUSED_SCALE = 1.06f
private const val SEERR_ROW_KEY = "seerr"
private const val FOCUS_RESTORE_ATTEMPTS = 20
// Space above the cards of a row: its title and the padding of the row
private val ROW_LEADING = 36.dp
private val FOCUS_RESTORE_DELAY = 50.milliseconds

private fun BaseItemDto.cardKey(row: Int) = "$row-$id"
private fun SeerrMedia.cardKey() = "$SEERR_ROW_KEY-$type-$id"

/**
 * JellyChan: search results as Compose rows: one row per kind of library item, then the Seerr titles that are not in
 * the library yet.
 *
 * @param focusedCard the last focused card: coming back to the page (resumed without the focus) it takes the focus back
 * @param focusFirstResult move the focus to the first card as soon as there are results (voice search, query from
 * another page), then [onFirstResultFocused] is called
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchResults(
	groups: Collection<SearchResultGroup>,
	seerrResults: List<SeerrMedia>,
	focusedCard: String?,
	focusFirstResult: Boolean,
	onFirstResultFocused: () -> Unit,
	onCardFocused: (key: String) -> Unit,
	onItemFocused: (item: BaseItemDto?) -> Unit,
	onItemClick: (item: BaseItemDto, row: Collection<BaseItemDto>) -> Unit,
	onSeerrClick: (media: SeerrMedia) -> Unit,
	modifier: Modifier = Modifier,
) {
	val rows = groups.filter { it.items.isNotEmpty() }
	val listState = rememberLazyListState()
	val firstCard = remember { FocusRequester() }
	val restoredCardFocus = remember { FocusRequester() }
	var restoreTarget by remember { mutableStateOf<String?>(null) }
	val currentFocusedCard by rememberUpdatedState(focusedCard)
	val view = LocalView.current
	val scope = rememberCoroutineScope()
	val hasResults = rows.isNotEmpty() || seerrResults.isNotEmpty()
	// A new query: the list would otherwise stay anchored on the row shown first (with one letter typed, People
	// often comes first and the movie and series rows then appear above it, out of sight)
	val generation = System.identityHashCode(groups)
	var skipScrollReset by remember { mutableStateOf(focusedCard != null) }

	LaunchedEffect(generation) {
		if (skipScrollReset) skipScrollReset = false
		else listState.scrollToItem(0)
	}

	LaunchedEffect(focusFirstResult, hasResults) {
		if (focusFirstResult && hasResults) {
			withFrameNanos { }
			firstCard.requestFocus()
			onFirstResultFocused()
		}
	}

	// Coming back from an item, the page is attached again with its focus blocked for a moment (see
	// DestinationFragmentView): retry until the view really holds the focus
	LifecycleResumeEffect(Unit) {
		val target = currentFocusedCard
		val job = if (target != null && !view.hasFocus()) scope.launch {
			restoreTarget = target
			var attempts = 0
			while (attempts++ < FOCUS_RESTORE_ATTEMPTS) {
				withFrameNanos { }
				runCatching { restoredCardFocus.requestFocus() }
				delay(FOCUS_RESTORE_DELAY)
				if (view.hasFocus()) break
			}
			restoreTarget = null
		} else null

		onPauseOrDispose { job?.cancel() }
	}

	fun Modifier.focusTargets(key: String, first: Boolean): Modifier {
		var modifier = this
		if (first) modifier = modifier.focusRequester(firstCard)
		if (key == restoreTarget) modifier = modifier.focusRequester(restoredCardFocus)
		return modifier
	}

	// Like the leanback rows: the row holding the focus moves to the top, instead of the least scroll that leaves the
	// row above cut in half. The rows themselves keep the default horizontal behavior.
	val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
	val rowLeading = with(LocalDensity.current) { ROW_LEADING.toPx() }
	val alignRowSpec = remember(rowLeading) {
		object : BringIntoViewSpec {
			override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = offset - rowLeading
		}
	}

	CompositionLocalProvider(LocalBringIntoViewSpec provides alignRowSpec) {
		LazyColumn(
			state = listState,
			contentPadding = PaddingValues(bottom = 27.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp),
			modifier = modifier,
		) {
			rows.forEachIndexed { rowIndex, group ->
				item(key = "$generation-${group.labelRes}") {
					ResultRow(title = stringResource(group.labelRes), bringIntoViewSpec = defaultBringIntoViewSpec) {
						itemsIndexed(group.items.toList(), key = { _, item -> item.id }) { index, item ->
							val key = item.cardKey(group.labelRes)
							LibraryCard(
								item = item,
								onClick = { onItemClick(item, group.items) },
								onFocused = {
									onCardFocused(key)
									onItemFocused(item)
								},
								modifier = Modifier.focusTargets(key, first = rowIndex == 0 && index == 0),
							)
						}
					}
				}
			}

			if (seerrResults.isNotEmpty()) {
				item(key = SEERR_ROW_KEY) {
					ResultRow(title = stringResource(R.string.jc_search_seerr_row), bringIntoViewSpec = defaultBringIntoViewSpec) {
						items(seerrResults, key = { it.cardKey() }) { media ->
							val key = media.cardKey()
							SeerrCard(
								media = media,
								onClick = { onSeerrClick(media) },
								onFocused = {
									onCardFocused(key)
									onItemFocused(null)
								},
								modifier = Modifier.focusTargets(key, first = rows.isEmpty() && media == seerrResults.first()),
							)
						}
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultRow(
	title: String,
	bringIntoViewSpec: BringIntoViewSpec,
	content: LazyListScope.() -> Unit,
) = CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
	Column {
		Text(
			text = title,
			fontSize = 14.sp,
			fontWeight = FontWeight.SemiBold,
			color = Color.White,
			modifier = Modifier.padding(start = 8.dp, top = 4.dp),
		)

		LazyRow(
			contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			// Coming back to a row lands on the card that was focused there, like the leanback rows
			modifier = Modifier
				.focusRestorer()
				.focusGroup(),
			content = content,
		)
	}
}

@Composable
private fun LibraryCard(
	item: BaseItemDto,
	onClick: () -> Unit,
	onFocused: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val rowItem = remember(item) { BaseItemDtoBaseRowItem(item) }
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val scale by animateFloatAsState(if (focused) FOCUSED_SCALE else 1f, label = "SearchCardFocus")

	LaunchedEffect(focused) {
		if (focused) onFocused()
	}

	// Zoom on the inner box only: the scrolling parents would otherwise see the zoomed bounds and move
	Box(
		modifier = modifier.clickable(
			interactionSource = interactionSource,
			indication = null,
			onClick = onClick,
		),
	) {
		Box(modifier = Modifier.scale(scale)) {
			BaseRowItemCard(item = rowItem, focused = focused)
		}
	}
}
