package org.jellyfin.androidtv.ui.seerr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import coil3.compose.rememberAsyncImagePainter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.seerr.SeerrAvailability
import org.jellyfin.androidtv.data.seerr.SeerrDetails
import org.jellyfin.androidtv.data.seerr.SeerrMediaType
import org.jellyfin.androidtv.data.seerr.SeerrRequest
import org.jellyfin.androidtv.data.seerr.SeerrRequestStatus
import org.jellyfin.androidtv.data.seerr.SeerrSeasonState
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.base.modifier.autoFocus
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.util.Locale

private val PageBackground = Color(0xFF111827)
private val SubtleText = Color(0xFFD1D5DB)
private val Accent = Color(0xFF5D55E7)
private val PosterWidth = 150.dp

class SeerrDetailFragment : Fragment() {
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		val type = arguments?.getString(EXTRA_TYPE)?.let { name -> SeerrMediaType.entries.firstOrNull { it.name == name } }
		val id = arguments?.getInt(EXTRA_ID)

		JellyfinTheme {
			if (type != null && id != null) SeerrDetailPage(type, id)
			else SeerrPageMessage(R.string.jc_seerr_error_load)
		}
	}

	companion object {
		const val EXTRA_TYPE = "SeerrType"
		const val EXTRA_ID = "SeerrId"
	}
}

@Composable
private fun SeerrDetailPage(
	type: SeerrMediaType,
	id: Int,
	viewModel: SeerrDetailViewModel = koinViewModel(),
) {
	val state by viewModel.state.collectAsState()
	val details = state.details

	LaunchedEffect(type, id) { viewModel.load(type, id) }

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(PageBackground),
	) {
		details?.backdropUrl?.let { SeerrBackdrop(it) }

		Column {
			MainToolbar(MainToolbarActiveButton.Discover)

			when {
				state.failed -> SeerrPageMessage(
					message = R.string.jc_seerr_error_load,
					action = R.string.jc_seerr_retry,
					onAction = viewModel::retry,
				)

				details == null -> SeerrPageMessage(R.string.jc_seerr_status_connecting)

				else -> SeerrDetailContent(
					details = details,
					state = state,
					viewModel = viewModel,
					modifier = Modifier.weight(1f),
				)
			}
		}
	}
}

@Composable
private fun SeerrDetailContent(
	details: SeerrDetails,
	state: SeerrDetailUiState,
	viewModel: SeerrDetailViewModel,
	modifier: Modifier = Modifier,
) {
	val navigationRepository = koinInject<NavigationRepository>()
	var headerHeight by remember { mutableIntStateOf(0) }
	var recommendationsFocused by remember { mutableStateOf(false) }
	// Only two positions, header or recommendations on top: no scroller, so the focus can never leave the page
	// stuck halfway with the title cut off
	val offset by animateIntAsState(if (recommendationsFocused) headerHeight else 0, label = "SeerrDetailOffset")

	Column(
		modifier = modifier
			.clipToBounds()
			.wrapContentHeight(Alignment.Top, unbounded = true)
			.offset { IntOffset(0, -offset) },
	) {
	Row(
		horizontalArrangement = Arrangement.spacedBy(32.dp),
		modifier = Modifier
			.fillMaxWidth()
			.onSizeChanged { headerHeight = it.height }
			.onFocusChanged { if (it.hasFocus) recommendationsFocused = false }
			.padding(horizontal = 48.dp, vertical = 16.dp),
	) {
		details.posterUrl?.let { poster ->
			Image(
				painter = rememberAsyncImagePainter(poster),
				contentDescription = null,
				contentScale = ContentScale.Crop,
				modifier = Modifier
					.width(PosterWidth)
					.height(PosterWidth * 3 / 2)
					.clip(RoundedCornerShape(10.dp)),
			)
		}

		Column(
			verticalArrangement = Arrangement.spacedBy(6.dp),
			modifier = Modifier.weight(1f),
		) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp),
			) {
				Text(
					text = details.title,
					fontSize = 26.sp,
					fontWeight = FontWeight.Bold,
					color = Color.White,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f, fill = false),
				)
				SeerrStatusBadge(details.availability, long = true, fontSize = 11)
			}

			val meta = buildList {
				details.year?.let(::add)
				add(
					stringResource(
						when (details.type) {
							SeerrMediaType.MOVIE -> R.string.jc_seerr_type_movie
							SeerrMediaType.SERIES -> R.string.jc_seerr_type_series
						}
					)
				)
				details.runtimeMinutes?.let { add(stringResource(R.string.jc_seerr_runtime, it)) }
				details.rating?.let { add("★ " + String.format(Locale.getDefault(), "%.1f", it)) }
			}.joinToString(" · ")
			Text(text = meta, fontSize = 12.sp, color = SubtleText)

			if (details.genres.isNotEmpty()) {
				Text(text = details.genres.joinToString(" / "), fontSize = 12.sp, color = SubtleText, maxLines = 1)
			}

			details.tagline?.let {
				Text(text = it, fontSize = 12.sp, fontStyle = FontStyle.Italic, color = SubtleText, maxLines = 2)
			}

			details.overview?.let {
				Text(text = it, fontSize = 12.sp, lineHeight = 17.sp, color = SubtleText, maxLines = 5, overflow = TextOverflow.Ellipsis)
			}

			Spacer(Modifier.height(6.dp))

			SeerrRequestArea(details, state, viewModel)
		}
	}

	if (state.recommendations.isNotEmpty()) {
		Column(
			modifier = Modifier.onFocusChanged { if (it.hasFocus) recommendationsFocused = true },
		) {
			Text(
				text = stringResource(R.string.jc_seerr_recommendations),
				fontSize = 14.sp,
				fontWeight = FontWeight.SemiBold,
				color = Color.White,
				modifier = Modifier.padding(start = 48.dp, top = 8.dp, bottom = 6.dp),
			)

			LazyRow(
				contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
				horizontalArrangement = Arrangement.spacedBy(12.dp),
			) {
				itemsIndexed(state.recommendations, key = { _, media -> "${media.type}-${media.id}" }) { _, media ->
					SeerrCard(
						media = media,
						onClick = { navigationRepository.openSeerrMedia(media) },
					)
				}
			}
		}
	}
	}
}

