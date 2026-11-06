package org.jellyfin.androidtv.ui.seerr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import org.jellyfin.androidtv.data.seerr.SeerrCatalog
import org.jellyfin.androidtv.data.seerr.SeerrDiscoverList
import org.jellyfin.androidtv.data.seerr.SeerrMedia
import org.jellyfin.androidtv.data.seerr.SeerrMediaPage
import org.jellyfin.androidtv.data.seerr.SeerrRepository
import org.jellyfin.androidtv.data.seerr.SeerrState
import timber.log.Timber
import java.io.IOException

data class SeerrDiscoverRow(
	val list: SeerrDiscoverList,
	val items: List<SeerrMedia>,
	val page: Int,
	val totalPages: Int,
	val loadingMore: Boolean = false,
)

data class SeerrDiscoverUiState(
	val loading: Boolean = true,
	val failed: Boolean = false,
	val rows: List<SeerrDiscoverRow> = emptyList(),
)

class SeerrDiscoverViewModel(
	seerrRepository: SeerrRepository,
	private val catalog: SeerrCatalog,
) : ViewModel() {
	val connection: StateFlow<SeerrState> = seerrRepository.state

	private val _state = MutableStateFlow(SeerrDiscoverUiState())
	val state = _state.asStateFlow()

	init {
		viewModelScope.launch {
			connection
				.map { it is SeerrState.Connected }
				.distinctUntilChanged()
				.collectLatest { connected ->
					if (connected) load()
					else _state.value = SeerrDiscoverUiState()
				}
		}
	}

	fun retry() {
		viewModelScope.launch { load() }
	}

	fun loadMore(list: SeerrDiscoverList) {
		val row = _state.value.rows.firstOrNull { it.list == list } ?: return
		if (row.loadingMore || row.page >= row.totalPages) return

		updateRow(list) { it.copy(loadingMore = true) }
		viewModelScope.launch {
			val next = fetch(list, row.page + 1)
			updateRow(list) { current ->
				if (next == null) return@updateRow current.copy(loadingMore = false)

				current.copy(
					items = (current.items + next.items).distinctBy { it.type to it.id },
					page = next.page,
					totalPages = next.totalPages,
					loadingMore = false,
				)
			}
		}
	}

	private suspend fun load() {
		_state.value = SeerrDiscoverUiState()

		val pages = coroutineScope {
			SeerrDiscoverList.entries
				.map { list -> async { list to fetch(list, 1) } }
				.awaitAll()
		}

		_state.value = SeerrDiscoverUiState(
			loading = false,
			failed = pages.all { (_, page) -> page == null },
			rows = pages.mapNotNull { (list, page) ->
				page?.takeIf { it.items.isNotEmpty() }?.let {
					SeerrDiscoverRow(list, it.items, it.page, it.totalPages)
				}
			},
		)
	}

	private suspend fun fetch(list: SeerrDiscoverList, page: Int): SeerrMediaPage? = try {
		catalog.discover(list, page)
	} catch (err: IOException) {
		Timber.w(err, "Unable to load Seerr list $list")
		null
	} catch (err: SerializationException) {
		Timber.w(err, "Unexpected Seerr response for list $list")
		null
	}

	private fun updateRow(list: SeerrDiscoverList, transform: (SeerrDiscoverRow) -> SeerrDiscoverRow) {
		_state.update { state ->
			state.copy(rows = state.rows.map { if (it.list == list) transform(it) else it })
		}
	}
}
