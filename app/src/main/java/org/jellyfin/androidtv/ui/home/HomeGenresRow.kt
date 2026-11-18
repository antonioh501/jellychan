package org.jellyfin.androidtv.ui.home

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.findViewTreeCompositionContext
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.presentation.CARD_FOCUS_BORDER
import org.jellyfin.androidtv.ui.presentation.CARD_FOCUS_INSET
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.koin.compose.koinInject
import timber.log.Timber

/** A genre on the home, with a title of that genre whose backdrop is its picture and the home background. */
data class HomeGenre(val genre: BaseItemDto, val sample: BaseItemDto?)

/**
 * JellyChan: "All genres" row at the bottom of the home, round pictures with the genre name below. Opening one
 * shows all movies and series of that genre.
 */
class HomeGenresRow(private val genres: List<HomeGenre>) : HomeFragmentRow {
	override fun addToRowsAdapter(context: Context, cardPresenter: CardPresenter, rowsAdapter: MutableObjectAdapter<Row>) {
		if (genres.isEmpty()) return
		val adapter = ArrayObjectAdapter(HomeGenrePresenter()).apply { addAll(0, genres) }
		rowsAdapter.add(ListRow(HeaderItem(context.getString(R.string.jc_home_all_genres)), adapter))
	}

	companion object {
		private const val MAX_GENRES = 16
		private const val SAMPLE_CANDIDATES = 8

		suspend fun load(api: ApiClient): HomeGenresRow = coroutineScope {
			val genres = try {
				api.genresApi.getGenres(
					includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
					sortBy = setOf(ItemSortBy.SORT_NAME),
					limit = MAX_GENRES,
				).content.items
			} catch (error: Exception) {
				Timber.w(error, "JellyChan home: unable to load genres")
				emptyList()
			}

			val candidates = genres.map { genre ->
				async(Dispatchers.IO) {
					try {
						api.itemsApi.getItems(
							genreIds = setOf(genre.id),
							includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
							recursive = true,
							imageTypes = setOf(ImageType.BACKDROP),
							sortBy = setOf(ItemSortBy.RANDOM),
							fields = setOf(ItemFields.OVERVIEW),
							limit = SAMPLE_CANDIDATES,
						).content.items
					} catch (error: Exception) {
						Timber.w(error, "JellyChan home: unable to load titles for genre %s", genre.name)
						emptyList()
					}
				}
			}.awaitAll()

			// A different title for every genre when possible (a title often has several genres); genres without any
			// movie or series with a picture are left out
			// (genres with fewer titles choose first, they have less to choose from)
			val used = mutableSetOf<java.util.UUID>()
			val samples = mutableMapOf<Int, BaseItemDto>()
			candidates.indices.sortedBy { candidates[it].size }.forEach { index ->
				val items = candidates[index]
				val sample = items.firstOrNull { it.id !in used } ?: items.firstOrNull() ?: return@forEach
				used += sample.id
				samples[index] = sample
			}
			HomeGenresRow(genres.indices.mapNotNull { index -> samples[index]?.let { HomeGenre(genres[index], it) } })
		}
	}
}

class HomeGenrePresenter : Presenter() {
	override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
		val view = ComposeView(parent.context).apply {
			setParentCompositionContext(parent.findViewTreeCompositionContext())
			setViewTreeLifecycleOwner(parent.findViewTreeLifecycleOwner())
			setViewTreeSavedStateRegistryOwner(parent.findViewTreeSavedStateRegistryOwner())
			isFocusable = true
			isFocusableInTouchMode = true
		}
		return GenreViewHolder(view)
	}

	override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
		(viewHolder as? GenreViewHolder)?.bind(item as? HomeGenre)
	}

	override fun onUnbindViewHolder(viewHolder: ViewHolder) {
		(viewHolder as? GenreViewHolder)?.bind(null)
	}

	private class GenreViewHolder(composeView: ComposeView) : ViewHolder(composeView) {
		private val genre = MutableStateFlow<HomeGenre?>(null)
		private val focused = MutableStateFlow(false)

		// The card may get the focus back before it is bound (back from another screen)
		fun bind(item: HomeGenre?) {
			genre.value = item
			focused.value = item != null && view.isFocused
		}

		init {
			composeView.setContent {
				val item by genre.collectAsState()
				val isFocused by focused.collectAsState()
				item?.let { GenreCard(it, isFocused) }
			}
			composeView.onFocusChangeListener = android.view.View.OnFocusChangeListener { _, hasFocus -> focused.value = hasFocus }
		}
	}
}

@androidx.compose.runtime.Composable
private fun GenreCard(item: HomeGenre, focused: Boolean) {
	val api = koinInject<ApiClient>()
	val imageUrl = item.sample?.itemBackdropImages?.firstOrNull()?.getUrl(api, maxWidth = 400)

	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(8.dp),
		modifier = Modifier.width(HOME_GENRE_SIZE.dp + CARD_FOCUS_INSET * 2 + 20.dp),
	) {
		// Selection border a little away from the picture, like the cards (the space is always reserved)
		Box(
			modifier = Modifier
				.then(if (focused) Modifier.border(CARD_FOCUS_BORDER, Color.White, CircleShape) else Modifier)
				.padding(CARD_FOCUS_INSET)
				.size(HOME_GENRE_SIZE.dp)
				.clip(CircleShape)
				.background(Color(0xFF1D2635)),
		) {
			if (imageUrl != null) {
				AsyncImage(
					model = imageUrl,
					contentDescription = null,
					contentScale = ContentScale.Crop,
					modifier = Modifier.size(HOME_GENRE_SIZE.dp),
				)
			}
		}
		Text(
			text = item.genre.name.orEmpty(),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			textAlign = TextAlign.Center,
			style = JellyfinTheme.typography.default.copy(
				fontSize = 12.sp,
				fontWeight = FontWeight.SemiBold,
				color = if (focused) Color.White else Color(0xFFD1D5DB),
			),
		)
	}
}
