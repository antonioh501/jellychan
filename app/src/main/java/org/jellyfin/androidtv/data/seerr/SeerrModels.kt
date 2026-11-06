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
