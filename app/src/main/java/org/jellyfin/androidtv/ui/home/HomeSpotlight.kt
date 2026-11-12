package org.jellyfin.androidtv.ui.home

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.leanback.widget.VerticalGridView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.databinding.ViewHomeSpotlightBinding
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.util.ImageHelper
import org.jellyfin.androidtv.util.PlaybackHelper
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * JellyChan home spotlight: a full screen carousel of random movies and series.
 * The backdrop (and a muted local trailer when available) is drawn by AppBackground in
 * spotlight mode, this row only shows logo, info and actions.
 */
class HomeSpotlightRow(val items: List<BaseItemDto>) : Row(), HomeFragmentRow {
	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		if (items.isNotEmpty()) rowsAdapter.add(this)
	}

	companion object {
		private const val ITEM_COUNT = 10

		suspend fun load(api: ApiClient): HomeSpotlightRow = withContext(Dispatchers.IO) {
			val items = try {
				api.itemsApi.getItems(
					includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
					recursive = true,
					sortBy = setOf(ItemSortBy.RANDOM),
					imageTypes = setOf(ImageType.BACKDROP),
					fields = setOf(ItemFields.OVERVIEW, ItemFields.GENRES),
					limit = ITEM_COUNT,
				).content.items
			} catch (error: Exception) {
				Timber.w(error, "JellyChan spotlight: unable to load items")
				emptyList()
			}
			HomeSpotlightRow(items)
		}
	}
}

class HomeSpotlightPresenter : RowPresenter() {
	init {
		headerPresenter = null
		selectEffectEnabled = false
	}

	override fun isUsingDefaultSelectEffect() = false

	override fun createRowViewHolder(parent: ViewGroup) = ViewHolder(HomeSpotlightView(parent.context))

	override fun onBindRowViewHolder(vh: RowPresenter.ViewHolder, item: Any) {
		super.onBindRowViewHolder(vh, item)
		if (item is HomeSpotlightRow) (vh.view as HomeSpotlightView).setItems(item.items)
	}
}

class HomeSpotlightView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : FrameLayout(context, attrs), KoinComponent {
	private val api by inject<ApiClient>()
	private val backgroundService by inject<BackgroundService>()
	private val imageHelper by inject<ImageHelper>()
	private val playbackHelper by inject<PlaybackHelper>()
	private val navigationRepository by inject<NavigationRepository>()

	private val binding = ViewHomeSpotlightBinding.inflate(LayoutInflater.from(context), this, true)

	private var items: List<BaseItemDto> = emptyList()
	private var index = 0
	private var active = false
	private var scope: CoroutineScope? = null
	private var cycleJob: Job? = null
	private val trailerCache = mutableMapOf<UUID, String?>()

	init {
		clipChildren = false
		clipToPadding = false
		descendantFocusability = FOCUS_AFTER_DESCENDANTS

		binding.jcMeta.ratingsOnly = true
		binding.jcPlay.setOnClickListener { current()?.let { playbackHelper.retrieveAndPlay(it.id, false, context) } }
		binding.jcDetails.setOnClickListener { current()?.let { navigationRepository.navigate(Destinations.itemDetails(it.id)) } }

		// Play icon: smaller and tinted like the label (dark on the light focused pill)
		val iconSize = (22 * resources.displayMetrics.density).toInt()
		binding.jcPlay.compoundDrawablesRelative[0]?.let { icon ->
			val d = icon.mutate()
			d.setBounds(0, 0, iconSize, iconSize)
			binding.jcPlay.setCompoundDrawablesRelative(d, null, null, null)
		}
		androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(
			binding.jcPlay,
			androidx.core.content.ContextCompat.getColorStateList(context, R.drawable.button_default_text),
		)

		// Left on the first action / right on the last action switches item (see dispatchKeyEvent)
	}

	private fun current() = items.getOrNull(index)

