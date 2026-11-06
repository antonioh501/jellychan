package org.jellyfin.androidtv.data.seerr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import org.jellyfin.androidtv.auth.repository.UserRepository
import timber.log.Timber
import java.io.IOException
import java.util.UUID

sealed interface SeerrState {
	data object NotConfigured : SeerrState

	data class Connecting(val account: SeerrAccount) : SeerrState

	data class Connected(
		val account: SeerrAccount,
		val user: SeerrUser,
		val settings: SeerrPublicSettings,
		val version: String?,
	) : SeerrState

	/** The saved session is no longer valid, the user has to sign in again. */
	data class SessionExpired(val account: SeerrAccount) : SeerrState

	/** The server could not be reached, the saved session might still be valid. */
	data class Unavailable(val account: SeerrAccount) : SeerrState
}

enum class SeerrLoginResult {
	SUCCESS,
	INVALID_ADDRESS,
	UNREACHABLE,
	NOT_SEERR,
	METHOD_DISABLED,
	INVALID_CREDENTIALS,
}

/**
 * Connection to the Seerr server of the current user. Follows user switches and restores the saved
 * session, the password is only used while signing in.
 */
class SeerrRepository(
	private val userRepository: UserRepository,
	private val store: SeerrAccountStore,
	private val httpClient: OkHttpClient,
	scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
	private val _state = MutableStateFlow<SeerrState>(SeerrState.NotConfigured)
	val state: StateFlow<SeerrState> = _state.asStateFlow()

	private val currentUserId get() = userRepository.currentUser.value?.id

	init {
		scope.launch {
			userRepository.currentUser
				.map { it?.id }
				.distinctUntilChanged()
				.collectLatest(::restore)
		}
	}

	/**
	 * Try the saved session again.
	 */
	suspend fun refresh() = restore(currentUserId)

	suspend fun login(
		address: String,
		method: SeerrLoginMethod,
		username: String,
		password: String,
	): SeerrLoginResult {
		val userId = currentUserId ?: return SeerrLoginResult.UNREACHABLE
		val url = parseSeerrUrl(address) ?: return SeerrLoginResult.INVALID_ADDRESS
		val client = SeerrClient(url, httpClient)

		return try {
			val settings = try {
				if (client.status().version == null) return SeerrLoginResult.NOT_SEERR
				client.publicSettings()
			} catch (_: SeerrHttpException) {
				return SeerrLoginResult.NOT_SEERR
			} catch (_: SerializationException) {
				return SeerrLoginResult.NOT_SEERR
			}

			val methodEnabled = when (method) {
				SeerrLoginMethod.SERVER_ACCOUNT -> settings.mediaServerLogin
				SeerrLoginMethod.LOCAL -> settings.localLogin
			}
			if (!methodEnabled) return SeerrLoginResult.METHOD_DISABLED

			client.login(method, username, password)

			val sessionCookie = client.sessionCookie ?: return SeerrLoginResult.NOT_SEERR
			val account = SeerrAccount(url.toString(), method, sessionCookie)
			store.put(userId, account)
			_state.value = connect(userId, account)

			SeerrLoginResult.SUCCESS
		} catch (err: SeerrHttpException) {
			if (err.isUnauthorized) SeerrLoginResult.INVALID_CREDENTIALS
			else SeerrLoginResult.UNREACHABLE
		} catch (err: IOException) {
			Timber.w(err, "Unable to sign in to Seerr")
			SeerrLoginResult.UNREACHABLE
		} catch (_: SerializationException) {
			SeerrLoginResult.NOT_SEERR
		}
	}

	/**
	 * Forget the saved session. The session is also ended on the server when it can be reached.
	 */
	suspend fun disconnect() {
		val userId = currentUserId ?: return
		val account = store.get(userId)

		store.remove(userId)
		_state.value = SeerrState.NotConfigured

		val url = account?.let { parseSeerrUrl(it.url) } ?: return
		try {
			SeerrClient(url, httpClient, account.sessionCookie).logout()
		} catch (err: IOException) {
			Timber.w(err, "Unable to end the Seerr session")
		}
	}

	private suspend fun restore(userId: UUID?) {
		val account = userId?.let(store::get)
		if (account == null) {
			_state.value = SeerrState.NotConfigured
			return
		}

		_state.value = SeerrState.Connecting(account)
		_state.value = connect(userId, account)
	}

	private suspend fun connect(userId: UUID, account: SeerrAccount): SeerrState {
		val url = parseSeerrUrl(account.url) ?: return SeerrState.Unavailable(account)
		val client = SeerrClient(url, httpClient, account.sessionCookie)

		return try {
			val user = client.currentUser()
			val settings = client.publicSettings()
			val version = client.status().version

			val current = account.copy(sessionCookie = client.sessionCookie ?: account.sessionCookie)
			if (current != account) store.put(userId, current)

			SeerrState.Connected(current, user, settings, version)
		} catch (err: SeerrHttpException) {
			if (err.isUnauthorized) SeerrState.SessionExpired(account)
			else SeerrState.Unavailable(account)
		} catch (err: IOException) {
			Timber.w(err, "Unable to connect to Seerr")
			SeerrState.Unavailable(account)
		} catch (err: SerializationException) {
			Timber.w(err, "Unexpected response from Seerr")
			SeerrState.Unavailable(account)
		}
	}
}
