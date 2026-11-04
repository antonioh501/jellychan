package org.jellyfin.androidtv.ui.playback.overlay

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.util.PlaybackHelper
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * JellyChan: previous/next episode buttons also work when the episode is not part of
 * a queue (e.g. the previous episode, or playback started without queuing).
 */
object EpisodeNavigation : KoinComponent {
	private val api by inject<ApiClient>()
	private val playbackHelper by inject<PlaybackHelper>()

	@JvmStatic
	fun playAdjacent(context: Context, item: BaseItemDto, forward: Boolean) {
		val seriesId = item.seriesId ?: return
		MainScope().launch {
			try {
				val episodes = withContext(Dispatchers.IO) {
					api.tvShowsApi.getEpisodes(seriesId = seriesId, adjacentTo = item.id).content.items
				}
				val index = episodes.indexOfFirst { it.id == item.id }
				val target = when {
					index >= 0 -> episodes.getOrNull(if (forward) index + 1 else index - 1)
					forward -> episodes.lastOrNull()
					else -> episodes.firstOrNull()
				}
				if (target != null) playbackHelper.retrieveAndPlay(target.id, false, 0L, context)
			} catch (error: Exception) {
				Timber.w(error, "JellyChan: unable to get adjacent episode")
			}
		}
	}
}