	// The carousel fills the whole first home page, from the toolbar to the bottom of the screen, so the rows below
	// it start off screen
	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val toolbarHeight = (HomeToolbarHeight.value * resources.displayMetrics.density).toInt()
		val height = resources.displayMetrics.heightPixels - toolbarHeight
		super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
	}

	override fun dispatchKeyEvent(event: KeyEvent): Boolean {
		if (event.action == KeyEvent.ACTION_DOWN && items.size > 1) {
			if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && binding.jcPlay.isFocused) {
				show(index - 1)
				return true
			}
			if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && binding.jcDetails.isFocused) {
				show(index + 1)
				return true
			}
		}
		return super.dispatchKeyEvent(event)
	}

	fun setItems(newItems: List<BaseItemDto>) {
		if (newItems == items) return
		items = newItems
		index = 0
		buildDots()
		show(0)
	}

	private fun buildDots() {
		binding.jcDots.removeAllViews()
		val size = (8 * resources.displayMetrics.density).toInt()
		val margin = (4 * resources.displayMetrics.density).toInt()
		repeat(items.size) {
			binding.jcDots.addView(View(context).apply {
				setBackgroundResource(R.drawable.jc_dot)
				layoutParams = LayoutParams(size, size).apply {
					marginStart = margin
					marginEnd = margin
				}
			})
		}
	}

	private fun show(newIndex: Int) {
		if (items.isEmpty()) return
		index = (newIndex + items.size) % items.size
		val item = items[index]

		val logo = imageHelper.getLogoImageUrl(item, 800)
		binding.jcLogo.isVisible = logo != null
		binding.jcTitle.isVisible = logo == null
		if (logo != null) binding.jcLogo.load(logo, null, null, 1.0, 0)
		binding.jcTitle.text = item.name

		binding.jcMeta.item = item
		binding.jcOverview.text = item.overview.orEmpty()
		binding.jcOverview.isVisible = !item.overview.isNullOrBlank()

		for (i in 0 until binding.jcDots.childCount) {
			val dot = binding.jcDots.getChildAt(i)
			dot.isActivated = i == index
			dot.alpha = if (i == index) 1f else 0.45f
		}

		if (active) startCycle()
	}

	private fun startCycle() {
		val item = current() ?: return
		backgroundService.setTrailer(null)
		backgroundService.setBackground(item)

		cycleJob?.cancel()
		cycleJob = scope?.launch {
			// Re-apply the backdrop once the home has settled (startup may clear it, and the details screen turns the
			// spotlight mode off while it is torn down, after the home is back)
			delay(0.6.seconds)
			backgroundService.setSpotlight(true)
			backgroundService.setBackground(item)
			// Give the backdrop a moment, then try a local trailer
			delay(1.9.seconds)
			val trailer = trailerUrlFor(item)
			if (trailer != null) {
				backgroundService.setTrailer(trailer)
				withTimeoutOrNull(150.seconds) { backgroundService.trailerEnded.first { it == trailer } }
			} else {
				delay(10.seconds)
			}
			if (items.size > 1) show(index + 1)
		}
	}

	private suspend fun trailerUrlFor(item: BaseItemDto): String? {
		if (trailerCache.containsKey(item.id)) return trailerCache[item.id]
		val url = try {
			val trailers = withContext(Dispatchers.IO) { api.userLibraryApi.getLocalTrailers(itemId = item.id).content }
			trailers.firstOrNull()?.let { trailer ->
				"${api.baseUrl}/Videos/${trailer.id}/stream?static=true&mediaSourceId=${trailer.id}&api_key=${api.accessToken}"
			}
		} catch (error: Exception) {
			Timber.w(error, "JellyChan spotlight: unable to get trailers for %s", item.id)
			null
		}
		trailerCache[item.id] = url
		return url
	}

	private fun setActive(value: Boolean) {
		if (value == active) return
		active = value
		backgroundService.setSpotlight(value)
		if (value) {
			startCycle()
		} else {
			cycleJob?.cancel()
			backgroundService.setTrailer(null)
		}
	}

	// Active while the focus is inside this view, or above it (toolbar) while it is on screen
	private val globalFocusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
		if (newFocus == null) return@OnGlobalFocusChangeListener
		val grid = findParentGrid()
		val inside = newFocus.isDescendantOf(this)
		val inRows = grid != null && newFocus.isDescendantOf(grid)
		when {
			inside -> setActive(true)
			inRows -> setActive(false)
			else -> Unit // toolbar: keep the current state
		}
	}

	private fun findParentGrid(): VerticalGridView? {
		var v = parent
		while (v != null) {
			if (v is VerticalGridView) return v
			v = v.parent
		}
		return null
	}

	private fun View.isDescendantOf(ancestor: View): Boolean {
		var v: View? = this
		while (v != null) {
			if (v === ancestor) return true
			v = v.parent as? View
		}
		return false
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		scope = MainScope()
		viewTreeObserver.addOnGlobalFocusChangeListener(globalFocusListener)
		if (hasFocus()) setActive(true)
	}

	override fun onDetachedFromWindow() {
		viewTreeObserver.removeOnGlobalFocusChangeListener(globalFocusListener)
		setActive(false)
		scope?.cancel()
		scope = null
		super.onDetachedFromWindow()
	}

	override fun onWindowVisibilityChanged(visibility: Int) {
		super.onWindowVisibilityChanged(visibility)
		// Stop the trailer when another screen covers the home (details, player...)
		if (visibility != VISIBLE) setActive(false)
		else if (hasFocus()) setActive(true)
	}
}
