package org.jellyfin.androidtv.data.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The way a user signs in to Seerr.
 */
@Serializable
enum class SeerrLoginMethod {
	/** The account of the media server Seerr is linked to. */
	SERVER_ACCOUNT,

	/** An account that only exists in Seerr, identified by its email address. */
	LOCAL,
}

/**
 * A Seerr connection saved for a user of this app. Only the session is stored, never the password.
 */
@Serializable
data class SeerrAccount(
	val url: String,
	val method: SeerrLoginMethod,
	val sessionCookie: String,
)

@Serializable
data class SeerrStatus(
	val version: String? = null,
)

@Serializable
data class SeerrPublicSettings(
	val initialized: Boolean = false,
	val localLogin: Boolean = false,
	val mediaServerLogin: Boolean = false,
	val cacheImages: Boolean = false,
	val movie4kEnabled: Boolean = false,
	val series4kEnabled: Boolean = false,
)

@Serializable
data class SeerrUser(
	val id: Int,
	val displayName: String? = null,
	val username: String? = null,
	val email: String? = null,
	val permissions: Int = 0,
) {
	val name: String? get() = displayName ?: username ?: email
}

@Serializable
data class SeerrMediaInfo(
	val status: Int = 1,
	// Field names fixed by the Seerr API
	@SerialName("jellyfinMediaId") val libraryItemId: String? = null,
	@SerialName("jellyfinMediaId4k") val libraryItem4kId: String? = null,
	val seasons: List<SeerrSeasonInfo> = emptyList(),
	val requests: List<SeerrRequestInfo> = emptyList(),
)

@Serializable
data class SeerrRequester(
	val id: Int = 0,
	val displayName: String? = null,
	val username: String? = null,
	val email: String? = null,
) {
	val name: String? get() = displayName ?: username ?: email
}

@Serializable
data class SeerrRequestedSeason(
	val seasonNumber: Int,
)

@Serializable
data class SeerrRequestInfo(
	val id: Int,
	val status: Int = 1,
	val requestedBy: SeerrRequester? = null,
	val seasons: List<SeerrRequestedSeason> = emptyList(),
)

@Serializable
data class SeerrSeasonInfo(
	val seasonNumber: Int,
	val status: Int = 1,
)

@Serializable
data class SeerrGenre(
	val name: String = "",
)

@Serializable
data class SeerrSeason(
	val seasonNumber: Int,
	val name: String? = null,
	val episodeCount: Int = 0,
)

/**
 * The details of a movie or a series. The two kinds share most fields, the others stay empty.
 */
@Serializable
data class SeerrDetailsResponse(
	val id: Int,
	val title: String? = null,
	val name: String? = null,
	val tagline: String? = null,
	val overview: String? = null,
	val posterPath: String? = null,
	val backdropPath: String? = null,
	val releaseDate: String? = null,
	val firstAirDate: String? = null,
	val runtime: Int? = null,
	val episodeRunTime: List<Int> = emptyList(),
	val voteAverage: Double = 0.0,
	val genres: List<SeerrGenre> = emptyList(),
	val seasons: List<SeerrSeason> = emptyList(),
	val mediaInfo: SeerrMediaInfo? = null,
)

@Serializable
internal data class SeerrRequestBody(
	val mediaType: String,
	val mediaId: Int,
	val seasons: List<Int>? = null,
)

@Serializable
data class SeerrResult(
	val id: Int,
	val mediaType: String? = null,
	val title: String? = null,
	val name: String? = null,
	val overview: String? = null,
	val posterPath: String? = null,
	val backdropPath: String? = null,
	val releaseDate: String? = null,
	val firstAirDate: String? = null,
	val mediaInfo: SeerrMediaInfo? = null,
)

@Serializable
data class SeerrResultPage(
	val page: Int = 1,
	val totalPages: Int = 1,
	val results: List<SeerrResult> = emptyList(),
)

@Serializable
internal data class SeerrServerAccountLogin(
	val username: String,
	val password: String,
)

@Serializable
internal data class SeerrLocalLogin(
	val email: String,
	val password: String,
)
