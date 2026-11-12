package org.jellyfin.androidtv.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
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
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarOverlay
import org.koin.android.ext.android.inject

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
			// The leanback code has its own awful focus handling that doesn't work properly with Compose view inteop to workaround this
			// issue we add custom behavior that only allows focus exit when the current selected row is the first one. Additionally when
			// we do switch the focus, we reset the leanback state so it won't cause weird behavior when focus is regained
			var rowsSupportFragment by remember { mutableStateOf<HomeRowsFragment?>(null) }
			AndroidFragment<HomeRowsFragment>(
				modifier = Modifier
					.focusGroup()
					.focusRequester(rowsFocusRequester)
					.focusProperties {
						onExit = {
							val isFirstRowSelected = rowsSupportFragment?.selectedPosition?.let { it <= 0 } ?: false
							if (requestedFocusDirection != FocusDirection.Up || !isFirstRowSelected) {
								cancelFocusChange()
							} else {
								rowsSupportFragment?.selectedPosition = 0
								rowsSupportFragment?.verticalGridView?.clearFocus()
							}
						}
					}
					.fillMaxSize()
					.padding(top = HomeToolbarHeight),
				maxLifecycle = Lifecycle.State.RESUMED,
				onUpdate = { fragment ->
					rowsSupportFragment = fragment
				}
			)

			// JellyChan: the home moves by whole pages that start under the toolbar, so the toolbar needs no veil;
			// arrows show whether a page sits above or below the current one
			val arrows by (rowsSupportFragment?.pageArrows ?: flowOf(HomePageArrows())).collectAsState(HomePageArrows())
			HomeScrollHint(
				visible = arrows.down,
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.padding(bottom = 8.dp),
			)
			HomeScrollHint(
				visible = arrows.up,
				up = true,
				modifier = Modifier
					.align(Alignment.TopCenter)
					.padding(top = HomeToolbarHeight - 6.dp),
			)
			MainToolbarOverlay(MainToolbarActiveButton.Home, showVeil = false)
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
