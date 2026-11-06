package org.jellyfin.androidtv.ui.seerr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import org.jellyfin.androidtv.data.seerr.SeerrCatalog
import org.jellyfin.androidtv.data.seerr.SeerrRepository
import org.jellyfin.androidtv.data.seerr.SeerrRequestEntry
import org.jellyfin.androidtv.data.seerr.SeerrRequestEntryPage
import org.jellyfin.androidtv.data.seerr.SeerrRequestResult
import org.jellyfin.androidtv.data.seerr.SeerrState
import timber.log.Timber
import java.io.IOException

data class SeerrRequestsUiState(
	val items: List<SeerrRequestEntry> = emptyList(),
	val page: Int = 0,
	val totalPages: Int = 1,
	val loading: Boolean = true,
	val loadingMore: Boolean = false,
	val failed: Boolean = false,
	val busy: Boolean = false,
	val message: SeerrRequestMessage? = null,
)

class SeerrRequestsViewModel(
	seerrRepository: SeerrRepository,
	private val catalog: SeerrCatalog,
) : ViewModel() {
	val connection: StateFlow<SeerrState> = seerrRepository.state

	private val _state = MutableStateFlow(SeerrRequestsUiState())
	val state = _state.asStateFlow()

	init {
		load()
	}

	fun retry() {
		_state.value = SeerrRequestsUiState()
		load()
	}

	fun loadMore() {
		val current = _state.value
		if (current.loading || current.loadingMore || current.page >= current.totalPages) return

		_state.update { it.copy(loadingMore = true) }
		load(current.page + 1)
	}

	fun cancel(requestId: Int) {
		if (_state.value.busy) return

		_state.update { it.copy(busy = true, message = null) }
		viewModelScope.launch {
			val result = catalog.cancelRequest(requestId)

			_state.update { state ->
				when (result) {
					SeerrRequestResult.SUCCESS -> state.copy(
						busy = false,
						message = SeerrRequestMessage.CANCELLED,
						items = state.items.filterNot { it.request.id == requestId },
					)

					SeerrRequestResult.FORBIDDEN -> state.copy(busy = false, message = SeerrRequestMessage.FORBIDDEN)
					SeerrRequestResult.FAILED -> state.copy(busy = false, message = SeerrRequestMessage.FAILED)
				}
			}
		}
	}

	private fun load(page: Int = 1) {
		viewModelScope.launch {
			val result: SeerrRequestEntryPage? = try {
				catalog.myRequests(page)
			} catch (err: IOException) {
				Timber.w(err, "Unable to load the Seerr requests")
				null
			} catch (err: SerializationException) {
				Timber.w(err, "Unable to read the Seerr requests")
				null
			}

			_state.update { state ->
				if (result == null) {
					state.copy(loading = false, loadingMore = false, failed = state.items.isEmpty())
				} else {
					state.copy(
						items = (state.items + result.items).distinctBy { it.request.id },
						page = result.page,
						totalPages = result.totalPages,
						loading = false,
						loadingMore = false,
						failed = false,
					)
				}
			}
		}
	}
}
