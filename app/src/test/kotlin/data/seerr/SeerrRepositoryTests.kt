package org.jellyfin.androidtv.data.seerr

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.jellyfin.androidtv.auth.repository.UserRepositoryImpl
import org.jellyfin.sdk.model.api.UserDto
import java.net.ServerSocket
import java.util.UUID

private class MemoryAccountStore : SeerrAccountStore {
	val accounts = mutableMapOf<UUID, SeerrAccount>()

	override fun get(userId: UUID) = accounts[userId]
	override fun put(userId: UUID, account: SeerrAccount) {
		accounts[userId] = account
	}

	override fun remove(userId: UUID) {
		accounts.remove(userId)
	}
}

/**
 * Stand-in for a Seerr server, accepts a single password.
 */
private class FakeSeerr(
	private val settings: String = """{"initialized":true,"localLogin":true,"mediaServerLogin":true,"unknownField":1}""",
) : Interceptor {
	val address = "seerr.test:5055"
	val validSession = "connect.sid=abc"
	var loggedOut = false

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val body = Buffer().also { request.body?.writeTo(it) }.readUtf8()
		val authenticated = request.header("Cookie") == validSession

		return when (request.url.encodedPath) {
			"/api/v1/status" -> request.respond(200, """{"version":"3.5.1"}""")
			"/api/v1/settings/public" -> request.respond(200, settings)
			"/api/v1/auth/local", "/api/v1/auth/jellyfin" -> {
				if ("\"password\":\"secret\"" in body) {
					request.respond(200, USER, "Set-Cookie" to "$validSession; Path=/; HttpOnly")
				} else {
					request.respond(401, """{"message":"Unauthorized"}""")
				}
			}

			"/api/v1/auth/me" -> {
				if (authenticated) request.respond(200, USER)
				else request.respond(401, """{"message":"Unauthorized"}""")
			}

			"/api/v1/auth/logout" -> {
				loggedOut = authenticated
				request.respond(200, "{}")
			}

			else -> request.respond(404, "not found")
		}
	}

	private companion object {
		const val USER = """{"id":7,"displayName":"Antonio","permissions":32,"somethingNew":true}"""
	}
}

private fun Request.respond(code: Int, body: String, vararg headers: Pair<String, String>) = Response.Builder()
	.request(this)
	.protocol(Protocol.HTTP_1_1)
	.code(code)
	.message("")
	.apply { headers.forEach { (name, value) -> addHeader(name, value) } }
	.body(body.toResponseBody())
	.build()

class SeerrRepositoryTests : FunSpec({
	val userId = UUID.randomUUID()

	fun user() = UserRepositoryImpl().apply {
		setCurrentUser(UserDto(id = userId, hasPassword = true, hasConfiguredPassword = true, hasConfiguredEasyPassword = false))
	}

	fun withRepository(
		seerr: Interceptor? = null,
		store: MemoryAccountStore = MemoryAccountStore(),
		block: suspend (SeerrRepository) -> Unit,
	) = kotlinx.coroutines.runBlocking {
		val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
		try {
			val httpClient = OkHttpClient.Builder().apply { seerr?.let(::addInterceptor) }.build()
			block(SeerrRepository(user(), store, httpClient, scope))
		} finally {
			scope.cancel()
		}
	}

	test("parseSeerrUrl() accepts what a user would type") {
		parseSeerrUrl("192.168.1.10:5055").toString() shouldBe "http://192.168.1.10:5055/"
		parseSeerrUrl(" https://seerr.example.com/ ").toString() shouldBe "https://seerr.example.com/"
		parseSeerrUrl("http://host:5055/api/v1/").toString() shouldBe "http://host:5055/"
		parseSeerrUrl("http://host/requests").toString() shouldBe "http://host/requests/"
		parseSeerrUrl("") shouldBe null
		parseSeerrUrl("http://") shouldBe null
	}

	test("signing in saves the session and not the password") {
		FakeSeerr().let { seerr ->
			val store = MemoryAccountStore()
			withRepository(seerr, store) { repository ->
				repository.login(seerr.address, SeerrLoginMethod.SERVER_ACCOUNT, "antonio", "secret") shouldBe SeerrLoginResult.SUCCESS

				val state = repository.state.value as SeerrState.Connected
				state.user.name shouldBe "Antonio"
				state.version shouldBe "3.5.1"
				store.accounts[userId] shouldBe SeerrAccount(
					url = "http://${seerr.address}/",
					method = SeerrLoginMethod.SERVER_ACCOUNT,
					sessionCookie = "connect.sid=abc",
				)
			}
		}
	}

	test("a wrong password is reported and nothing is saved") {
		FakeSeerr().let { seerr ->
			val store = MemoryAccountStore()
			withRepository(seerr, store) { repository ->
				repository.login(seerr.address, SeerrLoginMethod.LOCAL, "a@b.c", "wrong") shouldBe SeerrLoginResult.INVALID_CREDENTIALS
				store.accounts.isEmpty() shouldBe true
			}
		}
	}

	test("a disabled login method is reported") {
		FakeSeerr(settings = """{"localLogin":false,"mediaServerLogin":true}""").let { seerr ->
			withRepository(seerr) { repository ->
				repository.login(seerr.address, SeerrLoginMethod.LOCAL, "a@b.c", "secret") shouldBe SeerrLoginResult.METHOD_DISABLED
			}
		}
	}

	test("another service on the address is not mistaken for Seerr") {
		val other = Interceptor { it.request().respond(404, "not found") }

		withRepository(other) { repository ->
			repository.login("media.test:8096", SeerrLoginMethod.LOCAL, "a", "b") shouldBe SeerrLoginResult.NOT_SEERR
		}
	}

	test("a closed port is unreachable") {
		val port = ServerSocket(0).use { it.localPort }

		withRepository { repository ->
			repository.login("127.0.0.1:$port", SeerrLoginMethod.LOCAL, "a", "b") shouldBe SeerrLoginResult.UNREACHABLE
		}
	}

	test("a saved session is restored on start") {
		FakeSeerr().let { seerr ->
			val store = MemoryAccountStore()
			store.put(userId, SeerrAccount("http://${seerr.address}/", SeerrLoginMethod.LOCAL, "connect.sid=abc"))

			withRepository(seerr, store) { repository ->
				withTimeout(5_000) { repository.state.first { it is SeerrState.Connected } }
			}
		}
	}

	test("an expired session asks to sign in again and keeps the address") {
		FakeSeerr().let { seerr ->
			val store = MemoryAccountStore()
			store.put(userId, SeerrAccount("http://${seerr.address}/", SeerrLoginMethod.LOCAL, "connect.sid=old"))

			withRepository(seerr, store) { repository ->
				withTimeout(5_000) { repository.state.first { it is SeerrState.SessionExpired } }
				store.accounts.containsKey(userId) shouldBe true
			}
		}
	}

	test("disconnecting forgets the session and ends it on the server") {
		FakeSeerr().let { seerr ->
			val store = MemoryAccountStore()
			withRepository(seerr, store) { repository ->
				repository.login(seerr.address, SeerrLoginMethod.LOCAL, "a@b.c", "secret")
				repository.disconnect()

				repository.state.value shouldBe SeerrState.NotConfigured
				store.accounts.isEmpty() shouldBe true
				seerr.loggedOut shouldBe true
			}
		}
	}
})
