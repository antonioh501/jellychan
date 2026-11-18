package org.jellyfin.androidtv.ui.home

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnChildViewHolderSelectedListener
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.CustomMessage
import org.jellyfin.androidtv.constant.HomeSectionType
import org.jellyfin.androidtv.constant.LiveTvOption
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.data.repository.CustomMessageRepository
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.ui.GridButton
import org.jellyfin.androidtv.ui.browsing.CompositeClickedListener
import org.jellyfin.androidtv.ui.browsing.CompositeSelectedListener
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.itemhandling.refreshItem
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.playback.AudioEventListener
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.ui.presentation.CARD_FOCUS_INSET
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter
import org.jellyfin.androidtv.util.KeyProcessor
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.sdk.model.api.UserDataChangedMessage
import org.koin.android.ext.android.inject
import timber.log.Timber
import org.jellyfin.sdk.model.api.BaseItemDto
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class HomeRowsFragment : RowsSupportFragment(), AudioEventListener, View.OnKeyListener {
	private val api by inject<ApiClient>()
	private val backgroundService by inject<BackgroundService>()
	private val playbackManager by inject<PlaybackManager>()
	private val mediaManager by inject<MediaManager>()
	private val notificationsRepository by inject<NotificationsRepository>()
	private val userRepository by inject<UserRepository>()
	private val userPreferences by inject<UserPreferences>()
	private val userSettingPreferences by inject<UserSettingPreferences>()
	private val userViewsRepository by inject<UserViewsRepository>()
	private val dataRefreshService by inject<DataRefreshService>()
	private val customMessageRepository by inject<CustomMessageRepository>()
	private val navigationRepository by inject<NavigationRepository>()
	private val itemLauncher by inject<ItemLauncher>()
	private val keyProcessor by inject<KeyProcessor>()

	private val helper by lazy { HomeFragmentHelper(requireContext(), userRepository, userPreferences) }

	// Data
	private var currentItem: BaseRowItem? = null
	private var currentRow: ListRow? = null
	private var justLoaded = true

	// Special rows
	private val notificationsRow by lazy { NotificationsHomeFragmentRow(lifecycleScope, notificationsRepository) }
	private val nowPlaying by lazy { HomeFragmentNowPlayingRow(lifecycleScope, playbackManager, mediaManager) }

	/** JellyChan: what the top of the home shows for the selected card; null while the carousel is selected. */
	val homeFocus = MutableStateFlow<HomeFocus?>(null)

	/** JellyChan: backdrop of the selected card, set once the selection settles. */
	private var backgroundJob: Job? = null

	private var focusJob: Job? = null

	/** JellyChan: trailer of the selected card, started once the selection rests on it for a while. */
	private var trailerJob: Job? = null

	/** JellyChan: set by the home while its icon rail has the focus. */
	var railFocused = false

	/** JellyChan: selected row, the rows above it are hidden (unless the carousel is selected). */
	private var selectedRow = -1

	private companion object {
		const val ROW_FADE_MS = 150L
		const val FOCUS_RETRIES = 20
		val FOCUS_RETRY_DELAY = 100.milliseconds
		val BACKGROUND_DELAY = 250.milliseconds
		val BACKGROUND_RESTORE_DELAY = 600.milliseconds
		val TRAILER_DELAY = 2500.milliseconds
		val TRAILER_MAX_LENGTH = 5.minutes
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		// JellyChan: continuous scrolling. The carousel sits at the top of the screen; any other selected row sits on the
		// focus line, with the rows above it hidden so the top of the screen shows the selected card.
		verticalGridView?.apply {
			windowAlignment = BaseGridView.WINDOW_ALIGN_NO_EDGE
			windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
			windowAlignmentOffset = 0
			itemAlignmentOffset = 0
			itemAlignmentOffsetPercent = 0f
			// Called before the grid scrolls to the newly selected row, so the new offset is used for that scroll
			addOnChildViewHolderSelectedListener(object : OnChildViewHolderSelectedListener() {
				override fun onChildViewHolderSelected(
					parent: RecyclerView,
					child: RecyclerView.ViewHolder?,
					position: Int,
					subposition: Int,
				) = alignRow(position)
			})
			addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
				override fun onChildViewAttachedToWindow(view: View) {
					view.animate().cancel()
					view.alpha = rowAlpha(getChildAdapterPosition(view))
				}

				override fun onChildViewDetachedFromWindow(view: View) {
					view.animate().cancel()
					view.alpha = 1f
				}
			})
		}
	}

	private val spotlightPosition: Int
		get() {
			val rows = adapter ?: return -1
			return (0 until rows.size()).firstOrNull { rows.get(it) is HomeSpotlightRow } ?: -1
		}

	/** The carousel, and the rows above it (notifications, now playing), keep the top of the screen. */
	private fun isOnTop(position: Int) = position <= spotlightPosition

	private fun rowAlpha(position: Int): Float = when {
		selectedRow < 0 || position == RecyclerView.NO_POSITION -> 1f
		position < selectedRow && !isOnTop(selectedRow) -> 0f
		else -> 1f
	}

	private fun alignRow(position: Int) {
		val grid = verticalGridView ?: return
		val focusLine = (HomeFocusLine.value * resources.displayMetrics.density).roundToInt()
		val offset = if (position < 0 || isOnTop(position)) 0 else focusLine
		if (grid.windowAlignmentOffset != offset) grid.windowAlignmentOffset = offset

		selectedRow = position
		for (i in 0 until grid.childCount) {
			// The grid may call this during a layout, with a child slot still empty
			val child = grid.getChildAt(i) ?: continue
			val target = rowAlpha(grid.getChildAdapterPosition(child))
			if (child.alpha != target) child.animate().alpha(target).setDuration(ROW_FADE_MS).start()
		}
	}

	/**
	 * The home asks for the focus as soon as it is shown, but the rows may not be laid out yet (just loaded, or back from
	 * another screen with a row far down selected): the focus then stays on the home itself. Hand it to the rows once
	 * they can take it, unless the icon rail has it.
	 */
	private fun focusRows() {
		focusJob?.cancel()
		focusJob = lifecycleScope.launch {
			repeat(FOCUS_RETRIES) {
				val grid = verticalGridView ?: return@launch
				if (railFocused || grid.findFocus() != null && grid.findFocus() !== grid) return@launch
				grid.requestFocus()
				delay(FOCUS_RETRY_DELAY)
			}
		}
	}

	/** Backdrop of the selected card behind the top of the home, once the selection stops moving. */
	private fun showBackground(
		item: BaseItemDto?,
		wait: kotlin.time.Duration = BACKGROUND_DELAY,
		trailer: Boolean = true,
	) {
		backgroundJob?.cancel()
		stopTrailer()
		backgroundJob = lifecycleScope.launch {
			delay(wait)
			if (item == null) {
				backgroundService.clearBackgrounds()
			} else {
				backgroundService.setSpotlight(true)
				backgroundService.setFramed(true)
				backgroundService.setBackground(item)
				// JellyChan: after a few seconds on the same card its trailer takes the place of the backdrop, once
				if (trailer) startTrailer(item)
			}
		}
	}

	private fun startTrailer(item: BaseItemDto) {
		trailerJob = lifecycleScope.launch {
			delay(TRAILER_DELAY)
			val trailer = api.homeTrailerUrl(item) ?: return@launch
			backgroundService.setTrailer(trailer)
			withTimeoutOrNull(TRAILER_MAX_LENGTH) { backgroundService.trailerEnded.first { it == trailer } }
			if (backgroundService.trailerUrl.value == trailer) backgroundService.setTrailer(null)
		}
	}

	private fun stopTrailer() {
		if (trailerJob?.isActive == true || backgroundService.framed.value) backgroundService.setTrailer(null)
		trailerJob?.cancel()
		trailerJob = null
	}

	/** JellyChan: home sections the rows were built from, to rebuild them when the settings change. */
	private var loadedSections: List<HomeSectionType>? = null

	private var loadJob: Job? = null

	/** JellyChan: rebuilds the rows when the home sections changed (in the settings overlay, which keeps the home alive). */
	fun reloadIfSectionsChanged() {
		val sections = userSettingPreferences.activeHomesections
		if (loadedSections == null || sections == loadedSections) return
		Timber.i("Home sections changed, rebuilding the rows")
		loadRows()
	}

	private fun loadRows() {
		loadJob?.cancel()
		loadJob = lifecycleScope.launch(Dispatchers.IO) {
			val currentUser = withTimeout(30.seconds) {
				userRepository.currentUser.filterNotNull().first()
			}

			val homesections = userSettingPreferences.activeHomesections
			loadedSections = homesections

			// Make sure the rows are empty
			val rows = mutableListOf<HomeFragmentRow>()

			// JellyChan: spotlight carousel
			val spotlightRow = HomeSpotlightRow.load(api)
			// JellyChan: round genre pictures, last row of the home
			val genresRow = HomeGenresRow.load(api)

			// Check for coroutine cancellation
			if (!isActive) return@launch

			// Actually add the sections
			for (section in homesections) {
				when (section) {
					HomeSectionType.LATEST_MEDIA -> rows.add(helper.loadRecentlyAdded(userViewsRepository.views.first()))
					HomeSectionType.LIBRARY_TILES_SMALL -> rows.add(HomeFragmentViewsRow(small = false))
					HomeSectionType.LIBRARY_BUTTONS -> rows.add(HomeFragmentViewsRow(small = true))
					HomeSectionType.RESUME -> rows.add(helper.loadResumeVideo())
					HomeSectionType.RESUME_AUDIO -> rows.add(helper.loadResumeAudio())
					HomeSectionType.RESUME_BOOK -> Unit // Books are not (yet) supported
					HomeSectionType.ACTIVE_RECORDINGS -> rows.add(helper.loadLatestLiveTvRecordings())
					HomeSectionType.NEXT_UP -> rows.add(helper.loadNextUp())
					HomeSectionType.LIVE_TV -> if (currentUser.policy?.enableLiveTvAccess == true) {
						rows.add(HomeFragmentLiveTVRow(requireActivity(), userRepository))
						rows.add(helper.loadOnNow())
					}

					HomeSectionType.NONE -> Unit
				}

			}

			// Add sections to layout
			withContext(Dispatchers.Main) {
				// JellyChan: 16:9 cards in every row
				val cardPresenter = CardPresenter().apply { homeTileHeight = HOME_TILE_HEIGHT }

				// Add rows in order
				@Suppress("UNCHECKED_CAST")
				val rowsAdapter = adapter as MutableObjectAdapter<Row>
				rowsAdapter.clear()
				spotlightRow.addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
				notificationsRow.addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
				nowPlaying.addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
				rows.forEach { row -> row.addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter) }
				genresRow.addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)

				// Wire up Live TV sibling rows so the On Now row removes the buttons row when empty
				for (i in 0 until rowsAdapter.size()) {
					val listRow = rowsAdapter.get(i) as? ListRow ?: continue
					val itemAdapter = listRow.adapter as? ItemRowAdapter ?: continue
					if (itemAdapter.queryType == QueryType.LiveTvProgram && i > 0) {
						val previousRow = rowsAdapter.get(i - 1)
						if (previousRow != null) itemAdapter.setSiblingRow(previousRow)
					}
				}

				// JellyChan: the rows may load after the home asked for the focus
				focusRows()
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		adapter = MutableObjectAdapter<Row>(
			androidx.leanback.widget.ClassPresenterSelector().apply {
				addClassPresenter(ListRow::class.java, PositionableListRowPresenter().apply {
					keepRowHeight = true
					// JellyChan: rows start right of the icon rail (the rows already have the browse start padding)
					val railPadding = HomeContentStart - 38.dp - CARD_FOCUS_INSET
					startPadding = (railPadding.value * resources.displayMetrics.density).roundToInt()
					headerInset = (CARD_FOCUS_INSET.value * resources.displayMetrics.density).roundToInt()
				})
				// JellyChan: full screen spotlight carousel as first row
				addClassPresenter(HomeSpotlightRow::class.java, HomeSpotlightPresenter())
			}
		)

		loadRows()

		onItemViewClickedListener = CompositeClickedListener().apply {
			registerListener(ItemViewClickedListener())
			registerListener(notificationsRow::onItemClicked)
		}

		onItemViewSelectedListener = CompositeSelectedListener().apply {
			registerListener(ItemViewSelectedListener())
		}

		customMessageRepository.message
			.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
			.onEach { message ->
				when (message) {
					CustomMessage.RefreshCurrentItem -> refreshCurrentItem()
					else -> Unit
				}
			}.launchIn(lifecycleScope)

		lifecycleScope.launch {
			lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
				api.webSocket.subscribe<UserDataChangedMessage>()
					.onEach { refreshRows(force = true, delayed = false) }
					.launchIn(this)

				api.webSocket.subscribe<LibraryChangedMessage>()
					.onEach { refreshRows(force = true, delayed = false) }
					.launchIn(this)
			}
		}

		// Subscribe to Audio messages
		mediaManager.addAudioEventListener(this)
	}

	/**
	 * JellyChan: moves the selection of the focused row one card to the left. The Compose focus system around the
	 * fragment would otherwise take every left press and jump straight to the icon rail.
	 * Returns false on the first card (the rail takes the focus) or when no card row has the focus.
	 */
	fun moveLeftInSelectedRow(): Boolean {
		val grid = verticalGridView ?: return false
		val position = grid.selectedPosition.takeIf { it >= 0 } ?: return false
		val holder = getRowViewHolder(position) as? ListRowPresenter.ViewHolder ?: return false
		val row = holder.gridView ?: return false
		if (!row.hasFocus() || row.selectedPosition <= 0) return false
		row.setSelectedPositionSmooth(row.selectedPosition - 1)
		return true
	}

	/**
	 * JellyChan: gives the focus back to the selected card of the selected row (right from the icon rail). The focus
	 * search from the rail does not always reach it, e.g. the genre cards, which are Compose views.
	 */
	fun focusSelectedCard(): Boolean {
		val grid = verticalGridView ?: return false
		val position = grid.selectedPosition.takeIf { it >= 0 } ?: return grid.requestFocus()
		val holder = getRowViewHolder(position) as? ListRowPresenter.ViewHolder
		val row = holder?.gridView ?: return grid.requestFocus()
		val card = row.findViewHolderForAdapterPosition(row.selectedPosition.coerceAtLeast(0))?.itemView
		return card?.requestFocus() ?: row.requestFocus()
	}

	override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
		if (event?.action != KeyEvent.ACTION_UP) return false
		return keyProcessor.handleKey(keyCode, currentItem, activity)
	}

	override fun onResume() {
		super.onResume()

		// JellyChan: back from another screen, align the selected row again and bring back its backdrop
		verticalGridView?.post {
			val grid = verticalGridView ?: return@post
			if (grid.selectedPosition >= 0) alignRow(grid.selectedPosition)
		}
		focusRows()
		when (val focus = homeFocus.value) {
			is HomeFocus.Item -> showBackground(focus.item, BACKGROUND_RESTORE_DELAY)
			is HomeFocus.Genre -> showBackground(focus.sample, BACKGROUND_RESTORE_DELAY, trailer = false)
			null -> Unit
		}

		//React to deletion
		if (currentRow != null && currentItem != null && currentItem?.baseItem != null && currentItem!!.baseItem!!.id == dataRefreshService.lastDeletedItemId) {
			(currentRow!!.adapter as ItemRowAdapter).remove(currentItem)
			currentItem = null
			dataRefreshService.lastDeletedItemId = null
		}

		if (!justLoaded) {
			//Re-retrieve anything that needs it but delay slightly so we don't take away gui landing
			refreshCurrentItem()
			refreshRows()
		} else {
			justLoaded = false
		}

		// Update audio queue
		Timber.i("Updating audio queue in HomeFragment (onResume)")
		nowPlaying.update(requireContext(), adapter as MutableObjectAdapter<Row>)
	}

	override fun onQueueStatusChanged(hasQueue: Boolean) {
		if (activity == null || requireActivity().isFinishing) return

		Timber.i("Updating audio queue in HomeFragment (onQueueStatusChanged)")
		nowPlaying.update(requireContext(), adapter as MutableObjectAdapter<Row>)
	}

	private fun refreshRows(force: Boolean = false, delayed: Boolean = true) {
		lifecycleScope.launch(Dispatchers.IO) {
			if (delayed) delay(1.5.seconds)

			repeat(adapter.size()) { i ->
				val rowAdapter = (adapter[i] as? ListRow)?.adapter as? ItemRowAdapter
				if (force) rowAdapter?.Retrieve()
				else rowAdapter?.ReRetrieveIfNeeded()
			}
		}
	}

	private fun refreshCurrentItem() {
		val adapter = currentRow?.adapter as? ItemRowAdapter ?: return
		val item = currentItem ?: return

		Timber.i("Refresh item ${item.getFullName(requireContext())}")
		adapter.refreshItem(api, this, item)
	}

	override fun onPause() {
		super.onPause()

		// JellyChan: the next screen sets its own background
		backgroundJob?.cancel()
		stopTrailer()
		if (homeFocus.value != null) backgroundService.setSpotlight(false)
	}

	override fun onDestroy() {
		super.onDestroy()

		mediaManager.removeAudioEventListener(this)
	}

	private inner class ItemViewClickedListener : OnItemViewClickedListener {
		override fun onItemClicked(
			itemViewHolder: Presenter.ViewHolder?,
			item: Any?,
			rowViewHolder: RowPresenter.ViewHolder?,
			row: Row?,
		) {
			if (item is GridButton) {
				when (item.id) {
					LiveTvOption.LIVE_TV_GUIDE_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvGuide)
					LiveTvOption.LIVE_TV_SCHEDULE_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvSchedule)
					LiveTvOption.LIVE_TV_RECORDINGS_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvRecordings)
					LiveTvOption.LIVE_TV_SERIES_OPTION_ID -> navigationRepository.navigate(Destinations.liveTvSeriesRecordings)
				}
			}

			// JellyChan: all movies and series of a genre
			if (item is HomeGenre) {
				// Genres come without display preferences, which the grid needs: keep them per genre
				val genre = item.genre.copy(displayPreferencesId = item.genre.displayPreferencesId ?: item.genre.id.toString())
				navigationRepository.navigate(Destinations.libraryBrowser(genre))
				return
			}

			if (item !is BaseRowItem) return
			if (row !is ListRow) return
			@Suppress("UNCHECKED_CAST")
			itemLauncher.launch(item, row.adapter as MutableObjectAdapter<Any>, requireContext())
		}
	}

	private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
		override fun onItemSelected(
			itemViewHolder: Presenter.ViewHolder?,
			item: Any?,
			rowViewHolder: RowPresenter.ViewHolder?,
			row: Row?,
		) {
			// JellyChan: the spotlight row manages the background itself
			if (row is HomeSpotlightRow) {
				currentItem = null
				homeFocus.value = null
				backgroundJob?.cancel()
				trailerJob?.cancel()
				return
			}
			if (item is HomeGenre) {
				currentItem = null
				homeFocus.value = HomeFocus.Genre(item.genre.name.orEmpty(), item.sample)
				// JellyChan: a genre shows one of its titles as backdrop, never a trailer
				showBackground(item.sample, trailer = false)
			} else if (item !is BaseRowItem) {
				currentItem = null
				homeFocus.value = null
				showBackground(null)
			} else {
				currentItem = item
				currentRow = row as ListRow

				val itemRowAdapter = row.adapter as? ItemRowAdapter
				itemRowAdapter?.loadMoreItemsIfNeeded(itemRowAdapter.indexOf(item))

				// JellyChan: the top of the home shows the selected card over its backdrop
				homeFocus.value = item.baseItem?.let(HomeFocus::Item)
				showBackground(item.baseItem)
			}
		}
	}
}
