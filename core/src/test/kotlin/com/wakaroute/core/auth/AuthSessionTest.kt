package com.wakaroute.core.auth

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.net.ApiError
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
import com.wakaroute.core.net.HttpResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The account-wide sign-out, in all the shapes it comes in.
 *
 * Each of these corresponds to a sentence in the 実装ガイド that is only a
 * sentence until someone writes the obvious code. The symptom of every one of
 * them is the same and is nearly undiagnosable from a bug report:
 * 「ときどき勝手にログアウトされる」.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthSessionTest {

    @Test
    fun `the device secret is written before anything else in the response is used`() = runTest {
        // The secret comes back exactly once. Anything that can fail must not
        // run before it is stored, or the account exists on the server with no
        // way for this device to prove it owns it.
        val store = InMemorySecretStore()
        val http = ScriptedHttp(registration())

        session(http, store).accessToken()

        assertEquals("mnbd_test", store.read(SecretStore.DEVICE_SECRET))
        assertEquals("refresh-1", store.read(SecretStore.REFRESH_TOKEN))
    }

    @Test
    fun `a fresh token is reused rather than refreshed`() = runTest {
        val http = ScriptedHttp(registration())
        val session = session(http, InMemorySecretStore())

        assertEquals("access-1", session.accessToken())
        assertEquals("access-1", session.accessToken())
        assertEquals("one registration, no refresh", 1, http.calls.size)
    }

    @Test
    fun `an expired token is refreshed with the stored refresh token`() = runTest {
        val store = InMemorySecretStore(
            mapOf(
                SecretStore.DEVICE_ID to "0123456789abcdef0123",
                SecretStore.DEVICE_SECRET to "mnbd_test",
                SecretStore.REFRESH_TOKEN to "refresh-1",
            ),
        )
        val http = ScriptedHttp(authPayload(access = "access-2", refresh = "refresh-2"))

        assertEquals("access-2", session(http, store).accessToken())
        assertEquals("/api/v1/auth/refresh", http.paths.single())
        // The new one replaces the old. Keeping the old is how it gets replayed.
        assertEquals("refresh-2", store.read(SecretStore.REFRESH_TOKEN))
    }

    @Test
    fun `concurrent callers cause exactly one refresh`() = runTest {
        // Two screens waking at once both read the same stored token. Without
        // serialisation the second send is a replay, and the server revokes
        // every session on the account.
        val store = InMemorySecretStore(
            mapOf(
                SecretStore.DEVICE_ID to "0123456789abcdef0123",
                SecretStore.DEVICE_SECRET to "mnbd_test",
                SecretStore.REFRESH_TOKEN to "refresh-1",
            ),
        )
        val http = BlockingHttp(authPayload(access = "access-2", refresh = "refresh-2"))
        val session = session(http, store)

        val callers = List(8) { async { session.accessToken() } }
        advanceUntilIdle()
        http.release()
        val tokens = callers.awaitAll()

        assertEquals("every caller must get the same token", List(8) { "access-2" }, tokens)
        assertEquals("exactly one refresh may reach the network", 1, http.calls.size)
    }

    @Test
    fun `a failed refresh is never retried with the same token`() = runTest {
        // A timeout may mean the request succeeded and the response was lost —
        // so the token is already spent. Re-presenting it is the exact action
        // that signs the student out everywhere.
        val store = InMemorySecretStore(
            mapOf(
                SecretStore.DEVICE_ID to "0123456789abcdef0123",
                SecretStore.DEVICE_SECRET to "mnbd_test",
                SecretStore.REFRESH_TOKEN to "refresh-1",
            ),
        )
        val http = ScriptedHttp(
            "/api/v1/auth/refresh" to Result.failure(ApiError.TimedOut),
            "/api/v1/devices/token" to Result.success(authPayload(access = "access-3", refresh = "refresh-3")),
        )

        assertEquals("access-3", session(http, store).accessToken())

        assertEquals(
            "recovery goes through /devices/token, not a second refresh",
            listOf("/api/v1/auth/refresh", "/api/v1/devices/token"),
            http.paths,
        )
        assertEquals(1, http.paths.count { it == "/api/v1/auth/refresh" })
    }

    @Test
    fun `a spent refresh token is dropped so nothing can reuse it later`() = runTest {
        val store = InMemorySecretStore(
            mapOf(
                SecretStore.DEVICE_ID to "0123456789abcdef0123",
                SecretStore.DEVICE_SECRET to "mnbd_test",
                SecretStore.REFRESH_TOKEN to "refresh-1",
            ),
        )
        val http = ScriptedHttp(
            "/api/v1/auth/refresh" to Result.failure(ApiError.TimedOut),
            "/api/v1/devices/token" to Result.success(authPayload(access = "access-3", refresh = null)),
        )

        session(http, store).accessToken()

        // The recovery response carried no new refresh token, so the slot must
        // be empty rather than still holding the burnt one.
        assertNull(store.read(SecretStore.REFRESH_TOKEN))
    }

    @Test
    fun `an invalid device secret falls back to re-registration`() = runTest {
        // Re-registering with an unchanged deviceId returns the SAME account,
        // so this cannot orphan a student's records.
        val store = InMemorySecretStore(
            mapOf(
                SecretStore.DEVICE_ID to "0123456789abcdef0123",
                SecretStore.DEVICE_SECRET to "mnbd_stale",
            ),
        )
        val http = ScriptedHttp(
            "/api/v1/devices/token" to Result.failure(ApiError.Http(401, null)),
            "/api/v1/devices/register" to Result.success(registration()),
        )

        assertEquals("access-1", session(http, store).accessToken())
        assertEquals("mnbd_test", store.read(SecretStore.DEVICE_SECRET))
    }

    @Test
    fun `a server failure during recovery is not papered over with a new account`() = runTest {
        // A 500 is not evidence that the secret is bad. Registering here would
        // silently abandon the student's existing account.
        val store = InMemorySecretStore(
            mapOf(
                SecretStore.DEVICE_ID to "0123456789abcdef0123",
                SecretStore.DEVICE_SECRET to "mnbd_test",
            ),
        )
        val http = ScriptedHttp("/api/v1/devices/token" to Result.failure(ApiError.Http(500, null)))

        try {
            session(http, store).accessToken()
            fail("expected the 500 to propagate")
        } catch (e: ApiError.Http) {
            assertEquals(500, e.status)
        }

        assertTrue(http.paths.none { it.endsWith("/register") })
    }

    @Test
    fun `a 401 renewal is skipped when someone else already renewed`() = runTest {
        val store = InMemorySecretStore()
        val http = ScriptedHttp(registration())
        val session = session(http, store)

        val first = session.accessToken()
        // Another caller has since renewed; this one presents a token that is
        // no longer current, so there is nothing to spend.
        assertEquals(first, session.renewAfterUnauthorized("some-older-token"))
        assertEquals(1, http.calls.size)
    }

    @Test
    fun `a device id shorter than the server minimum is never sent`() = runTest {
        // The server rejects these with a 400, but the reason to guard is that
        // short ids collide between installs — and a collision joins one
        // student to another student's records.
        val client = DeviceAuthClient(ScriptedHttp(registration()), AppEnvironment.Production)

        try {
            client.register("short")
            fail("expected the short id to be rejected before it left the device")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("16"))
        }
    }

    @Test
    fun `a generated device id is stable and long enough`() {
        val store = InMemorySecretStore()
        val provider = StoredDeviceIdProvider(store)

        val first = provider.deviceId()
        assertTrue(first.length >= StoredDeviceIdProvider.MINIMUM_LENGTH)
        assertEquals("the id must not change between launches", first, provider.deviceId())
        assertNotNull(store.read(SecretStore.DEVICE_ID))
    }

    @Test
    fun `a stored device id that is too short is replaced`() {
        // Rather than being sent and rejected forever.
        val store = InMemorySecretStore(mapOf(SecretStore.DEVICE_ID to "abc"))
        val id = StoredDeviceIdProvider(store).deviceId()

        assertTrue(id.length >= StoredDeviceIdProvider.MINIMUM_LENGTH)
    }

    @Test
    fun `an unreadable expiry means refresh next time rather than crash`() = runTest {
        // The backend sends 7-digit fractional seconds and has shipped shapes
        // that standard parsers reject. An expiry we cannot read must not be
        // treated as valid forever, and must not throw either.
        val http = ScriptedHttp(
            "/api/v1/devices/register" to Result.success(registration(expiresAt = "きのう")),
            "/api/v1/auth/refresh" to Result.success(authPayload(access = "access-2", refresh = "refresh-2")),
        )
        val session = session(http, InMemorySecretStore())

        assertEquals("access-1", session.accessToken())

        // Renewed rather than reused. Failing in this direction costs one
        // request; the other direction puts a dead token on the wire and
        // surfaces to the student as a random sign-out.
        assertEquals("access-2", session.accessToken())
        assertEquals(
            listOf("/api/v1/devices/register", "/api/v1/auth/refresh"),
            http.paths,
        )
    }

    // --- helpers -----------------------------------------------------------

    private fun session(http: HttpClient, store: SecretStore) = AuthSession(
        client = DeviceAuthClient(http, AppEnvironment.Production),
        store = store,
        deviceIds = StoredDeviceIdProvider(store),
        now = { 1_000_000 },
    )

    /** Expiry an hour out from the fixed clock above. */
    private fun registration(expiresAt: String? = "1970-01-12T21:46:40Z") = """
        {
          "deviceSecret": "mnbd_test",
          "isNewAccount": true,
          "auth": {
            "accessToken": "access-1",
            "expiresAt": ${expiresAt?.let { "\"$it\"" } ?: "null"},
            "refreshToken": "refresh-1",
            "scopes": ["read:catalog"],
            "user": { "id": "TEST-USER-0001" }
          }
        }
    """.trimIndent()

    private fun authPayload(access: String, refresh: String?) = """
        {
          "accessToken": "$access",
          "expiresAt": "1970-01-12T21:46:40Z",
          "refreshToken": ${refresh?.let { "\"$it\"" } ?: "null"},
          "scopes": ["read:catalog"],
          "user": { "id": "TEST-USER-0001" }
        }
    """.trimIndent()

    /** Answers by path, so a test states which endpoint it expects. */
    private class ScriptedHttp(private val byPath: Map<String, Result<String>>) : HttpClient {
        val calls = mutableListOf<HttpRequest>()
        val paths: List<String> get() = calls.map { it.url.substringAfter("api.manabu2.com") }

        constructor(body: String) : this(mapOf("*" to Result.success(body)))

        constructor(vararg entries: Pair<String, Result<String>>) : this(entries.toMap())

        override suspend fun send(request: HttpRequest): HttpResponse {
            calls += request
            val path = request.url.substringAfter("api.manabu2.com")
            val answer = byPath[path] ?: byPath["*"] ?: error("no scripted answer for $path")
            return HttpResponse(200, answer.getOrThrow())
        }
    }

    /** Holds every call open until released, so overlap is real rather than assumed. */
    private class BlockingHttp(private val body: String) : HttpClient {
        val calls = mutableListOf<HttpRequest>()
        private val gate = CompletableDeferred<Unit>()

        fun release() = gate.complete(Unit)

        override suspend fun send(request: HttpRequest): HttpResponse {
            calls += request
            gate.await()
            return HttpResponse(200, body)
        }
    }
}
