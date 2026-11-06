package org.jellyfin.androidtv.ui.seerr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.search.composable.SearchTextInput
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private const val LOAD_MORE_THRESHOLD = 12

class SeerrSearchFragment : Fragment() {
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		JellyfinTheme {
			SeerrSearchPage()
		}
	}
}

@Composable
private fun SeerrSearchPage(
	viewModel: SeerrSearchViewModel = koinViewModel(),
) {
	val navigationRepository = koinInject<NavigationRepository>()
	val state by viewModel.state.collectAsState()
	var query by rememberSaveable { mutableStateOf("") }
	var focusResults by remember { mutableStateOf(false) }
	val inputFocus = remember { FocusRequester() }
	val resultsFocus = remember { FocusRequester() }
	val firstResultFocus = remember { FocusRequester() }
	val restoredResultFocus = remember { FocusRequester() }
	// Survives the trip to another page: without it the input takes the focus and opens the keyboard
	var focusedResult by rememberSaveable { mutableIntStateOf(0) }
	val restoredResult = remember { focusedResult.coerceAtMost(state.results.lastIndex) }
	var focusRestored by remember { mutableStateOf(false) }

	LaunchedEffect(Unit) {
		if (state.results.isEmpty()) inputFocus.requestFocus()
	}

	LaunchedEffect(focusResults, state.loading, state.results) {
		if (focusResults && !state.loading && state.results.isNotEmpty()) {
			withFrameNanos { }
			firstResultFocus.requestFocus()
			focusResults = false
		}
	}

	Column(
		modifier = Modifier
			.fillMaxSize()
			.background(Color(0xFF111827)),
	) {
		MainToolbar(MainToolbarActiveButton.Discover)

		SearchTextInput(
			query = query,
			onQueryChange = {
				query = it
				viewModel.search(it)
			},
			onQuerySubmit = {
				viewModel.search(query, immediately = true)
				focusResults = true
				// Some vendors keep showing a fullscreen keyboard until the focus moves away
				if (state.results.isEmpty()) resultsFocus.requestFocus()
				else firstResultFocus.requestFocus()
			},
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 48.dp)
				.focusRequester(inputFocus),
		)

		Box(
			modifier = Modifier
				.fillMaxSize()
				.focusRequester(resultsFocus)
				// Only a placeholder for the focus: it would catch the back key when there are results
				.focusable(enabled = state.results.isEmpty()),
		) {
			when {
				state.failed -> SeerrPageMessage(R.string.jc_seerr_error_load)
				state.results.isEmpty() && state.loading -> SeerrPageMessage(R.string.jc_seerr_status_connecting)
				state.results.isEmpty() && state.query.isNotEmpty() -> SeerrPageMessage(R.string.jc_seerr_no_results)
				else -> LazyVerticalGrid(
					columns = GridCells.Adaptive(SeerrCardWidth),
					contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
					horizontalArrangement = Arrangement.spacedBy(12.dp),
					verticalArrangement = Arrangement.spacedBy(14.dp),
					modifier = Modifier.fillMaxSize(),
				) {
					itemsIndexed(state.results, key = { _, media -> "${media.type}-${media.id}" }) { index, media ->
						val firstModifier = if (index == 0) Modifier.focusRequester(firstResultFocus) else Modifier
						val restoreModifier = when (index) {
							restoredResult -> Modifier
								.focusRequester(restoredResultFocus)
								.onPlaced {
									if (!focusRestored) {
										focusRestored = true
										restoredResultFocus.requestFocus()
									}
								}

							else -> Modifier
						}

						SeerrCard(
							media = media,
							modifier = firstModifier.then(restoreModifier),
							onClick = { navigationRepository.openSeerrMedia(media) },
							onFocused = {
								focusedResult = index
								if (index >= state.results.size - LOAD_MORE_THRESHOLD) viewModel.loadMore()
							},
						)
					}
				}
			}
		}
	}
}
