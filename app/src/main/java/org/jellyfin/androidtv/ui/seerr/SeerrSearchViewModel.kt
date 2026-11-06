package org.jellyfin.androidtv.ui.seerr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import org.jellyfin.androidtv.data.seerr.SeerrCatalog
import org.jellyfin.androidtv.data.seerr.SeerrMedia
import timber.log.Timber
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

data class SeerrSearchUiState(
	val query: String = "",
	val results: List<SeerrMedia> = emptyList(),
	val page: Int = 0,
	val totalPages: Int = 0,
	val loading: Boolean = false,
	val failed: Boolean = false,
)

class SeerrSearchViewModel(
	private val catalog: SeerrCatalog,
) : ViewModel() {
	private val _state = MutableStateFlow(SeerrSearchUiState())
	val state = _state.asStateFlow()

	private var searchJob: Job? = null

	fun search(query: String, immediately: Boolean = false) {
		val trimmed = query.trim()
		if (trimmed == _state.value.query && !_state.value.failed) return

		searchJob?.cancel()
		if (trimmed.isEmpty()) {
			_state.value = SeerrSearchUiState()
			return
		}

		_state.update { it.copy(query = trimmed, loading = true, failed = false) }
		searchJob = viewModelScope.launch {
			if (!immediately) delay(DEBOUNCE)

			val result = fetch(trimmed, 1)
			_state.update { state ->
				if (result == null) state.copy(results = emptyList(), loading = false, failed = true)
				else state.copy(
					results = result.items,
					page = result.page,
					totalPages = result.totalPages,
					loading = false,
				)
			}
		}
	}

	fun loadMore() {
		val current = _state.value
		if (current.loading || current.page >= current.totalPages) return

		_state.update { it.copy(loading = true) }
		searchJob = viewModelScope.launch {
			val result = fetch(current.query, current.page + 1)
			_state.update { state ->
				if (result == null) state.copy(loading = false)
				else state.copy(
					results = (state.results + result.items).distinctBy { it.type to it.id },
					page = result.page,
					totalPages = result.totalPages,
					loading = false,
				)
			}
		}
	}

	private suspend fun fetch(query: String, page: Int) = try {
		catalog.search(query, page)
	} catch (err: IOException) {
		Timber.w(err, "Unable to search Seerr")
		null
	} catch (err: SerializationException) {
		Timber.w(err, "Unexpected Seerr search response")
		null
	}

	private companion object {
		val DEBOUNCE = 600.milliseconds
	}
}
