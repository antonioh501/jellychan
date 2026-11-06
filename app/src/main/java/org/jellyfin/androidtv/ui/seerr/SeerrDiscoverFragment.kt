package org.jellyfin.androidtv.ui.seerr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import coil3.compose.rememberAsyncImagePainter
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.seerr.SeerrDiscoverList
import org.jellyfin.androidtv.data.seerr.SeerrMedia
import org.jellyfin.androidtv.data.seerr.SeerrMediaType
import org.jellyfin.androidtv.data.seerr.SeerrState
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.modifier.autoFocus
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private val PageBackground = Color(0xFF111827)
private val PagePadding = 48.dp
private const val LOAD_MORE_THRESHOLD = 5
private const val BACKDROP_ALPHA = 0.45f

class SeerrDiscoverFragment : Fragment() {
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		JellyfinTheme {
			SeerrDiscoverPage()
		}
	}
}

/**
 * Open a result. Titles in the library use the regular item page.
 */
fun NavigationRepository.openSeerrMedia(media: SeerrMedia) {
	val libraryItemId = media.libraryItemId
	if (libraryItemId != null) navigate(Destinations.itemDetails(libraryItemId))
	else navigate(Destinations.seerrDetails(media.type, media.id))
}

@Composable
private fun SeerrDiscoverPage(
	viewModel: SeerrDiscoverViewModel = koinViewModel(),
) {
	val navigationRepository = koinInject<NavigationRepository>()
	val connection by viewModel.connection.collectAsState()
	val state by viewModel.state.collectAsState()
	val context = LocalContext.current
	var focusedMedia by remember { mutableStateOf<SeerrMedia?>(null) }
	val shownMedia = focusedMedia ?: state.rows.firstOrNull()?.items?.firstOrNull()

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(PageBackground),
	) {
		shownMedia?.backdropUrl?.let { SeerrBackdrop(it) }

		Column {
			MainToolbar(MainToolbarActiveButton.Discover)

			when {
				connection is SeerrState.SessionExpired -> SeerrPageMessage(
					message = R.string.jc_seerr_status_expired,
					action = R.string.jc_seerr_open_settings,
					onAction = { context.startActivity(ActivityDestinations.seerrPreferences(context)) },
				)

				connection is SeerrState.Unavailable -> SeerrPageMessage(
					message = R.string.jc_seerr_error_unreachable,
					action = R.string.jc_seerr_open_settings,
					onAction = { context.startActivity(ActivityDestinations.seerrPreferences(context)) },
				)

				connection !is SeerrState.Connected || state.loading -> SeerrPageMessage(R.string.jc_seerr_status_connecting)

				state.failed -> SeerrPageMessage(
					message = R.string.jc_seerr_error_load,
					action = R.string.jc_seerr_retry,
					onAction = viewModel::retry,
				)

				else -> SeerrDiscoverContent(
					rows = state.rows,
					shownMedia = shownMedia,
					onFocused = { focusedMedia = it },
					onClick = navigationRepository::openSeerrMedia,
					onSearch = { navigationRepository.navigate(Destinations.seerrSearch) },
					onRequests = { navigationRepository.navigate(Destinations.seerrRequests) },
					onNearEnd = viewModel::loadMore,
				)
			}
		}
	}
}

@Composable
fun SeerrBackdrop(url: String) {
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.height(300.dp),
	) {
		Image(
			painter = rememberAsyncImagePainter(url),
			contentDescription = null,
			contentScale = ContentScale.Crop,
			modifier = Modifier
				.fillMaxSize()
				.alpha(BACKDROP_ALPHA),
		)
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Brush.verticalGradient(listOf(Color.Transparent, PageBackground))),
		)
		Box(
			modifier = Modifier
				.fillMaxHeight()
				.fillMaxWidth(0.6f)
				.background(Brush.horizontalGradient(listOf(PageBackground, Color.Transparent))),
		)
	}
}

