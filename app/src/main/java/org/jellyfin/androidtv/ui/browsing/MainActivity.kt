package org.jellyfin.androidtv.ui.browsing

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.integration.LeanbackChannelWorker
import org.jellyfin.androidtv.ui.InteractionTrackerViewModel
import org.jellyfin.androidtv.ui.background.AppBackground
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.ProvideLocalInteractionTracker
import org.jellyfin.androidtv.ui.composable.compat.AppNavigationHost
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.screensaver.InAppScreensaver
import org.jellyfin.androidtv.ui.settings.compat.MainActivitySettings
import org.jellyfin.androidtv.ui.startup.StartupActivity
import org.jellyfin.androidtv.util.AppLanguage
import org.jellyfin.androidtv.util.applyTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber
import android.graphics.Rect
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.recyclerview.widget.RecyclerView
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.fragment.app.FragmentManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.home.AppRailContentInset
import org.jellyfin.androidtv.ui.home.HomeSideRail
import org.jellyfin.androidtv.ui.home.RailSection
import org.jellyfin.androidtv.ui.home.appRailSection
import java.lang.ref.WeakReference
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.isVisible
import kotlin.math.roundToInt
import android.view.Gravity
import org.jellyfin.androidtv.ui.home.AppClockEnd
import org.jellyfin.androidtv.ui.home.AppClockTop
import org.jellyfin.androidtv.ui.home.showsAppClock
import org.jellyfin.androidtv.ui.shared.toolbar.ToolbarClock
import kotlinx.coroutines.flow.combine
import org.jellyfin.androidtv.ui.home.AppRailFadeEnd
import org.jellyfin.androidtv.ui.home.appRailFade

class MainActivity : FragmentActivity() {
	// JellyChan: interface language chosen in the app
	override fun attachBaseContext(newBase: Context) {
		super.attachBaseContext(AppLanguage.wrap(newBase))
	}

	private val navigationRepository by inject<NavigationRepository>()
	private val sessionRepository by inject<SessionRepository>()
	private val userRepository by inject<UserRepository>()
	private val interactionTrackerViewModel by viewModel<InteractionTrackerViewModel>()
	private val workManager by inject<WorkManager>()

	// JellyChan: app rail next to the screens that have no rail of their own (null: no rail on this screen)
	private val railSection = MutableStateFlow<RailSection?>(null)
	private val clockVisible = MutableStateFlow(false)
	private val railFade = MutableStateFlow(false)
	private val railFocusRequester = FocusRequester()
	private var lastContentFocus: WeakReference<View>? = null
	private var lastContentRect: Rect? = null
	private var leftKeyFocus: Pair<WeakReference<View>, Rect>? = null
	private var railView: View? = null
	private var screensHost: View? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		applyTheme()

		super.onCreate(savedInstanceState)

		if (!validateAuthentication()) return

		interactionTrackerViewModel.keepScreenOn.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
			.onEach { keepScreenOn ->
				if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
				else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
			}.launchIn(lifecycleScope)

		if (savedInstanceState == null && navigationRepository.canGoBack) navigationRepository.reset(clearHistory = true)

		navigationRepository.currentAction
			.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
			.onEach {
				interactionTrackerViewModel.notifyInteraction(canCancel = false, userInitiated = false)
			}.launchIn(lifecycleScope)

