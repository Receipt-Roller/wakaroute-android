package com.wakaroute.core.net

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RequestsTest {

    @Serializable
    private data class Body(val value: String)

    private val request = HttpRequest(HttpRequest.Method.GET, "https://example.invalid/thing")

    @Test
    fun `a successful body decodes`() = runTest {
        val client = FakeHttpClient(200, """{"value":"ok"}""")
        assertEquals(Body("ok"), client.sendDecoding(request, Body.serializer()))
    }

    @Test
    fun `an unknown field does not break decoding`() = runTest {
        // The catalogue gains fields between releases, and an app already on a
        // student's phone has to keep working when it does.
        val client = FakeHttpClient(200, """{"value":"ok","addedLater":42}""")
        assertEquals(Body("ok"), client.sendDecoding(request, Body.serializer()))
    }

    @Test
    fun `an undecodable body is an error, never a crash`() = runTest {
        val client = FakeHttpClient(200, "<html>maintenance</html>")

        try {
            client.sendDecoding(request, Body.serializer())
            fail("expected a decoding error")
        } catch (e: ApiError.Decoding) {
            assertFalse("a malformed body will still be malformed next time", e.isTransient)
        }
    }

    @Test
    fun `problem details are carried through and branched on by code`() = runTest {
        val client = FakeHttpClient(
            403,
            """{"title":"Forbidden","status":403,"detail":"文面は変わりえます","code":"device_registration_disabled"}""",
        )

        try {
            client.sendDecoding(request, Body.serializer())
            fail("expected an HTTP error")
        } catch (e: ApiError.Http) {
            // Branch on code. detail is prose and the backend guide warns it is
            // rewritten without notice.
            assertEquals("device_registration_disabled", e.code)
            assertEquals(403, e.status)
        }
    }

    @Test
    fun `an error body that is not problem details still produces an http error`() = runTest {
        val client = FakeHttpClient(500, "Internal Server Error")

        try {
            client.sendDecoding(request, Body.serializer())
            fail("expected an HTTP error")
        } catch (e: ApiError.Http) {
            assertEquals(500, e.status)
            assertEquals(null, e.problem)
        }
    }

    @Test
    fun `transient failures are retried, up to the limit`() = runTest {
        val client = FakeHttpClient.failingThenSucceeding(ApiError.TimedOut, 200, """{"value":"ok"}""")

        val result = retryingReads(attempts = 3) {
            client.sendDecoding(request, Body.serializer())
        }

        assertEquals(Body("ok"), result)
        assertEquals(2, client.requestCount)
    }

    @Test
    fun `a permanent failure is not retried`() = runTest {
        // A 404 is permanent. Retrying it is what lets one dead record block a
        // whole queue forever.
        val client = FakeHttpClient(404, """{"code":"not_found"}""")

        try {
            retryingReads(attempts = 3) { client.sendDecoding(request, Body.serializer()) }
            fail("expected the 404 to propagate")
        } catch (e: ApiError.Http) {
            assertEquals(404, e.status)
        }

        assertEquals("a 404 must be asked for exactly once", 1, client.requestCount)
    }

    @Test
    fun `retries are bounded`() = runTest {
        val client = FakeHttpClient.failing(ApiError.Offline, ApiError.Offline, ApiError.Offline, ApiError.Offline)

        try {
            retryingReads(attempts = 3) { client.sendDecoding(request, Body.serializer()) }
            fail("expected the failure to propagate once attempts run out")
        } catch (e: ApiError) {
            assertTrue(e is ApiError.Offline)
        }

        assertEquals(3, client.requestCount)
    }

    @Test
    fun `429 and 408 are transient but other 4xx are not`() {
        assertTrue(ApiError.Http(429, null).isTransient)
        assertTrue(ApiError.Http(408, null).isTransient)
        assertTrue(ApiError.Http(503, null).isTransient)

        assertFalse(ApiError.Http(400, null).isTransient)
        assertFalse(ApiError.Http(404, null).isTransient)
        assertFalse(ApiError.Http(409, null).isTransient)
    }
}
