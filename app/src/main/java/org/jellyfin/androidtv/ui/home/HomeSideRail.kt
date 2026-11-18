package org.jellyfin.androidtv.ui.home

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.LocalTextStyle
import org.jellyfin.androidtv.ui.base.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.seerr.SeerrRepository
import org.jellyfin.androidtv.data.seerr.SeerrState
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.ProfilePicture
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.settings.compat.SettingsViewModel
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel

/**
 * JellyChan: icon rail on the left of the home. User switch on top, the sections in the middle, the app mark at the
 * bottom. Icons are dim, the current section is white and the focused one is a light pill. While it has the focus the
 * rail opens into a dark panel over the home, with the name of each section (and of the user) next to its icon.
 */
@Composable
fun HomeSideRail(
	modifier: Modifier = Modifier,
) {
	val userRepository = koinInject<UserRepository>()
	val api = koinInject<ApiClient>()
	val navigationRepository = koinInject<NavigationRepository>()
	val mediaManager = koinInject<MediaManager>()
	val sessionRepository = koinInject<SessionRepository>()
	val seerrRepository = koinInject<SeerrRepository>()
	val seerrState by seerrRepository.state.collectAsState()
	val settingsViewModel = koinActivityViewModel<SettingsViewModel>()
	val activity = LocalActivity.current

	// Prevent user image to disappear when signing out by skipping null values
	val currentUser by remember { userRepository.currentUser.filterNotNull() }.collectAsState(null)
	val userImage = remember(currentUser) { currentUser?.primaryImage?.getUrl(api) }

	val userViewsRepository = koinInject<UserViewsRepository>()
	val itemLauncher = koinInject<ItemLauncher>()
	val libraries by remember {
		userViewsRepository.views.map { views -> views.filter { userViewsRepository.isSupported(it.collectionType) } }
	}.collectAsState(emptyList())

	val homeFocusRequester = remember { FocusRequester() }
	var expanded by remember { mutableStateOf(false) }
	val panelWidth by animateDpAsState(if (expanded) RailExpandedWidth else HomeContentStart, tween(220), label = "RailWidth")
	val panelAlpha by animateFloatAsState(if (expanded) 1f else 0f, tween(220), label = "RailPanel")

	Box(
		modifier = modifier
			.fillMaxHeight()
			.width(panelWidth)
			.drawBehind { drawRect(PanelColor, alpha = panelAlpha) },
	) {
		Column(
			verticalArrangement = Arrangement.spacedBy(6.dp),
			modifier = Modifier
				.fillMaxHeight()
				.padding(start = RailItemStart, top = 22.dp, bottom = 22.dp)
				.onFocusChanged { expanded = it.hasFocus }
				.focusRestorer(homeFocusRequester)
				.focusGroup(),
		) {
			val userImagePainter = rememberAsyncImagePainter(userImage)
			val userImageState by userImagePainter.state.collectAsState()
			val userImageVisible = userImageState is AsyncImagePainter.State.Success

			RailItem(
				label = currentUser?.name.orEmpty(),
				expanded = expanded,
				onClick = {
					mediaManager.clearAudioQueue()
					sessionRepository.destroyCurrentSession()

					// Open login activity
					activity?.startActivity(ActivityDestinations.startup(activity))
					activity?.finishAfterTransition()
				},
			) {
				ProfilePicture(
					url = userImage,
					contentDescription = stringResource(R.string.lbl_switch_user),
					modifier = Modifier
						.padding(if (userImageVisible) 3.dp else 9.dp)
						.aspectRatio(1f)
						.clip(CircleShape)
				)
			}

			Spacer(Modifier.weight(1f))

			RailIcon(R.drawable.ic_search, R.string.lbl_search, expanded) {
				navigationRepository.navigate(Destinations.search())
			}
			RailIcon(R.drawable.ic_house, R.string.lbl_home, expanded, Modifier.focusRequester(homeFocusRequester), active = true) {}
			if (seerrState != SeerrState.NotConfigured) {
				RailIcon(R.drawable.jc_ic_seerr, R.string.jc_seerr_discover, expanded) {
					navigationRepository.navigate(Destinations.seerrDiscover)
				}
			}
			// The user's libraries, right below Discover
			libraries.forEach { library ->
				RailItem(label = library.name.orEmpty(), expanded = expanded, onClick = { itemLauncher.launchUserView(library) }) {
					Icon(
						imageVector = ImageVector.vectorResource(library.collectionType.railIcon()),
						contentDescription = library.name,
						modifier = Modifier.size(20.dp),
					)
				}
			}
			RailIcon(R.drawable.ic_settings, R.string.lbl_settings, expanded) { settingsViewModel.show() }

			Spacer(Modifier.weight(1.4f))

			Image(
				painter = painterResource(R.drawable.jc_logo_mark),
				contentDescription = null,
				modifier = Modifier
					.padding(start = 6.dp)
					.size(30.dp),
			)
		}
	}
}

