package org.jellyfin.androidtv.data.seerr

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

data class SeerrSeasonState(
	val number: Int,
	val name: String,
	val episodes: Int,
	val availability: SeerrAvailability,
) {
	/** Seasons somebody already asked for, or that are present, can't be requested again. */
	val requestable get() = availability == SeerrAvailability.UNKNOWN
}

enum class SeerrRequestStatus {
	PENDING,
	APPROVED,
	DECLINED,
	FAILED,
	COMPLETED,
}

data class SeerrRequest(
	val id: Int,
	val status: SeerrRequestStatus,
	val requester: String?,
	val seasons: List<Int>,
	val cancellable: Boolean,
) {
	/** Declined and failed requests do not block a new one. */
	val active get() = status != SeerrRequestStatus.DECLINED && status != SeerrRequestStatus.FAILED
}

data class SeerrDetails(
	val id: Int,
	val type: SeerrMediaType,
	val title: String,
	val year: String?,
	val tagline: String?,
	val overview: String?,
	val genres: List<String>,
	val runtimeMinutes: Int?,
	val rating: Double?,
	val posterUrl: String?,
	val backdropUrl: String?,
	val availability: SeerrAvailability,
	val libraryItemId: UUID?,
	val seasons: List<SeerrSeasonState>,
	val requests: List<SeerrRequest>,
) {
	/** A movie that is not present and that nobody asked for yet. */
	val movieRequestable
		get() = type == SeerrMediaType.MOVIE && availability == SeerrAvailability.UNKNOWN && requests.none { it.active }
}

enum class SeerrRequestResult {
	SUCCESS,

	/** The account may not request, or its quota is used up. */
	FORBIDDEN,
	FAILED,
}

/** A request of the signed in user, together with the title it is about. */
data class SeerrRequestEntry(
	val request: SeerrRequest,
	val media: SeerrMedia,
	val createdAt: String?,
)

