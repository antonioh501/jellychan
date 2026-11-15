package org.jellyfin.androidtv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.seerr.SeerrCatalog
import org.jellyfin.androidtv.data.seerr.SeerrMedia
import org.jellyfin.androidtv.data.seerr.SeerrRepository
import org.jellyfin.androidtv.data.seerr.SeerrState
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class SearchViewModel(
	private val searchRepository: SearchRepository,
	private val seerrRepository: SeerrRepository,
	private val seerrCatalog: SeerrCatalog,
) : ViewModel() {
	companion object {
		// JellyChan: shorter debounce, results follow the on-screen keyboard while typing
		private val debounceDuration = 300.milliseconds
		private const val MAX_SUGGESTIONS = 10

		// JellyChan: Seerr searches TMDB, slower and remote: wait a bit longer and skip one-letter queries
		private val seerrDebounceDuration = 500.milliseconds
		private const val SEERR_MIN_QUERY_LENGTH = 2

		private val suggestionKinds = setOf(
			BaseItemKind.MOVIE,
			BaseItemKind.SERIES,
			BaseItemKind.BOX_SET,
			BaseItemKind.PERSON,
			BaseItemKind.MUSIC_ARTIST,
			BaseItemKind.MUSIC_ALBUM,
			BaseItemKind.PLAYLIST,
		)

		private val groups = mapOf(
			R.string.lbl_movies to setOf(BaseItemKind.MOVIE),
			R.string.lbl_series to setOf(BaseItemKind.SERIES),
			R.string.lbl_episodes to setOf(BaseItemKind.EPISODE),
			R.string.lbl_videos to setOf(BaseItemKind.VIDEO),
			R.string.lbl_programs to setOf(BaseItemKind.LIVE_TV_PROGRAM),
			R.string.channels to setOf(BaseItemKind.LIVE_TV_CHANNEL),
			R.string.lbl_playlists to setOf(BaseItemKind.PLAYLIST),
			R.string.lbl_artists to setOf(BaseItemKind.MUSIC_ARTIST),
			R.string.lbl_albums to setOf(BaseItemKind.MUSIC_ALBUM),
			R.string.lbl_songs to setOf(BaseItemKind.AUDIO),
			R.string.photo_albums to setOf(BaseItemKind.PHOTO_ALBUM),
			R.string.photos to setOf(BaseItemKind.PHOTO),
			R.string.lbl_collections to setOf(BaseItemKind.BOX_SET),
			R.string.lbl_people to setOf(BaseItemKind.PERSON),
		)
	}

	private var searchJob: Job? = null
	private var seerrJob: Job? = null

	private var previousQuery: String? = null

	private val _searchResultsFlow = MutableStateFlow<Collection<SearchResultGroup>>(emptyList())
	val searchResultsFlow = _searchResultsFlow.asStateFlow()

	/** JellyChan: title completions for the suggestion chips, taken from the current results. */
	val suggestionsFlow = _searchResultsFlow
		.map { groups ->
			groups.asSequence()
				.flatMap { it.items }
				.filter { it.type in suggestionKinds }
				.mapNotNull { it.name?.trim()?.takeIf(String::isNotBlank) }
				.distinctBy { it.lowercase() }
				.take(MAX_SUGGESTIONS)
				.toList()
		}
		.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

	private val _seerrResultsFlow = MutableStateFlow<List<SeerrMedia>>(emptyList())

	/** JellyChan: Seerr results that are not in the library yet (those already show up in the library rows). */
	val seerrResultsFlow = _seerrResultsFlow.asStateFlow()

	fun searchImmediately(query: String) = searchDebounced(query, 0.milliseconds)

	fun searchDebounced(query: String, debounce: Duration = debounceDuration): Boolean {
		val trimmed = query.trim()
		if (trimmed == previousQuery) return false
		previousQuery = trimmed

		searchJob?.cancel()
		searchSeerr(trimmed, if (debounce == 0.milliseconds) debounce else seerrDebounceDuration)

		if (trimmed.isBlank()) {
			_searchResultsFlow.value = emptyList()
			return true
		}

		searchJob = viewModelScope.launch {
			delay(debounce)

			_searchResultsFlow.value = groups.map { (stringRes, itemKinds) ->
				async {
					val result = searchRepository.search(trimmed, itemKinds)
					val items = result.getOrNull().orEmpty()

					SearchResultGroup(stringRes, items)
				}
			}.awaitAll()
		}

		return true
	}

	private fun searchSeerr(query: String, debounce: Duration) {
		seerrJob?.cancel()
		_seerrResultsFlow.value = emptyList()
		if (query.length < SEERR_MIN_QUERY_LENGTH || seerrRepository.state.value !is SeerrState.Connected) return

		seerrJob = viewModelScope.launch {
			delay(debounce)

			_seerrResultsFlow.value = try {
				seerrCatalog.search(query).items.filter { it.libraryItemId == null }
			} catch (err: IOException) {
				Timber.w(err, "Unable to search Seerr")
				emptyList()
			} catch (err: SerializationException) {
				Timber.w(err, "Unexpected Seerr search response")
				emptyList()
			}
		}
	}
}