@Composable
private fun RailIcon(
	icon: Int,
	label: Int,
	expanded: Boolean,
	modifier: Modifier = Modifier,
	active: Boolean = false,
	onClick: () -> Unit,
) = RailItem(
	label = stringResource(label),
	expanded = expanded,
	active = active,
	modifier = modifier,
	onClick = onClick,
) {
	Icon(
		imageVector = ImageVector.vectorResource(icon),
		contentDescription = stringResource(label),
		modifier = Modifier.size(20.dp),
	)
}

/** A rail entry: a 42dp icon, with its label on the right while the rail is open. The focused entry is a light pill. */
@Composable
private fun RailItem(
	label: String,
	expanded: Boolean,
	modifier: Modifier = Modifier,
	active: Boolean = false,
	onClick: () -> Unit,
	icon: @Composable () -> Unit,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val width by animateDpAsState(if (expanded) RailItemExpandedWidth else RailItemSize, tween(220), label = "RailItemWidth")
	val labelAlpha by animateFloatAsState(if (expanded) 1f else 0f, tween(if (expanded) 260 else 120), label = "RailLabel")
	val content = when {
		focused -> JellyfinTheme.colorScheme.onButtonFocused
		active || expanded -> Color.White
		else -> Color(0xFF9CA3AF)
	}

	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = modifier
			.width(width)
			.height(RailItemSize)
			.clip(CircleShape)
			.background(if (focused) JellyfinTheme.colorScheme.buttonFocused else Color.Transparent)
			.clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
	) {
		CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(color = content)) {
			Box(contentAlignment = Alignment.Center, modifier = Modifier.size(RailItemSize)) { icon() }
		}
		Text(
			text = label,
			maxLines = 1,
			softWrap = false,
			overflow = TextOverflow.Clip,
			style = JellyfinTheme.typography.default.copy(
				fontSize = 15.sp,
				fontWeight = if (active || focused) FontWeight.Medium else FontWeight.Normal,
				color = content,
			),
			modifier = Modifier
				.padding(start = 8.dp, end = 16.dp)
				.graphicsLayer { alpha = labelAlpha },
		)
	}
}

/** Icon of a library in the rail, by the kind of media it holds. */
private fun CollectionType?.railIcon(): Int = when (this) {
	CollectionType.MOVIES -> R.drawable.ic_movie
	CollectionType.TVSHOWS -> R.drawable.ic_tv
	CollectionType.MUSIC -> R.drawable.ic_music_album
	CollectionType.LIVETV -> R.drawable.ic_tv_guide
	CollectionType.PHOTOS, CollectionType.HOMEVIDEOS -> R.drawable.ic_photos
	CollectionType.BOXSETS -> R.drawable.ic_clapperboard
	else -> R.drawable.ic_folder
}

private val RailItemSize = 42.dp
private val RailItemStart = (HomeRailWidth - RailItemSize) / 2
private val RailItemExpandedWidth = 168.dp
private val RailExpandedWidth = RailItemStart * 2 + RailItemExpandedWidth

/** Dark panel behind the open rail: sharp edges, the same transparency all over. */
private val PanelColor = Color(0xF20B111E)