data class SeerrRequestEntryPage(
	val items: List<SeerrRequestEntry>,
	val page: Int,
	val totalPages: Int,
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

	suspend fun details(type: SeerrMediaType, id: Int): SeerrDetails {
		val connection = connection()
		val details = try {
			connection.client.details(type.path, id, language())
		} catch (err: SeerrHttpException) {
			if (err.isUnauthorized) seerrRepository.onSessionExpired()
			throw err
		}

		val imageBase = connection.imageBase()
		val info = details.mediaInfo
		val canManage = connection.user.permissions and MANAGE_PERMISSIONS != 0
		val requests = info?.requests.orEmpty().map { request ->
			SeerrRequest(
				id = request.id,
				status = request.status.toRequestStatus(),
				requester = request.requestedBy?.name,
				seasons = request.seasons.map { it.seasonNumber }.sorted(),
				cancellable = canManage || request.requestedBy?.id == connection.user.id,
			)
		}
		val requestedSeasons = requests.filter { it.active }.flatMap { it.seasons }.toSet()

		return SeerrDetails(
			id = details.id,
			type = type,
			title = details.title ?: details.name ?: throw IOException("Seerr sent no title"),
			year = (details.releaseDate ?: details.firstAirDate)?.take(YEAR_LENGTH)?.takeIf { it.length == YEAR_LENGTH },
			tagline = details.tagline?.takeIf { it.isNotBlank() },
			overview = details.overview?.takeIf { it.isNotBlank() },
			genres = details.genres.map { it.name }.filter { it.isNotBlank() },
			runtimeMinutes = (details.runtime ?: details.episodeRunTime.firstOrNull())?.takeIf { it > 0 },
			rating = details.voteAverage.takeIf { it > 0.0 },
			posterUrl = details.posterPath?.let { "$imageBase/t/p/$POSTER_SIZE$it" },
			backdropUrl = details.backdropPath?.let { "$imageBase/t/p/$BACKDROP_SIZE$it" },
			availability = info?.availability ?: SeerrAvailability.UNKNOWN,
			libraryItemId = info?.run { libraryItemId?.toUUIDOrNull() ?: libraryItem4kId?.toUUIDOrNull() },
			seasons = details.seasons
				.filter { it.seasonNumber > 0 }
				.sortedBy { it.seasonNumber }
				.map { season ->
					SeerrSeasonState(
						number = season.seasonNumber,
						name = season.name?.takeIf { it.isNotBlank() } ?: "",
						episodes = season.episodeCount,
						availability = info?.seasons
							?.firstOrNull { it.seasonNumber == season.seasonNumber }
							?.status.toAvailability()
							?.takeIf { it != SeerrAvailability.UNKNOWN }
							?: if (season.seasonNumber in requestedSeasons) SeerrAvailability.PENDING else SeerrAvailability.UNKNOWN,
					)
				},
			requests = requests,
		)
	}

	/**
	 * Request a movie, or the given seasons of a series.
	 */
	suspend fun request(type: SeerrMediaType, id: Int, seasons: List<Int>? = null): SeerrRequestResult {
		val connection = connection()

		return try {
			connection.client.request(type.path, id, seasons)
			SeerrRequestResult.SUCCESS
		} catch (err: SeerrHttpException) {
			when (err.code) {
				// Somebody already asked for it: the page shows the current state
				HTTP_CONFLICT -> SeerrRequestResult.SUCCESS
				HTTP_UNAUTHORIZED -> {
					seerrRepository.onSessionExpired()
					SeerrRequestResult.FAILED
				}

				HTTP_FORBIDDEN -> SeerrRequestResult.FORBIDDEN
				else -> SeerrRequestResult.FAILED
			}
		} catch (_: IOException) {
			SeerrRequestResult.FAILED
		}
	}

	/**
	 * The requests of the signed in user. The server only lists ids, so the titles are loaded one by one;
	 * a title that can't be loaded is left out of the page.
	 */
	suspend fun myRequests(page: Int = 1): SeerrRequestEntryPage {
		val connection = connection()
		val canManage = connection.user.permissions and MANAGE_PERMISSIONS != 0
		val imageBase = connection.imageBase()

		val list = try {
			connection.client.requests(connection.user.id, page, REQUEST_PAGE_SIZE)
		} catch (err: SeerrHttpException) {
			if (err.isUnauthorized) seerrRepository.onSessionExpired()
			throw err
		}

		val entries = coroutineScope {
			list.results.map { item ->
				async {
					val media = item.media ?: return@async null
					val type = when (media.mediaType) {
						"movie" -> SeerrMediaType.MOVIE
						"tv" -> SeerrMediaType.SERIES
						else -> return@async null
					}

					val details = try {
						connection.client.details(type.path, media.tmdbId, language())
					} catch (_: IOException) {
						return@async null
					}

					SeerrRequestEntry(
						request = SeerrRequest(
							id = item.id,
							status = item.status.toRequestStatus(),
							requester = item.requestedBy?.name,
							seasons = item.seasons.map { it.seasonNumber }.sorted(),
							cancellable = canManage || item.requestedBy?.id == connection.user.id,
						),
						media = SeerrMedia(
							id = details.id,
							type = type,
							title = details.title ?: details.name ?: return@async null,
							year = (details.releaseDate ?: details.firstAirDate)?.take(YEAR_LENGTH)?.takeIf { it.length == YEAR_LENGTH },
							overview = details.overview?.takeIf { it.isNotBlank() },
							posterUrl = details.posterPath?.let { "$imageBase/t/p/$POSTER_SIZE$it" },
							backdropUrl = details.backdropPath?.let { "$imageBase/t/p/$BACKDROP_SIZE$it" },
							availability = media.status.toAvailability(),
							libraryItemId = details.mediaInfo?.run { libraryItemId?.toUUIDOrNull() ?: libraryItem4kId?.toUUIDOrNull() },
						),
						createdAt = item.createdAt?.take(DATE_LENGTH),
					)
				}
			}.awaitAll().filterNotNull()
		}

		return SeerrRequestEntryPage(entries, list.pageInfo.page, list.pageInfo.pages)
	}

	/**
	 * Take back a request.
	 */
	suspend fun cancelRequest(requestId: Int): SeerrRequestResult {
		val connection = connection()

		return try {
			connection.client.cancelRequest(requestId)
			SeerrRequestResult.SUCCESS
		} catch (err: SeerrHttpException) {
			when (err.code) {
				// Already gone
				HTTP_NOT_FOUND -> SeerrRequestResult.SUCCESS
				HTTP_UNAUTHORIZED -> {
					seerrRepository.onSessionExpired()
					SeerrRequestResult.FAILED
				}

				HTTP_FORBIDDEN -> SeerrRequestResult.FORBIDDEN
				else -> SeerrRequestResult.FAILED
			}
		} catch (_: IOException) {
			SeerrRequestResult.FAILED
		}
	}

	/**
	 * Titles to suggest after a movie or a series: the recommendations first, then the similar ones.
	 * Anything that can't be loaded is left out, a missing row is better than an error.
	 */
	suspend fun recommendations(type: SeerrMediaType, id: Int, exclude: (SeerrMedia) -> Boolean = { false }): List<SeerrMedia> {
		val connection = connection()
		val imageBase = connection.imageBase()

		return listOf("recommendations", "similar")
			.flatMap { kind ->
				try {
					connection.client.related(type.path, id, kind, language()).results
				} catch (err: SeerrHttpException) {
					if (err.isUnauthorized) seerrRepository.onSessionExpired()
					emptyList()
				} catch (_: IOException) {
					emptyList()
				}
			}
			.mapNotNull { it.toMedia(type, imageBase) }
			.filter { it.id != id && !exclude(it) }
			.distinctBy { it.id }
	}

	/**
	 * The titles a person appears in, the most voted first. The person is found by [tmdbId], or by [name]
	 * when the library does not know the id. Talk shows and news are left out, they are not roles.
	 * Anything that can't be loaded gives an empty list.
	 */
	suspend fun personTitles(tmdbId: Int?, name: String, exclude: (SeerrMedia) -> Boolean = { false }): List<SeerrMedia> {
		val connection = connection()
		val imageBase = connection.imageBase()

		try {
			val personId = tmdbId ?: connection.client.search(name, 1, language()).results
				.firstOrNull { it.mediaType == "person" && it.name.equals(name, ignoreCase = true) }
				?.id
				?: return emptyList()

			val credits = connection.client.personCredits(personId, language())

			return (credits.cast + credits.crew)
				.filter { it.genreIds.none { genre -> genre in NON_ROLE_GENRES } }
				.sortedByDescending { it.voteCount }
				.mapNotNull { it.toMedia(null, imageBase) }
				.filter { it.posterUrl != null && !exclude(it) }
				.distinctBy { it.type to it.id }
				.take(PERSON_TITLES_LIMIT)
		} catch (err: SeerrHttpException) {
			if (err.isUnauthorized) seerrRepository.onSessionExpired()
			return emptyList()
		} catch (_: IOException) {
			return emptyList()
		}
	}

	private fun connection() = seerrRepository.state.value as? SeerrState.Connected
		?: throw IOException("Not connected to Seerr")

	private fun SeerrState.Connected.imageBase() = when {
		settings.cacheImages -> account.url.trimEnd('/') + "/imageproxy/tmdb"
		else -> "https://image.tmdb.org"
	}

	private suspend fun load(
		fallbackType: SeerrMediaType?,
		request: suspend (SeerrClient) -> SeerrResultPage,
	): SeerrMediaPage {
		val connection = connection()

		val result = try {
			request(connection.client)
		} catch (err: SeerrHttpException) {
			if (err.isUnauthorized) seerrRepository.onSessionExpired()
			throw err
		}

		val imageBase = connection.imageBase()

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

	private val SeerrMediaInfo.availability get() = status.toAvailability()

	private fun Int.toRequestStatus() = when (this) {
		REQUEST_APPROVED -> SeerrRequestStatus.APPROVED
		REQUEST_DECLINED -> SeerrRequestStatus.DECLINED
		REQUEST_FAILED -> SeerrRequestStatus.FAILED
		REQUEST_COMPLETED -> SeerrRequestStatus.COMPLETED
		else -> SeerrRequestStatus.PENDING
	}

	private fun Int?.toAvailability() = when (this) {
		STATUS_PENDING -> SeerrAvailability.PENDING
		STATUS_PROCESSING -> SeerrAvailability.PROCESSING
		STATUS_PARTIAL -> SeerrAvailability.PARTIAL
		STATUS_AVAILABLE -> SeerrAvailability.AVAILABLE
		else -> SeerrAvailability.UNKNOWN
	}

	private val SeerrMediaType.path
		get() = when (this) {
			SeerrMediaType.MOVIE -> "movie"
			SeerrMediaType.SERIES -> "tv"
		}

	private companion object {
		const val YEAR_LENGTH = 4
		const val DATE_LENGTH = 10
		const val REQUEST_PAGE_SIZE = 20
		const val PERSON_TITLES_LIMIT = 40

		// TMDB genres: News, Reality, Talk
		val NON_ROLE_GENRES = setOf(10763, 10764, 10767)
		const val POSTER_SIZE = "w342"
		const val BACKDROP_SIZE = "w1280"

		const val HTTP_UNAUTHORIZED = 401
		const val HTTP_FORBIDDEN = 403
		const val HTTP_NOT_FOUND = 404
		const val HTTP_CONFLICT = 409

		// ADMIN and MANAGE_REQUESTS
		const val MANAGE_PERMISSIONS = 2 or 16

		const val REQUEST_APPROVED = 2
		const val REQUEST_DECLINED = 3
		const val REQUEST_FAILED = 4
		const val REQUEST_COMPLETED = 5

		const val STATUS_PENDING = 2
		const val STATUS_PROCESSING = 3
		const val STATUS_PARTIAL = 4
		const val STATUS_AVAILABLE = 5
	}
}
