package org.jellyfin.androidtv.ui.home

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.ListRow
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
import org.jellyfin.androidtv.ui.presentation.PositionableListRowPresenter
import org.jellyfin.androidtv.util.KeyProcessor
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.sdk.model.api.UserDataChangedMessage
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.util.IdentityHashMap
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

	/** JellyChan: arrows of the current home page (a page above / below it). */
	val pageArrows = kotlinx.coroutines.flow.MutableStateFlow(HomePageArrows())

	/** JellyChan: home page of each row (see HomeLayout.kt); rows that are not listed form a page of their own. */
	private val rowPages = IdentityHashMap<Row, Int>()

	/** JellyChan: last known distance of each row from the top of its page, for when the page start is off screen. */
	private val rowPageTops = IdentityHashMap<Row, Int>()

	private companion object {
		const val PAGE_FADE_MS = 200L
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		// JellyChan: the rows move by whole pages. The selected row is aligned at the top of the screen when it is the
		// first one of its page, otherwise where it sits within its page, so the page stays still while moving inside it.
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
				) = alignPage(position)
			})
			// Rows may change size or be laid out again (back from another screen, refreshed rows): keep the page where
			// it belongs once the grid is idle
			viewTreeObserver.addOnGlobalLayoutListener {
				measurePageTops()
				if (scrollState == RecyclerView.SCROLL_STATE_IDLE && selectedPosition >= 0) alignPage(selectedPosition, retry = false)
			}
			// A page reached from far away may be placed on a stale guess: once the scroll ends its rows are laid out, so
			// place it again on their real positions
			addOnScrollListener(object : RecyclerView.OnScrollListener() {
				override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
					if (newState == RecyclerView.SCROLL_STATE_IDLE && selectedPosition >= 0) alignPage(selectedPosition, retry = false)
				}
			})
			// Rows of the other pages stay hidden when they are laid out
			addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
				override fun onChildViewAttachedToWindow(view: View) {
					val position = getChildAdapterPosition(view)
					val key = currentPageKey
					view.animate().cancel()
					view.alpha = if (key == null || position == RecyclerView.NO_POSITION || pageKey(position) == key) 1f else 0f
				}

				override fun onChildViewDetachedFromWindow(view: View) {
					view.animate().cancel()
					view.alpha = 1f
				}
			})
		}
	}

	private fun pageOf(position: Int): Int? = when (val row = adapter?.get(position)) {
		is HomeSpotlightRow -> 1
		is Row -> rowPages[row]
		else -> null
	}

	// JellyChan: rows such as notifications or now playing may sit above the spotlight, so compare with its position
	private fun updateScrolledDown(position: Int) {
		val rows = adapter ?: return
		if (position !in 0 until rows.size()) return
		val spotlightPosition = (0 until rows.size()).firstOrNull { rows.get(it) is HomeSpotlightRow } ?: 0
		val key = pageKey(position)
		pageArrows.value = HomePageArrows(
			up = position > spotlightPosition,
			down = (position + 1 until rows.size()).any { pageKey(it) != key },
		)
	}

	/** Page shown right now; rows of the other pages are faded out so nothing peeks in from the next page. */
	private var currentPageKey: Int? = null

	/** Rows without a page form a page of their own. */
	private fun pageKey(position: Int): Int = pageOf(position) ?: (-1 - position)

	private fun alignPage(position: Int, retry: Boolean = true) {
		val grid = verticalGridView ?: return
		val count = adapter?.size() ?: return
		if (position !in 0 until count) return

		val page = pageOf(position)
		var first = position
		while (page != null && first > 0 && pageOf(first - 1) == page) first--

		val view = grid.layoutManager?.findViewByPosition(position)
		// Rows are aligned on their cards, below the row title
		val contentTop = view?.let(::rowContentTop) ?: 0
		// The rows may use the bottom padding of the grid, and the spacing under a row does not need to be on screen
		val available = grid.height - grid.paddingTop
		val rowHeight = view?.let { it.height - it.paddingBottom - rowContentPaddingBottom(it) } ?: 0

		val firstView = if (first != position) grid.layoutManager?.findViewByPosition(first) else view
		// Rows not laid out yet (back from another screen, or a page far away): try again once they are
		if (retry && (view == null || firstView == null)) {
			grid.post { if (grid.selectedPosition == position) alignPage(position, retry = false) }
		}

		measurePageTops()

		var rowTop = 0
		if (first != position && view != null) {
			val known = (adapter?.get(position) as? Row)?.let { rowPageTops[it] }
			rowTop = when {
				firstView != null -> view.top - firstView.top
				// While moving, trust where the row was last seen; once the grid settles (retry is off) the first row of
				// a page that fits the screen must be laid out, so if it is not that position was stale (rows refreshed
				// while away): put the row at the bottom, which brings the page start in and gets measured again
				known != null && retry -> known
				else -> available - rowHeight
			}
			// A page taller than the screen scrolls just enough to show the selected row whole
			if (rowTop + rowHeight > available) rowTop = (available - rowHeight).coerceAtLeast(0)
		}
		val offset = rowTop + contentTop
		if (grid.windowAlignmentOffset != offset) grid.windowAlignmentOffset = offset

		val key = pageKey(position)
		if (key != currentPageKey) {
			currentPageKey = key
			for (i in 0 until grid.childCount) {
				val child = grid.getChildAt(i)
				val childPosition = grid.getChildAdapterPosition(child)
				if (childPosition != RecyclerView.NO_POSITION) {
					child.animate().alpha(if (pageKey(childPosition) == key) 1f else 0f).setDuration(PAGE_FADE_MS).start()
				}
			}
		}
	}

	/**
	 * Remember where the rows of every page sit, measured while the first row of their page is laid out. All pages are
	 * measured, not only the current one: rows of a page may change size (loaded later, refreshed) while another page
	 * is shown.
	 */
	private fun measurePageTops() {
		val grid = verticalGridView ?: return
		val layoutManager = grid.layoutManager ?: return
		val count = adapter?.size() ?: return
		for (i in 0 until grid.childCount) {
			val child = grid.getChildAt(i)
			val position = grid.getChildAdapterPosition(child)
			if (position == RecyclerView.NO_POSITION || position >= count) continue
			val page = pageOf(position) ?: continue
			var first = position
			while (first > 0 && pageOf(first - 1) == page) first--
			val firstView = layoutManager.findViewByPosition(first) ?: continue
			val row = adapter?.get(position) as? Row ?: continue
			rowPageTops[row] = child.top - firstView.top
		}
	}

	/** Empty space under the cards and titles of a row, which may fall off screen. */
	private fun rowContentPaddingBottom(row: View): Int =
		row.findViewById<View>(androidx.leanback.R.id.row_content)?.paddingBottom ?: 0

	/** Distance from the top of a row view to its cards (the row title sits above them). */
	private fun rowContentTop(row: View): Int {
		var content: View? = row.findViewById(androidx.leanback.R.id.row_content) ?: return 0
		var top = 0
		while (content != null && content !== row) {
			top += content.top
			content = content.parent as? View
		}
		return top
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		adapter = MutableObjectAdapter<Row>(
			androidx.leanback.widget.ClassPresenterSelector().apply {
				addClassPresenter(ListRow::class.java, PositionableListRowPresenter().apply { keepRowHeight = true })
				// JellyChan: full screen spotlight carousel as first row
				addClassPresenter(HomeSpotlightRow::class.java, HomeSpotlightPresenter())
			}
		)

		lifecycleScope.launch(Dispatchers.IO) {
			val currentUser = withTimeout(30.seconds) {
				userRepository.currentUser.filterNotNull().first()
			}

			// JellyChan: My media, Next up and Continue watching first (second page), then the others
			val homesections = userSettingPreferences.activeHomesections.sortedBy {
				when (it) {
					HomeSectionType.LIBRARY_TILES_SMALL, HomeSectionType.LIBRARY_BUTTONS -> 0
					HomeSectionType.NEXT_UP -> 1
					HomeSectionType.RESUME -> 2
					else -> 3
				}
			}

			// Make sure the rows are empty
			val rows = mutableListOf<HomeFragmentRow>()
			// JellyChan: home page of each section's rows; the carousel is page 1
			val sectionPages = mutableListOf<Int>()
			var nextPage = 4

			// JellyChan: spotlight carousel
			val spotlightRow = HomeSpotlightRow.load(api)

			// Check for coroutine cancellation
			if (!isActive) return@launch

			// Actually add the sections
			for (section in homesections) {
				val rowCount = rows.size
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

				val page = when (section) {
					HomeSectionType.LIBRARY_TILES_SMALL, HomeSectionType.LIBRARY_BUTTONS,
					HomeSectionType.NEXT_UP, HomeSectionType.RESUME, HomeSectionType.RESUME_AUDIO -> 2
					HomeSectionType.LATEST_MEDIA -> 3
					else -> nextPage++
				}
				repeat(rows.size - rowCount) { sectionPages.add(page) }
			}

			// Add sections to layout
			withContext(Dispatchers.Main) {
				// JellyChan: compact cards, so three rows fit on one home page; larger ones on the page with the My media
				// pills
				val cardPresenter = CardPresenter().apply { homeTileHeight = HOME_TILE_HEIGHT }
				val largeCardPresenter = CardPresenter().apply { homeTileHeight = HOME_TILE_HEIGHT_LARGE }

				// Add rows in order
				@Suppress("UNCHECKED_CAST")
				val rowsAdapter = adapter as MutableObjectAdapter<Row>
				spotlightRow.addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
				notificationsRow.addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
				nowPlaying.addToRowsAdapter(requireContext(), cardPresenter, rowsAdapter)
				rows.forEachIndexed { index, row ->
					val before = (0 until rowsAdapter.size()).mapTo(java.util.Collections.newSetFromMap(IdentityHashMap())) { rowsAdapter.get(it) }
					val presenter = if (sectionPages[index] == 2) largeCardPresenter else cardPresenter
					row.addToRowsAdapter(requireContext(), presenter, rowsAdapter)
					for (i in 0 until rowsAdapter.size()) {
						val added = rowsAdapter.get(i) ?: continue
						if (added !in before) rowPages[added] = sectionPages[index]
					}
				}

				// Wire up Live TV sibling rows so the On Now row removes the buttons row when empty
				for (i in 0 until rowsAdapter.size()) {
					val listRow = rowsAdapter.get(i) as? ListRow ?: continue
					val itemAdapter = listRow.adapter as? ItemRowAdapter ?: continue
					if (itemAdapter.queryType == QueryType.LiveTvProgram && i > 0) {
						val previousRow = rowsAdapter.get(i - 1)
						if (previousRow != null) itemAdapter.setSiblingRow(previousRow)
					}
				}
			}
		}

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

	override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
		if (event?.action != KeyEvent.ACTION_UP) return false
		return keyProcessor.handleKey(keyCode, currentItem, activity)
	}

	override fun onResume() {
		super.onResume()

		// JellyChan: back from another screen the rows are laid out again, bring the page state in line with them
		verticalGridView?.post {
			val grid = verticalGridView ?: return@post
			val position = grid.selectedPosition
			if (position < 0) return@post
			currentPageKey = null
			alignPage(position)
			updateScrolledDown(position)
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
			val rows = adapter
			val position = rows?.let { r -> (0 until r.size()).firstOrNull { r.get(it) === row } } ?: selectedPosition
			updateScrolledDown(position)
			// JellyChan: the spotlight row manages the background itself
			if (row is HomeSpotlightRow) {
				currentItem = null
				return
			}
			if (item !is BaseRowItem) {
				currentItem = null
				//fill in default background
				backgroundService.clearBackgrounds()
			} else {
				currentItem = item
				currentRow = row as ListRow

				val itemRowAdapter = row.adapter as? ItemRowAdapter
				itemRowAdapter?.loadMoreItemsIfNeeded(itemRowAdapter.indexOf(item))

				// JellyChan: below the carousel the background stays solid instead of following the selection
				backgroundService.clearBackgrounds()
			}
		}
	}
}
