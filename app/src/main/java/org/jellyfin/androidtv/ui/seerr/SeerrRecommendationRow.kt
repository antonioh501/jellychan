package org.jellyfin.androidtv.ui.seerr

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.data.seerr.SeerrCatalog
import org.jellyfin.androidtv.data.seerr.SeerrMedia
import org.jellyfin.androidtv.data.seerr.SeerrMediaType
import org.jellyfin.androidtv.data.seerr.SeerrRepository
import org.jellyfin.androidtv.data.seerr.SeerrState
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.browsing.BrowsingUtils
import org.jellyfin.androidtv.ui.card.JcPressEffect
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.koin.core.context.GlobalContext
import timber.log.Timber
import java.io.IOException

/**
 * Suggestions from Seerr for the page of a movie or a series of the library. Titles that the library
 * already suggests are left out, so nothing is shown twice.
 */
object SeerrRecommendationRow {
	private const val TMDB_PROVIDER = "Tmdb"

	@JvmStatic
	fun add(fragment: Fragment, adapter: MutableObjectAdapter<Row>, item: BaseItemDto) {
		val type = when (item.type) {
			BaseItemKind.MOVIE -> SeerrMediaType.MOVIE
			BaseItemKind.SERIES -> SeerrMediaType.SERIES
			else -> return
		}
		val tmdbId = item.providerIds?.get(TMDB_PROVIDER)?.toIntOrNull() ?: return

		val koin = GlobalContext.get()
		val repository = koin.get<SeerrRepository>()
		if (repository.state.value !is SeerrState.Connected) return

		val catalog = koin.get<SeerrCatalog>()
		val api = koin.get<ApiClient>()

		fragment.lifecycleScope.launch {
			val media = try {
				withContext(Dispatchers.IO) {
					val library = librarySuggestions(api, item)

					catalog.recommendations(type, tmdbId) { candidate ->
						candidate.libraryItemId == item.id ||
							candidate.libraryItemId in library.ids ||
							candidate.id in library.tmdbIds
					}
				}
			} catch (err: IOException) {
				Timber.w(err, "Unable to load the Seerr recommendations")
				return@launch
			} catch (err: SerializationException) {
				Timber.w(err, "Unable to read the Seerr recommendations")
				return@launch
			}

			if (media.isEmpty() || !fragment.isAdded) return@launch

			val rowAdapter = ArrayObjectAdapter(SeerrCardPresenter())
			media.forEach(rowAdapter::add)
			adapter.add(ListRow(HeaderItem(fragment.getString(R.string.jc_seerr_recommendations_from)), rowAdapter))
		}
	}

	@JvmStatic
	fun open(media: SeerrMedia) {
		GlobalContext.get().get<NavigationRepository>().openSeerrMedia(media)
	}

	private class LibrarySuggestions(
		val ids: Set<java.util.UUID>,
		val tmdbIds: Set<Int>,
	)

	private suspend fun librarySuggestions(api: ApiClient, item: BaseItemDto): LibrarySuggestions {
		val request = BrowsingUtils.createSimilarItemsRequest(item.id).copy(
			fields = ItemRepository.itemFields + ItemFields.PROVIDER_IDS,
		)

		val similar = try {
			api.libraryApi.getSimilarItems(request).content.items
		} catch (err: ApiClientException) {
			Timber.w(err, "Unable to load the similar items of the library")
			emptyList()
		}

		return LibrarySuggestions(
			ids = similar.map { it.id }.toSet(),
			tmdbIds = similar.mapNotNull { it.providerIds?.get(TMDB_PROVIDER)?.toIntOrNull() }.toSet(),
		)
	}
}

/**
 * A Seerr card inside a leanback row. It reacts to the focus and to OK like the other cards of the row.
 */
class SeerrCardPresenter : Presenter() {
	private class Wrapper(private val composeView: ComposeView) : FrameLayout(composeView.context) {
		var focusedState by mutableStateOf(false)
		private val pressEffect = JcPressEffect(composeView)

		init {
			isFocusable = true
			isFocusableInTouchMode = true
			descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
			addView(composeView)
		}

		override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
			super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
			focusedState = gainFocus
		}

		override fun setPressed(pressed: Boolean) {
			super.setPressed(pressed)
			pressEffect.setPressed(pressed)
		}

		override fun onDetachedFromWindow() {
			super.onDetachedFromWindow()
			pressEffect.reset()
		}

		// Hack to prevent Compose crash with leanback presenters
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			if (isAttachedToWindow) super.onMeasure(widthMeasureSpec, heightMeasureSpec)
			else setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
		}

		fun bind(media: SeerrMedia) = composeView.setContent {
			JellyfinTheme {
				// The size and the captions of the other cards of the library
				SeerrCardContent(
					media = media,
					focused = focusedState,
					width = 85.dp,
					titleSize = 12.sp,
					centered = true,
				)
			}
		}
	}

	private class ViewHolder(val wrapper: Wrapper) : Presenter.ViewHolder(wrapper)

	override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder =
		ViewHolder(Wrapper(ComposeView(parent.context)))

	override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
		if (viewHolder is ViewHolder && item is SeerrMedia) viewHolder.wrapper.bind(item)
	}

	override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) = Unit
}
