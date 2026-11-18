package org.jellyfin.androidtv.ui.composable.compat

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jellyfin.androidtv.ui.browsing.DestinationFragmentView
import org.jellyfin.androidtv.ui.navigation.NavigationAction
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.koin.compose.koinInject

@Composable
fun AppNavigationHost(
	modifier: Modifier = Modifier,
	navigationRepository: NavigationRepository = koinInject(),
	onHostCreated: (FrameLayout, DestinationFragmentView) -> Unit = { _, _ -> },
) {
	val factory = remember { AppNavigationHostViewFactory(onHostCreated) }

	val canGoBack by remember {
		navigationRepository.currentAction.map { navigationRepository.canGoBack }.distinctUntilChanged()
	}.collectAsState(navigationRepository.canGoBack)

	BackHandler(canGoBack) { navigationRepository.goBack() }

	AndroidView(
		factory = factory,
		modifier = modifier,
	)

	LaunchedEffect(Unit) {
		navigationRepository.currentAction.collect { action ->
			when (action) {
				is NavigationAction.NavigateFragment -> factory.view.navigate(action)
				NavigationAction.GoBack -> factory.view.goBack()
				NavigationAction.Nothing -> Unit
			}
		}
	}
}

private class AppNavigationHostViewFactory(
	private val onHostCreated: (FrameLayout, DestinationFragmentView) -> Unit,
) : (Context) -> View {
	private var _view: DestinationFragmentView? = null

	val view get() = requireNotNull(_view)

	override operator fun invoke(
		context: Context
	): View {
		// JellyChan: the screens sit in a frame, so views can be placed next to them (the app rail)
		val view = DestinationFragmentView(context)
		_view = view
		return FrameLayout(context).also { frame ->
			frame.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
			onHostCreated(frame, view)
		}
	}
}
