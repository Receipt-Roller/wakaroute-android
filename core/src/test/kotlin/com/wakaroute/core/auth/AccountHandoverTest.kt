package com.wakaroute.core.auth

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.content.ContentClient
import com.wakaroute.core.content.QuizAnswer
import com.wakaroute.core.net.ApiError
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
import com.wakaroute.core.net.HttpResponse
import com.wakaroute.core.offline.InMemoryPendingActionStore
import com.wakaroute.core.offline.LearningActionQueue
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Signing in on a second phone, and the work that can vanish while it happens.
 *
 * The dangerous property is that signing in **succeeds** either way. The
 * student sees their progress arrive and has no reason to suspect that
 * yesterday's quiz — still sitting in the queue, still belonging to the
 * anonymous account this install just abandoned — will never be sent.
 *
 * So the tests here are mostly about what must *not* happen quietly.
 */
class AccountHandoverTest {

    @Test
    fun `sign-in is refused while the queue still holds unsent work`() = runTest {
        val queue = queue(FakeApi(failWith = ApiError.Offline))
        queue.recordStudy(
            clientSessionId = "session-1",
            startedAt = "2026-08-05T10:00:00Z",
            endedAt = "2026-08-05T10:30:00Z",
            durationSeconds = 1800,
        )

        val auth = ScriptedAuth()
        val result = AccountHandover(auth.session, queue).signIn("a@example.invalid", "pw")

        assertEquals(
            AccountHandover.SignInResult.Refused(AccountHandover.UnsentWork(1)),
            result,
        )
        // Nothing was touched. The student still holds the account that owns
        // this record, and can try again on a better connection.
        assertTrue(auth.http.paths.none { it.endsWith("/auth/login") })
        assertEquals(1, queue.pending().size)
    }

    @Test
    fun `the refusal says how much is at stake`() = runTest {
        // A bare failure gives the student nothing to decide with. The count is
        // what turns 「あとで」 into an informed choice.
        val queue = queue(FakeApi(failWith = ApiError.Offline))
        queue.submitQuiz("lesson-1", listOf(QuizAnswer("q1", "o1")))
        queue.submitQuiz("lesson-2", listOf(QuizAnswer("q1", "o1")))

        val result = AccountHandover(ScriptedAuth().session, queue).signIn("a@example.invalid", "pw")

        assertEquals(2, (result as AccountHandover.SignInResult.Refused).unsent.pendingRecords)
    }

    @Test
    fun `queued work is sent before the account is left`() = runTest {
        // The queue is flushed first, not merely inspected. Most refusals are
        // temporary — the phone simply has not been online since — and a
        // student who is now online should not be blocked by yesterday.
        val api = FakeApi()
        val queue = queue(api)
        queue.markComplete("lesson-1")

        val auth = ScriptedAuth()
        val result = AccountHandover(auth.session, queue).signIn("a@example.invalid", "pw")

        assertTrue(result is AccountHandover.SignInResult.SignedIn)
        assertEquals(listOf("lesson-1"), api.sentLessons)
    }

    @Test
    fun `signing in clears the device secret`() = runTest {
        // Left in place, a later recovery through devices/token would silently
        // put the student back on the anonymous account they signed out of —
        // with the signed-in progress apparently gone.
        val auth = ScriptedAuth(
            stored = mapOf(
                SecretStore.DEVICE_ID to "0123456789abcdef0123",
                SecretStore.DEVICE_SECRET to "mnbd_test",
                SecretStore.REFRESH_TOKEN to "refresh-old",
            ),
        )

        AccountHandover(auth.session, queue(FakeApi())).signIn("a@example.invalid", "pw")

        assertNull(auth.store.read(SecretStore.DEVICE_SECRET))
        assertEquals("the new session's token, not the old one", "refresh-new", auth.store.read(SecretStore.REFRESH_TOKEN))
    }

    @Test
    fun `a signed-in install still counts as having an account`() = runTest {
        // Found on a device: sign in, go back, and その他 said
        // 「まだ何も保存していません」 to a student who had just restored three
        // years of work. Every screen that shows learner data is gated on this,
        // so the record they came back for was invisible everywhere.
        //
        // The cause is that signing in deletes the device secret on purpose —
        // so a check that only looks at the secret reports no account.
        val auth = ScriptedAuth()
        AccountHandover(auth.session, queue(FakeApi())).signIn("a@example.invalid", "pw")

        assertNull(auth.store.read(SecretStore.DEVICE_SECRET))
        assertTrue(auth.session.hasAccount())
    }

