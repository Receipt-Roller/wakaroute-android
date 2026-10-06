package com.wakaroute.core.auth

import com.wakaroute.core.cards.CardProgress
import com.wakaroute.core.cards.CardReview
import com.wakaroute.core.cards.InMemoryCardProgressStore
import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.content.ContentClient
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
 * Deleting a learning record, and the two ways to get it subtly wrong.
 *
 * **Order.** Forget locally first and a failed request leaves an account on the
 * server the student can no longer reach — so can no longer delete. They asked
 * to be forgotten and instead became unreachable.
 *
 * **Leftovers.** The offline queue holds quiz answers and study times. Sending
 * them after the delete would recreate the record that was just deleted, under
 * whatever account comes next.
 */
class AccountDeletionTest {

    @Test
    fun `the server is asked before this device forgets anything`() = runTest {
        val auth = ScriptedAuth(deleteFails = ApiError.Offline)
        val queue = queue()
        queue.markComplete("lesson-1")

        val result = AccountDeletion(auth.session, queue).delete()

        assertTrue(result is AccountDeletion.Result.Failed)
        // Everything still here, because the account still is. A student on a
        // train taps 削除 and nothing happens — which is the correct nothing.
        assertEquals("mnbd_test", auth.store.read(SecretStore.DEVICE_SECRET))
        assertEquals(1, queue.pending().size)
        assertTrue(auth.session.hasAccount())
    }

    @Test
    fun `a deleted account leaves no credential behind`() = runTest {
        val auth = ScriptedAuth()

        assertEquals(AccountDeletion.Result.Deleted, AccountDeletion(auth.session, queue()).delete())

        assertNull(auth.store.read(SecretStore.DEVICE_SECRET))
        assertNull(auth.store.read(SecretStore.REFRESH_TOKEN))
        // Nothing left that could recover the account that was just deleted.
        assertTrue(!auth.session.hasAccount())
    }

    @Test
    fun `queued records go with the account they belong to`() = runTest {
        // These are lesson completions and study times made by someone who has
        // just asked to be forgotten. Left in place they would be sent under
        // the next account and recreate the record that was deleted.
        val queue = queue()
        queue.markComplete("lesson-1")
        queue.recordStudy(
            clientSessionId = "session-1",
            startedAt = "2026-08-05T10:00:00Z",
            endedAt = "2026-08-05T10:30:00Z",
            durationSeconds = 1800,
        )

        AccountDeletion(ScriptedAuth().session, queue).delete()

        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `card progress is forgotten only once the server has deleted`() = runTest {
        // It lives only on this device, so nothing else will ever clear it.
        val answered = CardProgress(mapOf("kanji-1" to CardReview(box = 2, reviewedOnEpochDay = 20_000)))

        val kept = InMemoryCardProgressStore(answered)
        AccountDeletion(ScriptedAuth(deleteFails = ApiError.Offline).session, queue(), kept).delete()
        assertEquals(answered, kept.read())

        val cleared = InMemoryCardProgressStore(answered)
        AccountDeletion(ScriptedAuth().session, queue(), cleared).delete()
        assertEquals(CardProgress(), cleared.read())
    }

    @Test
    fun `deletion carries a bearer token`() = runTest {
        // DeviceAuthClient is built on the *unauthenticated* http client, so
        // nothing attaches the header unless this path does it on purpose.
        // Without it every delete is a 401 — on the one screen where the app
        // must not report success it did not achieve.
        val auth = ScriptedAuth()

        AccountDeletion(auth.session, queue()).delete()

        assertEquals("Bearer access-1", auth.http.authorizationOn("/api/v1/me"))
        assertEquals(HttpRequest.Method.DELETE, auth.http.methodOn("/api/v1/me"))
    }

    @Test
    fun `a rejected delete is reported rather than swallowed`() = runTest {
        // 204 has no body, so this cannot go through sendDecoding — and raw
        // send() returns the response without looking at the status. That is
        // the shortcut that turns a 500 into 「削除しました」.
        val auth = ScriptedAuth(deleteFails = null, deleteStatus = 500)

        val result = AccountDeletion(auth.session, queue()).delete()

        assertEquals(500, ((result as AccountDeletion.Result.Failed).cause as ApiError.Http).status)
        assertEquals("mnbd_test", auth.store.read(SecretStore.DEVICE_SECRET))
    }

    // --- helpers -----------------------------------------------------------

    private fun queue() = LearningActionQueue(
        store = InMemoryPendingActionStore(),
        content = ContentClient(OfflineContent, AppEnvironment.Production),
        now = { 1_000_000 },
        newId = { "id-${ids++}" },
    )

    private var ids = 0

    /** The queue must never reach the network in these tests. */
    private object OfflineContent : HttpClient {
        override suspend fun send(request: HttpRequest): HttpResponse = throw ApiError.Offline
    }

    private class ScriptedAuth(
        deleteFails: ApiError? = null,
        deleteStatus: Int = 204,
    ) {
        val store = InMemorySecretStore(
            mapOf(
                SecretStore.DEVICE_ID to "0123456789abcdef0123",
                SecretStore.DEVICE_SECRET to "mnbd_test",
                SecretStore.REFRESH_TOKEN to "refresh-old",
            ),
        )
        val http = AuthHttp(deleteFails, deleteStatus)
        val session = AuthSession(
            client = DeviceAuthClient(http, AppEnvironment.Production),
            store = store,
            deviceIds = StoredDeviceIdProvider(store),
            now = { 1_000_000 },
        )
    }

    private class AuthHttp(
        private val deleteFails: ApiError?,
        private val deleteStatus: Int,
    ) : HttpClient {
        private val sent = mutableListOf<HttpRequest>()

        fun authorizationOn(path: String) = sent.last { it.url.endsWith(path) }.headers["Authorization"]

        fun methodOn(path: String) = sent.last { it.url.endsWith(path) }.method

        override suspend fun send(request: HttpRequest): HttpResponse {
            sent += request

            if (request.method == HttpRequest.Method.DELETE) {
                deleteFails?.let { throw it }
                return HttpResponse(deleteStatus, "")
            }

            return HttpResponse(
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
