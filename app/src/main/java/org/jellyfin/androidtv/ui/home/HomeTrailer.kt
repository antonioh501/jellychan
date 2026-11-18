package org.jellyfin.androidtv.ui.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID

/** JellyChan: trailer url of each title already looked up (null: none), shared by the carousel and the home rows. */
private val trailerCache = mutableMapOf<UUID, String?>()

/**
 * JellyChan: muted trailer for the home background. First a video in the "backdrops" folder of the title (a theme
 * video for the server), then a local trailer. Episodes and seasons use the ones of their series.
 */
suspend fun ApiClient.homeTrailerUrl(item: BaseItemDto): String? {
	val id = when (item.type) {
		BaseItemKind.EPISODE, BaseItemKind.SEASON -> item.seriesId ?: item.id
		else -> item.id
	}
	if (trailerCache.containsKey(id)) return trailerCache[id]

	val url = try {
		withContext(Dispatchers.IO) {
			val video = libraryApi.getThemeVideos(itemId = id, inheritFromParent = true).content.items.firstOrNull()
				?: userLibraryApi.getLocalTrailers(itemId = id).content.firstOrNull()
			video?.let { "$baseUrl/Videos/${it.id}/stream?static=true&mediaSourceId=${it.id}&api_key=$accessToken" }
		}
	} catch (error: Exception) {
		Timber.w(error, "JellyChan: unable to get the trailer of %s", id)
		return null
	}
	trailerCache[id] = url
	return url
}
