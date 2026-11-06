package org.jellyfin.androidtv.ui.seerr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import org.jellyfin.androidtv.data.seerr.SeerrAvailability
import org.jellyfin.androidtv.data.seerr.SeerrCatalog
import org.jellyfin.androidtv.data.seerr.SeerrDetails
import org.jellyfin.androidtv.data.seerr.SeerrMedia
import org.jellyfin.androidtv.data.seerr.SeerrMediaType
import org.jellyfin.androidtv.data.seerr.SeerrRequestResult
import timber.log.Timber
import java.io.IOException

enum class SeerrRequestMessage {
	SENT,
	CANCELLED,
	FORBIDDEN,
	FAILED,
}

data class SeerrDetailUiState(
	val details: SeerrDetails? = null,
	val recommendations: List<SeerrMedia> = emptyList(),
	val loading: Boolean = true,
	val failed: Boolean = false,
	val selectedSeasons: Set<Int> = emptySet(),
	val busy: Boolean = false,
	val message: SeerrRequestMessage? = null,
)

class SeerrDetailViewModel(
	private val catalog: SeerrCatalog,
) : ViewModel() {
	private val _state = MutableStateFlow(SeerrDetailUiState())
	val state = _state.asStateFlow()

	private var type: SeerrMediaType? = null
	private var id: Int = 0

	fun load(type: SeerrMediaType, id: Int) {
		// The page is recreated when coming back to it: keep what is already shown
		if (this.type == type && this.id == id && _state.value.details != null) return

		this.type = type
		this.id = id
		_state.value = SeerrDetailUiState()
		fetch()
	}

	fun retry() {
		_state.update { it.copy(loading = true, failed = false) }
		fetch()
	}

	fun toggleSeason(number: Int) {
		_state.update { state ->
			val selected = state.selectedSeasons
			state.copy(selectedSeasons = if (number in selected) selected - number else selected + number, message = null)
		}
	}

	fun selectAllSeasons() {
		_state.update { state ->
			val requestable = state.details?.seasons.orEmpty().filter { it.requestable }.map { it.number }.toSet()
			// A second press clears the selection
			state.copy(selectedSeasons = if (state.selectedSeasons == requestable) emptySet() else requestable, message = null)
		}
	}

	fun request() {
		val current = _state.value
		val details = current.details ?: return
		if (current.busy) return

		// What is already requested or present is never sent again
		val seasons = when (details.type) {
			SeerrMediaType.MOVIE -> {
				if (!details.movieRequestable) return
				null
			}

			SeerrMediaType.SERIES -> {
				val requestable = details.seasons.filter { it.requestable }.map { it.number }.toSet()
				current.selectedSeasons.filter { it in requestable }.sorted().takeIf { it.isNotEmpty() } ?: return
			}
		}

		_state.update { it.copy(busy = true, message = null) }
		viewModelScope.launch {
			val result = catalog.request(details.type, details.id, seasons)

			_state.update { state ->
				when (result) {
					SeerrRequestResult.SUCCESS -> state.copy(
						busy = false,
						message = SeerrRequestMessage.SENT,
						details = state.details?.markRequested(seasons),
						selectedSeasons = emptySet(),
					)

					SeerrRequestResult.FORBIDDEN -> state.copy(busy = false, message = SeerrRequestMessage.FORBIDDEN)
					SeerrRequestResult.FAILED -> state.copy(busy = false, message = SeerrRequestMessage.FAILED)
				}
			}
			if (result == SeerrRequestResult.SUCCESS) refresh()
		}
	}

	fun cancelRequest(requestId: Int) {
		if (_state.value.busy) return

		_state.update { it.copy(busy = true, message = null) }
		viewModelScope.launch {
			val result = catalog.cancelRequest(requestId)

			_state.update {
				it.copy(
					busy = false,
					message = when (result) {
						SeerrRequestResult.SUCCESS -> SeerrRequestMessage.CANCELLED
						SeerrRequestResult.FORBIDDEN -> SeerrRequestMessage.FORBIDDEN
						SeerrRequestResult.FAILED -> SeerrRequestMessage.FAILED
					},
				)
			}
			if (result == SeerrRequestResult.SUCCESS) refresh()
		}
	}

	/** Show the request at once: the server needs a moment to report it back. */
	private fun SeerrDetails.markRequested(seasons: List<Int>?) = when (type) {
		SeerrMediaType.MOVIE -> copy(availability = SeerrAvailability.PENDING)
		SeerrMediaType.SERIES -> copy(
			seasons = this.seasons.map { season ->
				if (seasons != null && season.number in seasons) season.copy(availability = SeerrAvailability.PENDING) else season
			},
		)
	}

	private fun fetch() {
		val type = type ?: return
		val id = id
		viewModelScope.launch {
			val details = loadDetails(type, id)
			_state.update {
				if (details == null) it.copy(loading = false, failed = true)
				else it.copy(details = details, loading = false, failed = false)
			}
		}
		viewModelScope.launch {
			val recommendations = try {
				catalog.recommendations(type, id)
			} catch (err: IOException) {
				Timber.w(err, "Unable to load the Seerr recommendations")
				emptyList()
			} catch (err: SerializationException) {
				Timber.w(err, "Unable to read the Seerr recommendations")
				emptyList()
			}
			_state.update { it.copy(recommendations = recommendations) }
		}
	}

	/** Show the new status without hiding the page behind a spinner. */
	private suspend fun refresh() {
		val type = type ?: return
		val details = loadDetails(type, id) ?: return
		_state.update { it.copy(details = details, selectedSeasons = emptySet()) }
	}

	private suspend fun loadDetails(type: SeerrMediaType, id: Int) = try {
		catalog.details(type, id)
	} catch (err: IOException) {
		Timber.w(err, "Unable to load the Seerr details")
		null
	} catch (err: SerializationException) {
		Timber.w(err, "Unable to read the Seerr details")
		null
	}
}
