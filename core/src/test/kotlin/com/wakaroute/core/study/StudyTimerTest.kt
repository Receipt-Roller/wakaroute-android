package com.wakaroute.core.study

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.content.ContentClient
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
import com.wakaroute.core.net.HttpResponse
import com.wakaroute.core.offline.InMemoryPendingActionStore
import com.wakaroute.core.offline.LearningActionQueue
import com.wakaroute.core.offline.PendingAction
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyTimerTest {

    private var clock = Instant.parse("2026-08-05T09:00:00Z")
    private var sessionIds = 0
    private val store = InMemoryPendingActionStore()

    @Test
    fun `a finished session is queued, not sent directly`() = runTest {
        // The record most likely to be made with no signal. Queued always.
        val timer = timer()
        timer.start(subject = "数学")
        clock = clock.plusSeconds(1500)

        assertEquals(1500, timer.stop())

        val recorded = store.read().single() as PendingAction.RecordStudy
        assertEquals(1500, recorded.durationSeconds)
        assertEquals("数学", recorded.subject)
    }

    @Test
    fun `a mis-tap is not study`() = runTest {
        // Recording a few seconds would put a meaningless entry on the calendar
        // and count towards a streak the student did not earn.
        val timer = timer()
        timer.start()
        clock = clock.plusSeconds(5)

        assertNull(timer.stop())
        assertTrue(store.read().isEmpty())
    }

    @Test
    fun `stopping when nothing runs records nothing`() = runTest {
        assertNull(timer().stop())
        assertTrue(store.read().isEmpty())
    }

    @Test
    fun `starting again discards the session left running`() = runTest {
        // One clock per student. Someone who starts 数学 having left 英語 running
        // an hour ago must not have both counting — and an hour they did not
        // study is worse than a lost minute they did.
        val timer = timer()
        timer.start(subject = "英語")
        clock = clock.plusSeconds(3600)

        timer.start(subject = "数学")
        clock = clock.plusSeconds(600)
        timer.stop()

        val recorded = store.read().single() as PendingAction.RecordStudy
        assertEquals("数学", recorded.subject)
        assertEquals(600, recorded.durationSeconds)
    }

    @Test
    fun `each session gets its own deduplication key`() = runTest {
        val timer = timer()

        timer.start()
        clock = clock.plusSeconds(300)
        timer.stop()

        timer.start()
        clock = clock.plusSeconds(300)
        timer.stop()

        val keys = store.read().map { (it as PendingAction.RecordStudy).clientSessionId }
        assertEquals("two sessions are two sessions", 2, keys.size)
        assertEquals(2, keys.toSet().size)
    }

    @Test
    fun `a resend keeps the key, so the same minutes are not counted twice`() = runTest {
        // A student looking at an inflated total has no way to tell it is wrong.
        val timer = timer()
        timer.start()
        clock = clock.plusSeconds(1200)
        timer.stop()

        val queued = store.read().single() as PendingAction.RecordStudy
        val api = RecordingApi()
        LearningActionQueue(store, ContentClient(api, AppEnvironment.Production)).flush()

        assertEquals(listOf(queued.clientSessionId), api.idempotencyKeys)
        assertTrue(api.bodies.single().contains(queued.clientSessionId))
    }

    @Test
    fun `the instants are sent, not a locally decided date`() = runTest {
        // The server decides which day a session belongs to. A phone in another
        // timezone, or one whose clock is wrong, would otherwise put study on
        // the wrong date and quietly break a streak.
        val timer = timer()
        timer.start()
        clock = clock.plusSeconds(900)
        timer.stop()

        val recorded = store.read().single() as PendingAction.RecordStudy
        assertEquals("2026-08-05T09:00:00Z", recorded.startedAt)
        assertEquals("2026-08-05T09:15:00Z", recorded.endedAt)
    }

    @Test
    fun `elapsed seconds track the running clock`() {
        val timer = timer()
        assertEquals(0, timer.elapsedSeconds())

        timer.start()
        clock = clock.plusSeconds(75)
        assertEquals(75, timer.elapsedSeconds())
    }

    private fun timer() = StudyTimer(
        queue = LearningActionQueue(store, ContentClient(RecordingApi(), AppEnvironment.Production)),
        now = { clock },
        newSessionId = { "session-${sessionIds++}" },
    )

    private class RecordingApi : HttpClient {
        val idempotencyKeys = mutableListOf<String>()
        val bodies = mutableListOf<String>()

        override suspend fun send(request: HttpRequest): HttpResponse {
            request.headers["Idempotency-Key"]?.let { idempotencyKeys += it }
            request.body?.let { bodies += it }
            return HttpResponse(200, """{"sessionId":"s1","studyDate":"2026-08-05","kind":"study","status":"completed"}""")
        }
    }
}