		// JellyChan: when the focus enters the app rail, remember where it was on the screen, to go back there
		window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { old, new ->
			val rail = railView ?: return@addOnGlobalFocusChangeListener
			if (old != null && new != null && new.isInside(rail) && !old.isInside(rail)) rememberContentFocus(old)
		}
		// JellyChan: follow the screen on top, for the app rail
		supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
			override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
				if (f.id != R.id.container) return
				railSection.value = f.appRailSection()
				railFade.value = f.appRailFade()
				clockVisible.value = f.showsAppClock()
			}
		}, true)

		setContent {
			JellyfinTheme {
				ProvideLocalInteractionTracker(
					interactionTracker = { interactionTrackerViewModel.notifyInteraction(false, userInitiated = true) }
				) {
					AppBackground()
					AppNavigationHost(
						navigationRepository = navigationRepository,
						modifier = Modifier.fillMaxSize(),
						onHostCreated = ::setupRail,
					)
					InAppScreensaver()
					MainActivitySettings()
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()

		if (!validateAuthentication()) return

		applyTheme()

		interactionTrackerViewModel.activityPaused = false
	}

	private fun validateAuthentication(): Boolean {
		if (sessionRepository.currentSession.value == null || userRepository.currentUser.value == null) {
			Timber.w("Activity ${this::class.qualifiedName} started without a session, bouncing to StartupActivity")
			startActivity(Intent(this, StartupActivity::class.java))
			finish()
			return false
		}

		return true
	}

	/**
	 * JellyChan: left from the left edge of a screen opens the app rail. Called when nothing handled the key: if the
	 * normal focus search would not move the focus to the left, the rail takes it.
	 */
	private fun moveFocusToRail(event: KeyEvent, focusBefore: View?): Boolean {
		if (railSection.value == null) return false
		if (event.action != KeyEvent.ACTION_DOWN || event.keyCode != KeyEvent.KEYCODE_DPAD_LEFT) return false
		val focused = currentFocus ?: return false
		if (focused !== focusBefore || railView?.hasFocus() == true) return false

		val next = focused.focusSearch(View.FOCUS_LEFT)
		if (next != null && next !== focused && next.isLeftOf(focused)) return false

		rememberContentFocus(focused)
		return runCatching { railFocusRequester.requestFocus() }.isSuccess
	}

	private fun rememberContentFocus(view: View) {
		val anchor = view.focusAnchor()
		lastContentFocus = WeakReference(anchor)
		lastContentRect = leftKeyFocus?.takeIf { (ref, _) -> ref.get() === view && anchor === view }?.second
	}

	/** A list (leanback grid or row) gives the focus back to its selected item: remember the list, not the item. */
	private fun View.focusAnchor(): View =
		generateSequence(parent) { it.parent }.filterIsInstance<RecyclerView>().firstOrNull() ?: this

	private fun View.isInside(ancestor: View): Boolean =
		this === ancestor || generateSequence(parent) { it.parent }.any { it === ancestor }

	private fun View.isLeftOf(other: View): Boolean {
		val rect = Rect().also { getGlobalVisibleRect(it) }
		val otherRect = Rect().also { other.getGlobalVisibleRect(it) }
		return rect.centerX() < otherRect.centerX()
	}

	/** JellyChan: gives the focus back to the screen, where it was before the rail took it. */
	private fun restoreContentFocus(): Boolean {
		val view = lastContentFocus?.get()?.takeIf { it.isAttachedToWindow && it.isShown }
		// A Compose screen gets the focus from its left edge at the height of the item that had it
		val rect = lastContentRect?.let { Rect(-1, it.top, 0, it.bottom) }
		if ((if (view != null && rect != null) view.requestFocus(View.FOCUS_RIGHT, rect) else view?.requestFocus()) != true) {
			screensHost?.requestFocus(View.FOCUS_RIGHT)
		}
		return true
	}

	/**
	 * JellyChan: the app rail is a view of its own next to the screens (not part of the Compose tree around them, which
	 * would pull the focus to it), shown on the screens that have no rail of their own.
	 */
	private fun setupRail(frame: FrameLayout, host: DestinationFragmentView) {
		screensHost = host
		val rail = ComposeView(this).apply {
			setContent {
				JellyfinTheme {
					val section by railSection.collectAsState()
					section?.let {
						HomeSideRail(
							active = it,
							focusRequester = railFocusRequester,
							modifier = Modifier.onPreviewKeyEvent { event ->
								// Right from the rail goes back to where the focus was on the screen
								if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionRight) return@onPreviewKeyEvent false
								restoreContentFocus()
							},
						)
					}
				}
			}
		}
		railView = rail
		frame.addView(rail, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT))

		// The app clock, top right of every screen but the players
		val clock = ComposeView(this).apply {
			setContent {
				JellyfinTheme {
					val visible by clockVisible.collectAsState()
					if (visible) ToolbarClock()
				}
			}
		}
		val density = resources.displayMetrics.density
		frame.addView(clock, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
			gravity = Gravity.TOP or Gravity.END
			topMargin = (AppClockTop.value * density).roundToInt()
			marginEnd = (AppClockEnd.value * density).roundToInt()
		})

		val inset = (AppRailContentInset.value * resources.displayMetrics.density).roundToInt()
		val fadeEnd = (AppRailFadeEnd.value * density).roundToInt()
		combine(railSection, railFade) { section, fade -> section to fade }.onEach { (section, fade) ->
			host.setPadding(if (section != null) inset else 0, 0, 0, 0)
			host.leftFade = if (section != null && fade) inset to fadeEnd else null
			rail.isVisible = section != null
		}.launchIn(lifecycleScope)
	}

	override fun onPause() {
		super.onPause()

		interactionTrackerViewModel.activityPaused = true
	}

	override fun onStop() {
		super.onStop()

		workManager.enqueue(OneTimeWorkRequestBuilder<LeanbackChannelWorker>().build())

		lifecycleScope.launch(Dispatchers.IO) {
			Timber.i("MainActivity stopped")
			sessionRepository.restoreSession(destroyOnly = true)
		}
	}

	// Forward key events to fragments

	private fun Fragment.onKeyEvent(keyCode: Int, event: KeyEvent?): Boolean {
		var result = childFragmentManager.fragments.any { it.onKeyEvent(keyCode, event) }
		if (!result && this is View.OnKeyListener) result = onKey(currentFocus, keyCode, event)
		return result
	}

	private fun onKeyEvent(keyCode: Int, event: KeyEvent?): Boolean = supportFragmentManager.fragments
		.any { it.onKeyEvent(keyCode, event) }

	override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyDown(keyCode, event)

	override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyUp(keyCode, event)

	override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean =
		onKeyEvent(keyCode, event) || super.onKeyUp(keyCode, event)

	/**
	 * JellyChan: when a screen is replaced, Compose may look for the next focus starting from a view that is no
	 * longer part of the window and throw. Drop that key press instead of crashing.
	 */
	// Activity.dispatchKeyEvent is public framework API; androidx.core only re-declares it as restricted
	@SuppressLint("RestrictedApi")
	override fun dispatchKeyEvent(event: KeyEvent): Boolean = try {
		// A view removed with its screen may still be marked as focused (seen leaving the home rows, which sit inside
		// Compose). Every key press would then fail: drop that stale focus so the window can focus the new screen.
		if (currentFocus?.isAttachedToWindow == false) {
			Timber.w("Cleared the focus of a view that is no longer on screen")
			window.decorView.clearFocus()
		}
		val focusBefore = currentFocus
		// Where the focus is inside the screen (a Compose screen only tells its focused item before the focus leaves)
		if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN && focusBefore != null) {
			leftKeyFocus = WeakReference(focusBefore) to Rect().also { focusBefore.getFocusedRect(it) }
		}
		val handled = super.dispatchKeyEvent(event)
		handled || moveFocusToRail(event, focusBefore)
	} catch (error: IllegalArgumentException) {
		if (error.message?.contains("descendant") != true) throw error
		Timber.w(error, "Ignored a key event that failed the focus search")
		true
	}
}
