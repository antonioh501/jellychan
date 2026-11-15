package org.jellyfin.androidtv.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.search.composable.SearchKeyboard
import org.jellyfin.androidtv.ui.search.composable.SearchResults
import org.jellyfin.androidtv.ui.search.composable.SearchSuggestions
import org.jellyfin.androidtv.ui.search.composable.SearchVoiceInput
import org.jellyfin.androidtv.ui.seerr.openSeerrMedia
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.androidtv.util.speech.rememberSpeechRecognizerAvailability
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

class SearchFragment : Fragment() {
	companion object {
		const val EXTRA_QUERY = "query"
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	) = content {
		val viewModel = koinViewModel<SearchViewModel>()
		val searchFragmentDelegate = koinInject<SearchFragmentDelegate> { parametersOf(requireContext()) }
		val navigationRepository = koinInject<NavigationRepository>()
		val userRepository = koinInject<UserRepository>()
		val userId = userRepository.currentUser.collectAsState().value?.id
		val context = LocalContext.current
		val historyStore = remember { SearchHistoryStore(context) }
		var history by remember(userId) { mutableStateOf(historyStore.get(userId)) }
		val results by viewModel.searchResultsFlow.collectAsState()
		val seerrResults by viewModel.seerrResultsFlow.collectAsState()
		val suggestions by viewModel.suggestionsFlow.collectAsState()

		// JellyChan: no text field and no system keyboard; the query is edited by the on-screen keyboard (see SearchKeyboard)
		var query by rememberSaveable { mutableStateOf("") }
		// Survives the trip to an item, so coming back lands on the same card
		var focusedCard by rememberSaveable { mutableStateOf<String?>(null) }
		var focusFirstResult by remember { mutableStateOf(false) }
		val keyboardFocusRequester = remember { FocusRequester() }
		val firstKeyFocusRequester = remember { FocusRequester() }
		val speechRecognizerAvailability = rememberSpeechRecognizerAvailability()

		fun updateQuery(value: String, immediate: Boolean = false) {
			query = value
			if (immediate) viewModel.searchImmediately(value) else viewModel.searchDebounced(value)
		}

		fun saveQuery() {
			history = historyStore.add(userId, query)
		}

		LaunchedEffect(Unit) {
			val extraQuery = arguments?.getString(EXTRA_QUERY)
			when {
				focusedCard != null -> Unit // SearchResults gives the focus back to that card
				!extraQuery.isNullOrBlank() && query.isEmpty() -> {
					updateQuery(extraQuery, immediate = true)
					focusFirstResult = true
				}
				else -> firstKeyFocusRequester.requestFocus()
			}
		}

		Column {
			MainToolbar(MainToolbarActiveButton.Search)

			Row(modifier = Modifier.fillMaxSize()) {
				SearchKeyboard(
					query = query,
					// Always apply edits to the current state: key events can arrive faster than recompositions
					onType = { updateQuery(query + it) },
					onBackspace = { updateQuery(query.dropLast(1)) },
					onClear = { updateQuery("") },
					firstKeyFocusRequester = firstKeyFocusRequester,
					voiceInput = if (speechRecognizerAvailability) {
						{
							SearchVoiceInput(
								onQueryChange = { query = it },
								onQuerySubmit = {
									updateQuery(query, immediate = true)
									focusFirstResult = true
								}
							)
						}
					} else null,
					modifier = Modifier
						.focusRequester(keyboardFocusRequester)
						.padding(start = 48.dp, end = 24.dp),
				)

				Column(modifier = Modifier.weight(1f)) {
					val showHistory = query.isBlank()
					SearchSuggestions(
						suggestions = if (showHistory) history else suggestions.filterNot { it.equals(query.trim(), ignoreCase = true) },
						isHistory = showHistory,
						onSuggestionClick = { suggestion ->
							history = historyStore.add(userId, suggestion)
							updateQuery(suggestion, immediate = true)
							focusFirstResult = true
						},
						onClearHistory = {
							historyStore.clear(userId)
							history = emptyList()
							keyboardFocusRequester.requestFocus()
						},
						modifier = Modifier
							.fillMaxWidth()
							.padding(top = 8.dp, end = 48.dp),
					)

					SearchResults(
						groups = results,
						seerrResults = seerrResults,
						focusedCard = focusedCard,
						focusFirstResult = focusFirstResult,
						onFirstResultFocused = { focusFirstResult = false },
						onCardFocused = { focusedCard = it },
						onItemFocused = searchFragmentDelegate::onFocused,
						onItemClick = { item, row ->
							saveQuery()
							searchFragmentDelegate.launch(item, row)
						},
						onSeerrClick = { media ->
							saveQuery()
							navigationRepository.openSeerrMedia(media)
						},
						modifier = Modifier
							.padding(top = 4.dp)
							.fillMaxSize(),
					)
				}
			}
		}
	}
}