@Composable
private fun SeerrRequestArea(
	details: SeerrDetails,
	state: SeerrDetailUiState,
	viewModel: SeerrDetailViewModel,
) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		when (details.type) {
			SeerrMediaType.MOVIE -> if (details.movieRequestable) {
				Button(
					onClick = viewModel::request,
					enabled = !state.busy,
					modifier = Modifier.autoFocus(),
				) {
					Text(stringResource(R.string.jc_seerr_request), fontSize = 13.sp)
				}
			}

			SeerrMediaType.SERIES -> if (details.seasons.any { it.requestable }) {
				Button(
					onClick = viewModel::request,
					enabled = state.selectedSeasons.isNotEmpty() && !state.busy,
				) {
					Text(
						text = stringResource(R.string.jc_seerr_request_selected, state.selectedSeasons.size),
						fontSize = 13.sp,
					)
				}

				Button(onClick = viewModel::selectAllSeasons) {
					Text(stringResource(R.string.jc_seerr_select_all), fontSize = 13.sp)
				}
			}
		}

		state.message?.let { message ->
			Text(
				text = stringResource(message.text()),
				fontSize = 13.sp,
				fontWeight = FontWeight.SemiBold,
				color = if (message == SeerrRequestMessage.SENT || message == SeerrRequestMessage.CANCELLED) Color(0xFF4ADE80) else Color(0xFFF87171),
			)
		}
	}

	val firstCancellable = details.requests.firstOrNull { it.canBeCancelled() }
	details.requests.forEach { request ->
		SeerrRequestLine(
			request = request,
			busy = state.busy,
			focusFirst = request == firstCancellable,
			onCancel = { viewModel.cancelRequest(request.id) },
		)
	}

	if (details.type == SeerrMediaType.SERIES && details.seasons.isNotEmpty()) {
		Spacer(Modifier.height(4.dp))

		val firstRequestable = details.seasons.firstOrNull { it.requestable }?.number

		LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
			items(details.seasons, key = { it.number }) { season ->
				SeerrSeasonChip(
					season = season,
					modifier = if (season.number == firstRequestable) Modifier.autoFocus() else Modifier,
					selected = season.number in state.selectedSeasons,
					onClick = { viewModel.toggleSeason(season.number) },
				)
			}
		}
	}
}

@Composable
private fun SeerrSeasonChip(
	season: SeerrSeasonState,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val title = stringResource(R.string.jc_seerr_season, season.number)
	val status = season.availability.label()?.let { stringResource(it) }

	Button(
		onClick = onClick,
		modifier = modifier,
		enabled = season.requestable,
		colors = if (selected) ButtonDefaults.colors(containerColor = Accent, contentColor = Color.White) else ButtonDefaults.colors(),
	) {
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			Text(text = (if (selected) "✓ " else "") + title, fontSize = 12.sp)
			// Seasons that are not out yet have no episodes
			val caption = status ?: season.episodes.takeIf { it > 0 }?.let { stringResource(R.string.jc_seerr_episodes, it) }
			caption?.let { Text(text = it, fontSize = 10.sp) }
		}
	}
}

@StringRes
private fun SeerrRequestMessage.text() = when (this) {
	SeerrRequestMessage.SENT -> R.string.jc_seerr_request_sent
	SeerrRequestMessage.CANCELLED -> R.string.jc_seerr_request_cancelled
	SeerrRequestMessage.FORBIDDEN -> R.string.jc_seerr_request_forbidden
	SeerrRequestMessage.FAILED -> R.string.jc_seerr_request_failed
}

private fun SeerrRequest.canBeCancelled() = cancellable && active && status != SeerrRequestStatus.COMPLETED

@Composable
private fun SeerrRequestLine(
	request: SeerrRequest,
	busy: Boolean,
	focusFirst: Boolean,
	onCancel: () -> Unit,
) {
	val status = stringResource(
		when (request.status) {
			SeerrRequestStatus.PENDING -> R.string.jc_seerr_request_status_pending
			SeerrRequestStatus.APPROVED -> R.string.jc_seerr_request_status_approved
			SeerrRequestStatus.DECLINED -> R.string.jc_seerr_request_status_declined
			SeerrRequestStatus.FAILED -> R.string.jc_seerr_request_status_failed
			SeerrRequestStatus.COMPLETED -> R.string.jc_seerr_request_status_completed
		}
	)
	val text = listOfNotNull(
		request.requester?.let { stringResource(R.string.jc_seerr_requested_by, it) },
		status,
		request.seasons.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.jc_seerr_request_seasons, it.joinToString(", ")) },
	).joinToString(" \u00b7 ")

	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Text(text = text, fontSize = 12.sp, color = SubtleText)

		if (request.canBeCancelled()) {
			Button(
				onClick = onCancel,
				enabled = !busy,
				modifier = if (focusFirst) Modifier.autoFocus() else Modifier,
			) {
				Text(stringResource(R.string.jc_seerr_cancel_request), fontSize = 12.sp)
			}
		}
	}
}