@Composable
private fun SeerrDiscoverContent(
	rows: List<SeerrDiscoverRow>,
	shownMedia: SeerrMedia?,
	onFocused: (SeerrMedia) -> Unit,
	onClick: (SeerrMedia) -> Unit,
	onSearch: () -> Unit,
	onRequests: () -> Unit,
	onNearEnd: (SeerrDiscoverList) -> Unit,
) {
	val initialCard = remember { FocusRequester() }
	var initialFocusDone by remember { mutableStateOf(false) }
	// Survives the trip to another page, so coming back lands on the same card
	var focusedCard by rememberSaveable { mutableStateOf<String?>(null) }
	val restoredCard = remember { focusedCard }
	// Going up from the first row lands on the buttons instead of skipping them for the toolbar
	val requestsButton = remember { FocusRequester() }

	Row(
		verticalAlignment = Alignment.Top,
		modifier = Modifier
			.fillMaxWidth()
			.height(104.dp)
			.padding(horizontal = PagePadding),
	) {
		SeerrMediaInfo(
			media = shownMedia,
			modifier = Modifier.weight(1f),
		)

		Spacer(Modifier.width(24.dp))

		Button(onClick = onRequests, modifier = Modifier.focusRequester(requestsButton)) {
			Text(stringResource(R.string.jc_seerr_my_requests), fontSize = 12.sp)
		}

		Spacer(Modifier.width(8.dp))

		Button(onClick = onSearch) {
			Icon(
				imageVector = ImageVector.vectorResource(R.drawable.ic_search),
				contentDescription = null,
				modifier = Modifier.size(15.dp),
			)
			Spacer(Modifier.width(8.dp))
			Text(stringResource(R.string.jc_seerr_search), fontSize = 12.sp)
		}
	}

	LazyColumn(
		contentPadding = PaddingValues(top = 4.dp, bottom = 27.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
		modifier = Modifier.fillMaxSize(),
	) {
		items(rows, key = { it.list }) { row ->
			val isFirstRow = row == rows.first()

			Column {
				Text(
					text = stringResource(row.list.title()),
					fontSize = 14.sp,
					fontWeight = FontWeight.SemiBold,
					color = Color.White,
					modifier = Modifier.padding(start = PagePadding, bottom = 6.dp),
				)

				LazyRow(
					contentPadding = PaddingValues(horizontal = PagePadding, vertical = 6.dp),
					horizontalArrangement = Arrangement.spacedBy(12.dp),
					modifier = Modifier
						.focusRestorer()
						.focusGroup()
						.then(
							if (isFirstRow) {
								Modifier.onPreviewKeyEvent { event ->
									val up = event.key == Key.DirectionUp
									if (up && event.type == KeyEventType.KeyDown) requestsButton.requestFocus()
									up
								}
							} else {
								Modifier
							}
						),
				) {
					itemsIndexed(row.items, key = { _, media -> "${media.type}-${media.id}" }) { index, media ->
						val cardKey = "${row.list}-${media.type}-${media.id}"
						val isInitialCard = when (restoredCard) {
							null -> isFirstRow && index == 0
							else -> cardKey == restoredCard
						}
						val focusModifier = when {
							isInitialCard -> Modifier
								.focusRequester(initialCard)
								.onPlaced {
									if (!initialFocusDone) {
										initialFocusDone = true
										initialCard.requestFocus()
									}
								}

							else -> Modifier
						}

						SeerrCard(
							media = media,
							modifier = focusModifier,
							onClick = { onClick(media) },
							onFocused = {
								focusedCard = cardKey
								onFocused(media)
								if (index >= row.items.size - LOAD_MORE_THRESHOLD) onNearEnd(row.list)
							},
						)
					}
				}
			}
		}
	}
}

@StringRes
private fun SeerrDiscoverList.title() = when (this) {
	SeerrDiscoverList.TRENDING -> R.string.jc_seerr_list_trending
	SeerrDiscoverList.POPULAR_MOVIES -> R.string.jc_seerr_list_popular_movies
	SeerrDiscoverList.POPULAR_SERIES -> R.string.jc_seerr_list_popular_series
	SeerrDiscoverList.UPCOMING_MOVIES -> R.string.jc_seerr_list_upcoming_movies
	SeerrDiscoverList.UPCOMING_SERIES -> R.string.jc_seerr_list_upcoming_series
}

@Composable
private fun SeerrMediaInfo(
	media: SeerrMedia?,
	modifier: Modifier = Modifier,
) {
	if (media == null) return

	val details = listOfNotNull(
		media.year,
		stringResource(
			when (media.type) {
				SeerrMediaType.MOVIE -> R.string.jc_seerr_type_movie
				SeerrMediaType.SERIES -> R.string.jc_seerr_type_series
			}
		),
		media.availability.label()?.let { stringResource(it) },
	).joinToString(" · ")

	Column(modifier = modifier) {
		Text(
			text = media.title,
			fontSize = 22.sp,
			fontWeight = FontWeight.Bold,
			color = Color.White,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
		Spacer(Modifier.height(2.dp))
		Text(
			text = details,
			fontSize = 11.sp,
			color = Color(0xFFD1D5DB),
			maxLines = 1,
		)
		media.overview?.let {
			Spacer(Modifier.height(6.dp))
			Text(
				text = it,
				fontSize = 12.sp,
				lineHeight = 16.sp,
				color = Color(0xFFD1D5DB),
				maxLines = 3,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

@Composable
fun SeerrPageMessage(
	@StringRes message: Int,
	@StringRes action: Int? = null,
	onAction: () -> Unit = {},
) {
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center,
		modifier = Modifier
			.fillMaxSize()
			.padding(PagePadding),
	) {
		Text(
			text = stringResource(message),
			fontSize = 16.sp,
			color = Color(0xFFD1D5DB),
		)

		if (action != null) {
			Spacer(Modifier.height(16.dp))
			Button(
				onClick = onAction,
				modifier = Modifier.autoFocus(),
			) {
				Text(stringResource(action))
			}
		}
	}
}
