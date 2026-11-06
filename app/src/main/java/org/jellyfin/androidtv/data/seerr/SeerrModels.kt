package org.jellyfin.androidtv.data.seerr

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
internal data class SeerrServerAccountLogin(
	val username: String,
	val password: String,
)

@Serializable
internal data class SeerrLocalLogin(
	val email: String,
	val password: String,
)
