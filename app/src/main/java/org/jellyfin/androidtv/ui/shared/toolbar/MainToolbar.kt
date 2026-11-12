package org.jellyfin.androidtv.ui.shared.toolbar

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.flow.filterNotNull
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.seerr.SeerrRepository
import org.jellyfin.androidtv.data.seerr.SeerrState
import org.jellyfin.androidtv.ui.NowPlayingComposable
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.ProfilePicture
import org.jellyfin.androidtv.ui.base.ProvideTextStyle
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.base.button.IconButton
import org.jellyfin.androidtv.ui.base.button.IconButtonDefaults
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

enum class MainToolbarActiveButton {
	User,
	Home,
	Discover,
	Search,

	None,
}

@Composable
fun MainToolbar(
	activeButton: MainToolbarActiveButton = MainToolbarActiveButton.None,
) {
	val userRepository = koinInject<UserRepository>()
	val api = koinInject<ApiClient>()

	// Prevent user image to disappear when signing out by skipping null values
	val currentUser by remember { userRepository.currentUser.filterNotNull() }.collectAsState(null)
	val userImage = remember(currentUser) { currentUser?.primaryImage?.getUrl(api) }

	MainToolbar(
		userImage = userImage,
		activeButton = activeButton,
	)
}

@Composable
private fun MainToolbar(
	userImage: String? = null,
	activeButton: MainToolbarActiveButton,
) {
	val focusRequester = remember { FocusRequester() }
	val navigationRepository = koinInject<NavigationRepository>()
	val mediaManager = koinInject<MediaManager>()
	val sessionRepository = koinInject<SessionRepository>()
	val seerrRepository = koinInject<SeerrRepository>()
	val seerrState by seerrRepository.state.collectAsState()
	val settingsViewModel = koinActivityViewModel<SettingsViewModel>()
	val activity = LocalActivity.current

	// JellyChan: active tab is a light grey pill, like the library tabs
	val activeButtonColors = ButtonDefaults.colors(
		containerColor = Color(0xFFD1D5DB),
		contentColor = Color(0xFF111827),
	)

	Toolbar(
		modifier = Modifier
			.focusRestorer(focusRequester)
			.focusGroup(),
		// JellyChan: higher than the upstream toolbar, centered in a thinner band
		raise = MainToolbarRaise,
		start = {
			// JellyChan: full app logo, the text is drawn live to stay sharp at this size
			Row(verticalAlignment = Alignment.CenterVertically) {
				Image(
					painter = painterResource(R.drawable.jc_logo_mark),
					contentDescription = null,
				)
				Spacer(Modifier.width(8.dp))
				Text(
					text = stringResource(R.string.app_name_release),
					fontSize = 17.sp,
					fontWeight = FontWeight.Bold,
					letterSpacing = (-0.3).sp,
					color = Color.White,
					maxLines = 1,
				)
				Spacer(Modifier.width(16.dp))
				ToolbarButtons {
					NowPlayingComposable(
						onFocusableChange = {},
					)
				}
			}
		},
		center = {
			ToolbarButtons(
				modifier = Modifier
					.focusRequester(focusRequester)
			) {
				ProvideTextStyle(JellyfinTheme.typography.default.copy(fontWeight = FontWeight.Medium)) {
					Button(
						onClick = {
							if (activeButton != MainToolbarActiveButton.Home) {
								navigationRepository.navigate(
									Destinations.home,
									replace = true,
								)
							}
						},
						colors = if (activeButton == MainToolbarActiveButton.Home) activeButtonColors else ButtonDefaults.colors(),
						// JellyChan: compact pill with icon, like the web tabs
						contentPadding = PaddingValues(horizontal = 15.dp, vertical = 6.dp),
						content = {
							Icon(
								imageVector = ImageVector.vectorResource(R.drawable.ic_house),
								contentDescription = null,
								modifier = Modifier.size(15.dp),
							)
							Spacer(Modifier.width(8.dp))
							Text(stringResource(R.string.lbl_home), fontSize = 12.sp)
						}
					)
					if (seerrState != SeerrState.NotConfigured) {
						Button(
							onClick = {
								if (activeButton != MainToolbarActiveButton.Discover) {
									navigationRepository.navigate(Destinations.seerrDiscover)
								}
							},
							colors = if (activeButton == MainToolbarActiveButton.Discover) activeButtonColors else ButtonDefaults.colors(),
							contentPadding = PaddingValues(horizontal = 15.dp, vertical = 6.dp),
							content = {
								Icon(
									imageVector = ImageVector.vectorResource(R.drawable.jc_ic_seerr),
									contentDescription = null,
									modifier = Modifier.size(15.dp),
								)
								Spacer(Modifier.width(8.dp))
								Text(stringResource(R.string.jc_seerr_discover), fontSize = 12.sp)
							}
						)
					}
					Button(
						onClick = {
							if (activeButton != MainToolbarActiveButton.Search) {
								navigationRepository.navigate(Destinations.search())
							}
						},
						colors = if (activeButton == MainToolbarActiveButton.Search) activeButtonColors else ButtonDefaults.colors(),
						contentPadding = PaddingValues(horizontal = 15.dp, vertical = 6.dp),
						content = {
							Icon(
								imageVector = ImageVector.vectorResource(R.drawable.ic_search),
								contentDescription = null,
								modifier = Modifier.size(15.dp),
							)
							Spacer(Modifier.width(8.dp))
							Text(stringResource(R.string.lbl_search), fontSize = 12.sp)
						}
					)
				}
			}
		},
		end = {
			ToolbarButtons {
				val userImagePainter = rememberAsyncImagePainter(userImage)
				val userImageState by userImagePainter.state.collectAsState()
				val userImageVisible = userImageState is AsyncImagePainter.State.Success

				// JellyChan: the user button lives on the right, next to settings
				IconButton(
					onClick = {
						if (activeButton != MainToolbarActiveButton.User) {
							mediaManager.clearAudioQueue()
							sessionRepository.destroyCurrentSession()

							// Open login activity
							activity?.startActivity(ActivityDestinations.startup(activity))
							activity?.finishAfterTransition()
						}
					},
					colors = if (activeButton == MainToolbarActiveButton.User) activeButtonColors else ButtonDefaults.colors(),
					contentPadding = if (userImageVisible) PaddingValues(3.dp) else IconButtonDefaults.ContentPadding,
				) {
					ProfilePicture(
						url = userImage,
						contentDescription = stringResource(R.string.lbl_switch_user),
						modifier = Modifier
							.aspectRatio(1f)
							.clip(IconButtonDefaults.Shape)
					)
				}

				IconButton(
					onClick = { settingsViewModel.show() },
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_settings),
						contentDescription = stringResource(R.string.lbl_settings),
					)
				}

				ToolbarClock()
			}
		}
	)
}
