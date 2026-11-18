package org.jellyfin.androidtv.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jellyfin.androidtv.ui.presentation.CARD_FOCUS_INSET
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.fragment.app.Fragment
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.content
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.ui.settings.compat.SettingsViewModel
import org.koin.android.ext.android.inject
import org.koin.compose.viewmodel.koinActivityViewModel

class HomeFragment : Fragment() {
	private val sessionRepository by inject<SessionRepository>()
	private val serverRepository by inject<ServerRepository>()
	private val notificationRepository by inject<NotificationsRepository>()

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View = content { HomeContent() }

	@androidx.compose.runtime.Composable
	private fun HomeContent() {
		val rowsFocusRequester = remember { FocusRequester() }
		LaunchedEffect(rowsFocusRequester) { rowsFocusRequester.requestFocus() }

		Box(modifier = Modifier.fillMaxSize()) {
			var rowsSupportFragment by remember { mutableStateOf<HomeRowsFragment?>(null) }
			val focus by (rowsSupportFragment?.homeFocus ?: flowOf(null)).collectAsState(null)

			// JellyChan: the settings are an overlay that keeps the home alive; rebuild the rows when the home sections
			// changed there
			val settingsViewModel = koinActivityViewModel<SettingsViewModel>()
			val settingsVisible by settingsViewModel.visible.collectAsState()
			LaunchedEffect(settingsVisible) {
				if (!settingsVisible) rowsSupportFragment?.reloadIfSectionsChanged()
			}

			// Info about the focused card, right above the selected row
			HomeFocusInfo(focus = focus, modifier = Modifier.align(Alignment.TopStart))

			// The leanback code has its own awful focus handling that doesn't work properly with Compose view interop. To
			// work around it, focus may only leave the rows to the left (the rail); when it does, the leanback focus is
			// cleared so it won't cause weird behavior when focus is regained
			AndroidFragment<HomeRowsFragment>(
				modifier = Modifier
					.onPreviewKeyEvent { event ->
						// JellyChan: left moves to the previous card first, the rail only from the first card
						event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft &&
							rowsSupportFragment?.moveLeftInSelectedRow() == true
					}
					.focusGroup()
					.focusRequester(rowsFocusRequester)
					.focusProperties {
						onExit = {
							if (requestedFocusDirection != FocusDirection.Left) cancelFocusChange()
							else rowsSupportFragment?.verticalGridView?.clearFocus()
						}
					}
					.fillMaxSize(),
				maxLifecycle = Lifecycle.State.RESUMED,
				onUpdate = { fragment ->
					rowsSupportFragment = fragment
				}
			)

			// JellyChan: cards scrolled to the left fade into the navy base on the content line (the left edge of the
			// selection border), so nothing runs under the rail icons. Same colors as the base behind it, shown only over
			// the rows (the carousel has its own veil). Drawn in Compose: the leanback fading edge renders the row in
			// software and crashes on hardware bitmaps.
			val rowFadeAlpha by animateFloatAsState(if (focus != null) 1f else 0f, tween(300), label = "RowFade")
			Box(
				modifier = Modifier
					.fillMaxHeight()
					.width(RowFadeEnd)
					.graphicsLayer {
						alpha = rowFadeAlpha
						compositingStrategy = CompositingStrategy.Offscreen
					}
					.drawBehind {
						drawRect(RowFadeBase)
						drawRect(
							Brush.horizontalGradient(
								0.0f to Color.Black,
								RowFadeSolid / RowFadeEnd to Color.Black,
								1.0f to Color.Transparent,
							),
							blendMode = BlendMode.DstIn,
						)
					}
			)

			HomeSideRail(
				modifier = Modifier
					// JellyChan: right from the rail goes back to the selected card of the rows
					.onPreviewKeyEvent { event ->
						if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionRight) return@onPreviewKeyEvent false
						val rows = rowsSupportFragment ?: return@onPreviewKeyEvent false
						rowsFocusRequester.requestFocus()
						rows.focusSelectedCard()
						true
					}
					.align(Alignment.CenterStart)
					.onFocusChanged { rowsSupportFragment?.railFocused = it.hasFocus },
			)
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		sessionRepository.currentSession
			.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
			.map { session ->
				if (session == null) null
				else serverRepository.getServer(session.serverId)
			}
			.onEach { server ->
				notificationRepository.updateServerNotifications(server)
			}
			.launchIn(viewLifecycleOwner.lifecycleScope)
	}
}

/** JellyChan: the rows fade out from the card start (the content line) to just past the rail icons. */
private val RowFadeEnd = HomeContentStart - CARD_FOCUS_INSET
private val RowFadeSolid = RowFadeEnd - 10.dp

/** Same vertical gradient as the navy base of the home backdrop. */
private val RowFadeBase = Brush.verticalGradient(
	0.0f to Color(0xFF1B2436),
	0.55f to Color(0xFF111827),
	1.0f to Color(0xFF111827),
)
