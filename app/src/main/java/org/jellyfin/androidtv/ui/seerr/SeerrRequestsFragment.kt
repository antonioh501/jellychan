package org.jellyfin.androidtv.ui.seerr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import coil3.compose.rememberAsyncImagePainter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.seerr.SeerrMediaType
import org.jellyfin.androidtv.data.seerr.SeerrRequestEntry
import org.jellyfin.androidtv.data.seerr.SeerrRequestStatus
import org.jellyfin.androidtv.data.seerr.SeerrState
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.modifier.autoFocus
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private val PageBackground = Color(0xFF111827)
private val RowBackground = Color(0xFF1D2635)
private val SubtleText = Color(0xFF9CA3AF)
private val RowShape = RoundedCornerShape(10.dp)
private val InnerShape = RoundedCornerShape(8.dp)
private const val LOAD_MORE_THRESHOLD = 4

class SeerrRequestsFragment : Fragment() {
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		JellyfinTheme {
			SeerrRequestsPage()
		}
	}
}

@Composable
private fun SeerrRequestsPage(
	viewModel: SeerrRequestsViewModel = koinViewModel(),
) {
	val navigationRepository = koinInject<NavigationRepository>()
	val connection by viewModel.connection.collectAsState()
	val state by viewModel.state.collectAsState()
	val context = LocalContext.current

	Column(
		modifier = Modifier
			.fillMaxSize()
			.background(PageBackground),
	) {
		MainToolbar(MainToolbarActiveButton.Discover)

		Text(
			text = stringResource(R.string.jc_seerr_my_requests),
			fontSize = 22.sp,
			fontWeight = FontWeight.Bold,
			color = Color.White,
			modifier = Modifier.padding(start = 48.dp, top = 4.dp, bottom = 8.dp),
		)

		when {
			connection is SeerrState.SessionExpired || connection is SeerrState.Unavailable -> SeerrPageMessage(
				message = if (connection is SeerrState.SessionExpired) R.string.jc_seerr_status_expired else R.string.jc_seerr_error_unreachable,
				action = R.string.jc_seerr_open_settings,
				onAction = { context.startActivity(ActivityDestinations.seerrPreferences(context)) },
			)

			connection !is SeerrState.Connected || state.loading -> SeerrPageMessage(R.string.jc_seerr_status_connecting)

			state.failed -> SeerrPageMessage(
				message = R.string.jc_seerr_error_load,
				action = R.string.jc_seerr_retry,
				onAction = viewModel::retry,
			)

			state.items.isEmpty() -> SeerrPageMessage(R.string.jc_seerr_requests_empty)

			else -> {
				state.message?.let { message ->
					Text(
						text = stringResource(message.text()),
						fontSize = 12.sp,
						color = SubtleText,
						modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
					)
				}

				LazyColumn(
					contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp),
					modifier = Modifier.fillMaxSize(),
				) {
					itemsIndexed(state.items, key = { _, entry -> entry.request.id }) { index, entry ->
						SeerrRequestRow(
							entry = entry,
							busy = state.busy,
							focusFirst = index == 0,
							onOpen = { navigationRepository.openSeerrMedia(entry.media) },
							onCancel = { viewModel.cancel(entry.request.id) },
							onFocused = { if (index >= state.items.size - LOAD_MORE_THRESHOLD) viewModel.loadMore() },
						)
					}
				}
			}
		}
	}
}

private fun SeerrRequestMessage.text() = when (this) {
	SeerrRequestMessage.SENT -> R.string.jc_seerr_request_sent
	SeerrRequestMessage.CANCELLED -> R.string.jc_seerr_request_cancelled
	SeerrRequestMessage.FORBIDDEN -> R.string.jc_seerr_request_forbidden
	SeerrRequestMessage.FAILED -> R.string.jc_seerr_request_failed
}

@Composable
private fun SeerrRequestRow(
	entry: SeerrRequestEntry,
	busy: Boolean,
	focusFirst: Boolean,
	onOpen: () -> Unit,
	onCancel: () -> Unit,
	onFocused: () -> Unit,
) {
	val media = entry.media
	val request = entry.request
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	LaunchedEffect(focused) { if (focused) onFocused() }

	val status = stringResource(
		when (request.status) {
			SeerrRequestStatus.PENDING -> R.string.jc_seerr_request_status_pending
			SeerrRequestStatus.APPROVED -> R.string.jc_seerr_request_status_approved
			SeerrRequestStatus.DECLINED -> R.string.jc_seerr_request_status_declined
			SeerrRequestStatus.FAILED -> R.string.jc_seerr_request_status_failed
			SeerrRequestStatus.COMPLETED -> R.string.jc_seerr_request_status_completed
		}
	)
	val details = listOfNotNull(
		media.year,
		stringResource(if (media.type == SeerrMediaType.MOVIE) R.string.jc_seerr_type_movie else R.string.jc_seerr_type_series),
		request.seasons.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.jc_seerr_request_seasons, it.joinToString(", ")) },
		entry.createdAt?.let { stringResource(R.string.jc_seerr_requested_on, it.asLocalDate()) },
	).joinToString(" · ")

	val canCancel = request.cancellable && request.active && request.status != SeerrRequestStatus.COMPLETED

	// The title opens the page, the cancel button is a second stop on the same line
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		modifier = Modifier
			.fillMaxWidth()
			.clip(RowShape)
			.background(RowBackground)
			.padding(4.dp)
			.focusGroup(),
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			modifier = Modifier
				.weight(1f)
				.then(if (focusFirst) Modifier.autoFocus() else Modifier)
				.clip(InnerShape)
				.border(2.dp, if (focused) Color.White else Color.Transparent, InnerShape)
				.clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
				.padding(4.dp),
		) {
			Box(
				modifier = Modifier
					.width(44.dp)
					.aspectRatio(2f / 3f)
					.clip(RoundedCornerShape(4.dp))
					.background(PageBackground),
			) {
				media.posterUrl?.let {
					Image(
						painter = rememberAsyncImagePainter(it),
						contentDescription = null,
						contentScale = ContentScale.Crop,
						modifier = Modifier.fillMaxSize(),
					)
				}
			}

			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = media.title,
					fontSize = 15.sp,
					fontWeight = FontWeight.SemiBold,
					color = Color.White,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				Text(text = details, fontSize = 11.sp, color = SubtleText, maxLines = 1, overflow = TextOverflow.Ellipsis)
				Text(text = status, fontSize = 12.sp, color = Color(0xFFD1D5DB))
			}

			SeerrStatusBadge(media.availability, long = true, fontSize = 10)
		}

		if (canCancel) {
			Button(onClick = onCancel, enabled = !busy) {
				Text(stringResource(R.string.jc_seerr_cancel_request), fontSize = 12.sp)
			}
		}
	}
}

/** The day the server sent, written the way the device writes dates. */
private fun String.asLocalDate(): String = try {
	LocalDate.parse(this).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
} catch (_: java.time.format.DateTimeParseException) {
	this
}