    @Test
    fun `anything left over is discarded rather than filed under the new account`() = runTest {
        // Sending it after the switch would attach one student's study time to
        // another student's record — worse than losing it.
        val queue = queue(FakeApi(failWith = ApiError.Offline))
        queue.markComplete("lesson-1")

        val result = AccountHandover(ScriptedAuth().session, queue)
            .signIn("a@example.invalid", "pw", discardingUnsentWork = true)

        assertTrue(result is AccountHandover.SignInResult.SignedIn)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `linking is not guarded because nothing moves`() = runTest {
        // The user id is unchanged, so a queue entry made before the link is
        // still valid after it. Refusing here would be a pointless obstacle in
        // front of the one action that makes the record recoverable at all.
        val queue = queue(FakeApi(failWith = ApiError.Offline))
        queue.markComplete("lesson-1")

        val auth = ScriptedAuth()
        val link = AccountHandover(auth.session, queue).link("a@example.invalid", "pw")

        assertEquals("TEST-USER-0001", link.userId)
        assertEquals(1, queue.pending().size)
    }

    @Test
    fun `linking carries a bearer token`() = runTest {
        // `/me/link` is the one auth endpoint that acts on an existing account,
        // and DeviceAuthClient is built on the *unauthenticated* http client —
        // so nothing attaches the header unless this path does it deliberately.
        // Without it every link is a 401, on a screen that only appears once a
        // student has something worth keeping.
        val auth = ScriptedAuth()
        AccountHandover(auth.session, queue(FakeApi())).link("a@example.invalid", "pw")

        assertEquals("Bearer access-1", auth.http.authorizationOn("/api/v1/me/link"))
    }

    @Test
    fun `a failed sign-in leaves the queue alone`() = runTest {
        // Wrong password, no network — the local account is still the student's
        // and everything in the queue still belongs to it.
        val queue = queue(FakeApi())
        queue.markComplete("lesson-1")

        val auth = ScriptedAuth(loginFails = ApiError.Http(401, null))
        val handover = AccountHandover(auth.session, queue)

        // Flushed on the way in, so the entry is gone for a legitimate reason;
        // what matters is that the failure did not clear the secret.
        runCatching { handover.signIn("a@example.invalid", "wrong") }

        assertEquals("mnbd_test", auth.store.read(SecretStore.DEVICE_SECRET))
    }

    // --- helpers -----------------------------------------------------------

    private fun queue(api: FakeApi) = LearningActionQueue(
        store = InMemoryPendingActionStore(),
        content = ContentClient(api, AppEnvironment.Production),
        now = { 1_000_000 },
        newId = { "id-${ids++}" },
    )

    private var ids = 0

    /** Content endpoints, which succeed unless told otherwise. */
    private class FakeApi(private val failWith: ApiError? = null) : HttpClient {
        val sentLessons = mutableListOf<String>()

        override suspend fun send(request: HttpRequest): HttpResponse {
            failWith?.let { throw it }

            val path = request.url.substringAfter("api.manabu2.com")
            sentLessons += path.removePrefix("/api/v1/lessons/").substringBefore('/')

            return HttpResponse(200, """{"lessonId":"x","courseId":"course-1","isCompleted":true}""")
        }
    }

    /** An [AuthSession] wired to canned auth responses. */
    private class ScriptedAuth(
        stored: Map<String, String> = mapOf(
            SecretStore.DEVICE_ID to "0123456789abcdef0123",
            SecretStore.DEVICE_SECRET to "mnbd_test",
            SecretStore.REFRESH_TOKEN to "refresh-old",
        ),
        loginFails: ApiError? = null,
    ) {
        val store = InMemorySecretStore(stored)
        val http = AuthHttp(loginFails)
        val session = AuthSession(
            client = DeviceAuthClient(http, AppEnvironment.Production),
            store = store,
            deviceIds = StoredDeviceIdProvider(store),
            now = { 1_000_000 },
        )
    }

    private class AuthHttp(private val loginFails: ApiError?) : HttpClient {
        val paths = mutableListOf<String>()
        private val sent = mutableListOf<HttpRequest>()

        fun authorizationOn(path: String): String? =
            sent.last { it.url.endsWith(path) }.headers["Authorization"]

        override suspend fun send(request: HttpRequest): HttpResponse {
            val path = request.url.substringAfter("api.manabu2.com")
            paths += path
            sent += request

            return when {
                path.endsWith("/auth/login") -> {
                    loginFails?.let { throw it }
                    HttpResponse(
                        200,
                        """
                        {
                          "accessToken": "access-new",
                          "expiresAt": "1970-01-12T21:46:40Z",
                          "refreshToken": "refresh-new",
                          "scopes": ["read:catalog"],
                          "user": { "id": "TEST-USER-0001" }
                        }
                        """.trimIndent(),
                    )
                }

                path.endsWith("/me/link") -> HttpResponse(
                    200,
                    """{"userId":"TEST-USER-0001","email":"a@example.invalid","message":"ok"}""",
                )

                else -> HttpResponse(
                    200,
                    """
                    {
                      "accessToken": "access-1",
                      "expiresAt": "1970-01-12T21:46:40Z",
                      "refreshToken": "refresh-1",
                      "scopes": ["read:catalog"],
                      "user": { "id": "TEST-USER-0001" }
                    }
                    """.trimIndent(),
                )
            }
        }
    }
}
