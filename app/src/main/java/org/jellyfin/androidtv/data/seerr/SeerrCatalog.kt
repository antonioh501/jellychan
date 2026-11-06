package org.jellyfin.androidtv.data.seerr

import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import java.io.IOException
import java.util.Locale
import java.util.UUID

enum class SeerrMediaType {
	MOVIE,
	SERIES,
}

enum class SeerrAvailability {
	UNKNOWN,
	PENDING,
	PROCESSING,
	PARTIAL,
	AVAILABLE,
}

/**
 * The lists shown on the discover page.
 */
enum class SeerrDiscoverList(
	internal val path: String,
	internal val mediaType: SeerrMediaType? = null,
) {
	TRENDING("discover/trending"),
	POPULAR_MOVIES("discover/movies", SeerrMediaType.MOVIE),
	POPULAR_SERIES("discover/tv", SeerrMediaType.SERIES),
	UPCOMING_MOVIES("discover/movies/upcoming", SeerrMediaType.MOVIE),
	UPCOMING_SERIES("discover/tv/upcoming", SeerrMediaType.SERIES),
}

data class SeerrMedia(
	val id: Int,
	val type: SeerrMediaType,
	val title: String,
	val year: String?,
	val overview: String?,
	val posterUrl: String?,
	val backdropUrl: String?,
	val availability: SeerrAvailability,
	val libraryItemId: UUID?,
)

data class SeerrMediaPage(
	val items: List<SeerrMedia>,
	val page: Int,
	val totalPages: Int,
)

/**
 * Browse the movies and series known to the connected Seerr server.
 */
class SeerrCatalog(
	private val seerrRepository: SeerrRepository,
	private val language: () -> String = { Locale.getDefault().language },
) {
	suspend fun discover(list: SeerrDiscoverList, page: Int = 1) = load(list.mediaType) { client ->
		client.discover(list.path, page, language())
	}

	suspend fun search(query: String, page: Int = 1) = load(null) { client ->
		client.search(query, page, language())
	}

	private suspend fun load(
		fallbackType: SeerrMediaType?,
		request: suspend (SeerrClient) -> SeerrResultPage,
	): SeerrMediaPage {
		val connection = seerrRepository.state.value as? SeerrState.Connected
			?: throw IOException("Not connected to Seerr")

		val result = try {
			request(connection.client)
		} catch (err: SeerrHttpException) {
			if (err.isUnauthorized) seerrRepository.onSessionExpired()
			throw err
		}

		val imageBase = when {
			connection.settings.cacheImages -> connection.account.url.trimEnd('/') + "/imageproxy/tmdb"
			else -> "https://image.tmdb.org"
		}

		return SeerrMediaPage(
			items = result.results.mapNotNull { it.toMedia(fallbackType, imageBase) },
			page = result.page,
			totalPages = result.totalPages,
		)
	}

	private fun SeerrResult.toMedia(fallbackType: SeerrMediaType?, imageBase: String): SeerrMedia? {
		val type = when (mediaType) {
			"movie" -> SeerrMediaType.MOVIE
			"tv" -> SeerrMediaType.SERIES
			null -> fallbackType
			else -> null
		} ?: return null

		return SeerrMedia(
			id = id,
			type = type,
			title = title ?: name ?: return null,
			year = (releaseDate ?: firstAirDate)?.take(YEAR_LENGTH)?.takeIf { it.length == YEAR_LENGTH },
			overview = overview?.takeIf { it.isNotBlank() },
			posterUrl = posterPath?.let { "$imageBase/t/p/$POSTER_SIZE$it" },
			backdropUrl = backdropPath?.let { "$imageBase/t/p/$BACKDROP_SIZE$it" },
			availability = mediaInfo?.availability ?: SeerrAvailability.UNKNOWN,
			libraryItemId = mediaInfo?.run { libraryItemId?.toUUIDOrNull() ?: libraryItem4kId?.toUUIDOrNull() },
		)
	}

	private val SeerrMediaInfo.availability
		get() = when (status) {
			STATUS_PENDING -> SeerrAvailability.PENDING
			STATUS_PROCESSING -> SeerrAvailability.PROCESSING
			STATUS_PARTIAL -> SeerrAvailability.PARTIAL
			STATUS_AVAILABLE -> SeerrAvailability.AVAILABLE
			else -> SeerrAvailability.UNKNOWN
		}

	private companion object {
		const val YEAR_LENGTH = 4
		const val POSTER_SIZE = "w342"
		const val BACKDROP_SIZE = "w1280"

		const val STATUS_PENDING = 2
		const val STATUS_PROCESSING = 3
		const val STATUS_PARTIAL = 4
		const val STATUS_AVAILABLE = 5
	}
}
