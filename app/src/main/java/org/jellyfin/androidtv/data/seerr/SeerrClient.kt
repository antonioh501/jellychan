package org.jellyfin.androidtv.data.seerr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The server answered with an unsuccessful status code.
 */
class SeerrHttpException(val code: Int) : IOException("Seerr responded with HTTP $code") {
	val isUnauthorized get() = code == 401 || code == 403
}

/**
 * Turn what the user typed into the base address of a Seerr server. The scheme is optional and a
 * trailing API path is removed.
 */
fun parseSeerrUrl(input: String): HttpUrl? {
	val address = input.trim()
	if (address.isEmpty()) return null

	val url = (if ("://" in address) address else "http://$address").toHttpUrlOrNull() ?: return null
	val path = url.encodedPath.trimEnd('/').removeSuffix(API_PATH)

	return url.newBuilder()
		.encodedPath("$path/")
		.query(null)
		.fragment(null)
		.build()
}

/**
 * Minimal client for the Seerr REST API. Seerr keeps the user signed in with a session cookie, which
 * is picked up from every response so a refreshed session is never lost.
 */
class SeerrClient(
	private val baseUrl: HttpUrl,
	httpClient: OkHttpClient,
	sessionCookie: String? = null,
) {
	private val client = httpClient.newBuilder()
		.connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
		.readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
		.build()

	@Volatile
	var sessionCookie: String? = sessionCookie
		private set

	suspend fun status(): SeerrStatus = get("status")

	suspend fun publicSettings(): SeerrPublicSettings = get("settings/public")

	suspend fun currentUser(): SeerrUser = get("auth/me")

	suspend fun login(
		method: SeerrLoginMethod,
		username: String,
		password: String,
	): SeerrUser = when (method) {
		SeerrLoginMethod.SERVER_ACCOUNT -> post(SERVER_ACCOUNT_LOGIN_PATH, json.encodeToString(SeerrServerAccountLogin(username, password)))
		SeerrLoginMethod.LOCAL -> post(LOCAL_LOGIN_PATH, json.encodeToString(SeerrLocalLogin(username, password)))
	}

	suspend fun logout() {
		execute(request("auth/logout").post(EMPTY_BODY).build())
	}

	private suspend inline fun <reified T> get(path: String): T =
		json.decodeFromString(execute(request(path).get().build()))

	private suspend inline fun <reified T> post(path: String, body: String): T =
		json.decodeFromString(execute(request(path).post(body.toRequestBody(JSON_MEDIA_TYPE)).build()))

	private fun request(path: String): Request.Builder {
		val url = baseUrl.newBuilder()
			.addPathSegments("api/v1/$path")
			.build()

		return Request.Builder().url(url).apply {
			sessionCookie?.let { header("Cookie", it) }
		}
	}

	private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
		client.newCall(request).execute().use { response ->
			updateSession(response)
			if (!response.isSuccessful) throw SeerrHttpException(response.code)
			response.body?.string().orEmpty()
		}
	}

	private fun updateSession(response: Response) {
		Cookie.parseAll(response.request.url, response.headers)
			.firstOrNull { it.name == SESSION_COOKIE_NAME }
			?.let { sessionCookie = "${it.name}=${it.value}" }
	}

	private companion object {
		const val CONNECT_TIMEOUT_SECONDS = 5L
		const val READ_TIMEOUT_SECONDS = 20L
		const val SESSION_COOKIE_NAME = "connect.sid"
		const val LOCAL_LOGIN_PATH = "auth/local"

		// Fixed by the Seerr API
		const val SERVER_ACCOUNT_LOGIN_PATH = "auth/jellyfin"

		val JSON_MEDIA_TYPE = "application/json".toMediaType()
		val EMPTY_BODY = ByteArray(0).toRequestBody(null)

		val json = Json {
			ignoreUnknownKeys = true
			coerceInputValues = true
		}
	}
}

private const val API_PATH = "/api/v1"
