package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import android.view.View
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/** JellyChan: favorite toggle in the player, like the heart in the web player. */
class FavoriteAction(
	private val context: Context,
	private val glue: CustomPlaybackTransportControlGlue,
) : CustomAction(context, glue), KoinComponent {
	private val api by inject<ApiClient>()
	private val overrides = mutableMapOf<java.util.UUID, Boolean>()

	fun isFavorite(item: org.jellyfin.sdk.model.api.BaseItemDto?): Boolean =
		item != null && (overrides[item.id] ?: item.userData?.isFavorite == true)

	init {
		initializeWithIcon(R.drawable.jc_ic_heart_outline)
	}

	fun setFavorite(favorite: Boolean) {
		initializeWithIcon(if (favorite) R.drawable.jc_ic_heart_filled else R.drawable.jc_ic_heart_outline)
		glue.notifyActionChangedPublic(this)
	}

	override fun handleClickAction(
		playbackController: PlaybackController,
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
	) {
		val item = playbackController.currentlyPlayingItem ?: return
		val newValue = !isFavorite(item)
		MainScope().launch {
			try {
				val userData = withContext(Dispatchers.IO) {
					if (newValue) api.userLibraryApi.markFavoriteItem(itemId = item.id).content
					else api.userLibraryApi.unmarkFavoriteItem(itemId = item.id).content
				}
				overrides[item.id] = userData.isFavorite
				setFavorite(userData.isFavorite)
			} catch (error: Exception) {
				Timber.w(error, "JellyChan: unable to toggle favorite")
			}
		}
	}
}
