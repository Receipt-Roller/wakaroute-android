package com.wakaroute.core.offline

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.content.ContentClient
import com.wakaroute.core.content.QuizAnswer
import com.wakaroute.core.net.ApiError
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
import com.wakaroute.core.net.HttpResponse
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 共通判断規則 §5, rule by rule.
 *
 * The queue exists so that a student who studied on a train does not lose their
 * record to the signal. Every test here is a way that promise gets broken while
 * the app still looks like it is working.
 */
class LearningActionQueueTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var clock = 1_000L
    private var ids = 0

    // --- merge rules -------------------------------------------------------

    @Test
    fun `opening a lesson twice is still one fact`() = runTest {
        val store = InMemoryPendingActionStore()
        val queue = queue(store, FakeApi())

        queue.markViewed("lesson-1")
        queue.markViewed("lesson-1")

        assertEquals(1, store.read().size)
    }

    @Test
    fun `finishing a lesson twice is still one fact`() = runTest {
        val store = InMemoryPendingActionStore()
        val queue = queue(store, FakeApi())

        queue.markComplete("lesson-1")
        queue.markComplete("lesson-1")

        assertEquals(1, store.read().size)
    }

    @Test
    fun `two quiz sittings are two sittings`() = runTest {
        // Different answers, a different score. Collapsing them would erase one
        // of the student's attempts.
        val store = InMemoryPendingActionStore()
        val queue = queue(store, FakeApi())

        queue.submitQuiz("lesson-1", listOf(QuizAnswer("q1", "wrong")))
        queue.submitQuiz("lesson-1", listOf(QuizAnswer("q1", "right")))

        assertEquals(2, store.read().size)
    }

    @Test
    fun `a later verdict replaces an earlier one`() = runTest {
        // One per lesson, and if the student changed their mind the later
        // answer is what they mean. The server replaces it too.
        val store = InMemoryPendingActionStore()
        val queue = queue(store, FakeApi())

        queue.rateLesson("lesson-1", understood = false, reasons = listOf("explanation"))
        queue.rateLesson("lesson-1", understood = true)

        val rating = store.read().single() as PendingAction.RateLesson
        assertTrue(rating.understood)
        assertTrue("reasons are dropped when understood", rating.reasons.isEmpty())
    }

    @Test
    fun `merging is per kind, not per lesson`() = runTest {
        // A completion and a quiz submission for the same lesson are different
        // facts. Keying the merge on the lesson alone would swallow one.
        val store = InMemoryPendingActionStore()
        val queue = queue(store, FakeApi())

        queue.markViewed("lesson-1")
        queue.markComplete("lesson-1")
        queue.submitQuiz("lesson-1", listOf(QuizAnswer("q1", "o1")))
        queue.rateLesson("lesson-1", understood = true)

        assertEquals(4, store.read().size)
    }

    @Test
    fun `the same kind on different lessons does not merge`() = runTest {
        val store = InMemoryPendingActionStore()
        val queue = queue(store, FakeApi())

        queue.markComplete("lesson-1")
        queue.markComplete("lesson-2")

        assertEquals(2, store.read().size)
    }

    // --- the idempotency key -----------------------------------------------

    @Test
    fun `a quiz keeps the key it was given, across a resend days later`() = runTest {
        // The whole reason the key is stored rather than generated at send
        // time. A fresh key on the retry is a second attempt: another row in
        // the history, a changed 理解度, a different 修了証 condition.
        val store = InMemoryPendingActionStore()
        val api = FakeApi(failWith = ApiError.Offline)
        val queue = queue(store, api)

        val queued = queue.submitQuiz("lesson-1", listOf(QuizAnswer("q1", "o1")))
        queue.flush()

        // Days pass.
        clock += 60 * 60 * 24 * 3
        api.failWith = null
        queue(store, api).flush()

        assertEquals(listOf(queued.idempotencyKey), api.idempotencyKeys)
    }

    @Test
    fun `two sittings get different keys`() = runTest {
        val store = InMemoryPendingActionStore()
        val queue = queue(store, FakeApi())

        val first = queue.submitQuiz("lesson-1", listOf(QuizAnswer("q1", "a")))
        val second = queue.submitQuiz("lesson-1", listOf(QuizAnswer("q1", "b")))

        assertTrue(first.idempotencyKey != second.idempotencyKey)
    }

    // --- permanent and temporary failures -----------------------------------

    @Test
    fun `a permanently failing entry is set aside and the rest still go`() = runTest {
        // A completion for a lesson that has since been deleted is a 404 for
        // all time. Retried in order it blocks everything behind it — for ever.
        val store = InMemoryPendingActionStore()
        val api = FakeApi(failLesson = "deleted-lesson" to ApiError.Http(404, null))
        val queue = queue(store, api)

        queue.markComplete("deleted-lesson")
        queue.markComplete("lesson-2")
        queue.markComplete("lesson-3")

        val result = queue.flush()

        assertEquals(2, result.sent)
        assertEquals(1, result.discarded)
        assertFalse(result.stoppedEarly)
        assertTrue("nothing is left to block the next run", store.read().isEmpty())
    }

    @Test
    fun `a temporary failure stops the run and keeps the order`() = runTest {
        val store = InMemoryPendingActionStore()
        val api = FakeApi(failLesson = "lesson-2" to ApiError.Offline)
        val queue = queue(store, api)

        queue.markComplete("lesson-1")
        queue.markComplete("lesson-2")
        queue.markComplete("lesson-3")

        val result = queue.flush()

        assertEquals(1, result.sent)
        assertTrue(result.stoppedEarly)
        assertEquals(
            listOf("lesson-2", "lesson-3"),
            store.read().map { it.lessonId },
        )
    }

    @Test
    fun `429 and 408 are temporary, other 4xx are not`() = runTest {
        for (status in listOf(429, 408, 503)) {
            val store = InMemoryPendingActionStore()
            val queue = queue(store, FakeApi(failWith = ApiError.Http(status, null)))
            queue.markComplete("lesson-1")

            assertTrue("$status must be retried later", queue.flush().stoppedEarly)
            assertEquals(1, store.read().size)
        }

        for (status in listOf(400, 404, 409)) {
            val store = InMemoryPendingActionStore()
            val queue = queue(store, FakeApi(failWith = ApiError.Http(status, null)))
            queue.markComplete("lesson-1")

            val result = queue.flush()
            assertFalse("$status will fail the same way for ever", result.stoppedEarly)
            assertEquals(1, result.discarded)
            assertTrue(store.read().isEmpty())
        }
    }

    @Test
    fun `entries are sent oldest first`() = runTest {
        val store = InMemoryPendingActionStore()
        val api = FakeApi()
        val queue = queue(store, api)

        queue.markComplete("lesson-1")
        clock += 10
        queue.markComplete("lesson-2")
        clock += 10
        queue.markComplete("lesson-3")

        queue.flush()

        assertEquals(listOf("lesson-1", "lesson-2", "lesson-3"), api.sentLessons)
    }

    // --- durability ---------------------------------------------------------

    @Test
    fun `the queue survives the process`() = runTest {
        // A student who answers underground and closes the app has, from their
        // point of view, answered.
        val file = File(folder.newFolder(), "pending.json")
        val api = FakeApi(failWith = ApiError.Offline)

        queue(FilePendingActionStore(file), api).submitQuiz("lesson-1", listOf(QuizAnswer("q1", "o1")))

        val reloaded = FilePendingActionStore(file).read()
        assertEquals(1, reloaded.size)
        assertTrue((reloaded.single() as PendingAction.SubmitQuiz).idempotencyKey.isNotBlank())
    }

    @Test
    fun `an unreadable queue file does not block everything after it`() {
        val file = File(folder.newFolder(), "pending.json")
        file.writeText("{ this is not the queue")

        // Empty rather than an exception: retrying an unparseable file for ever
        // would block every record made after it.
        assertEquals(emptyList<PendingAction>(), FilePendingActionStore(file).read())
        assertTrue("kept for diagnosis rather than deleted", file.exists())
    }

    // --- §6 limits ----------------------------------------------------------

    @Test
    fun `an over-long comment is trimmed rather than left to be discarded`() = runTest {
        // Over 1000 characters is a 400 — permanent — so the queue would throw
        // the whole verdict away. Losing the tail of a comment is better than
        // losing the answer.
        val store = InMemoryPendingActionStore()
        queue(store, FakeApi()).rateLesson("lesson-1", understood = false, comment = "あ".repeat(2000))

        val rating = store.read().single() as PendingAction.RateLesson
        assertEquals(LearningActionQueue.MAX_COMMENT_LENGTH, rating.comment!!.length)
    }

    // --- helpers -----------------------------------------------------------

    private fun queue(store: PendingActionStore, api: FakeApi) = LearningActionQueue(
        store = store,
        content = ContentClient(api, AppEnvironment.Production),
        now = { clock },
        newId = { "id-${ids++}" },
    )

    private class FakeApi(
        var failWith: ApiError? = null,
        private val failLesson: Pair<String, ApiError>? = null,
    ) : HttpClient {
        val sentLessons = mutableListOf<String>()
        val idempotencyKeys = mutableListOf<String>()

        override suspend fun send(request: HttpRequest): HttpResponse {
            val path = request.url.substringAfter("api.manabu2.com")
            val lessonId = path.removePrefix("/api/v1/lessons/").substringBefore('/')

            failWith?.let { throw it }
            failLesson?.let { (id, error) -> if (id == lessonId) throw error }

            sentLessons += lessonId
            request.headers["Idempotency-Key"]?.let { idempotencyKeys += it }

            return HttpResponse(200, """{"lessonId":"$lessonId","courseId":"course-1","isCompleted":true,
                "attemptId":"a","quizId":"q","scorePercent":80,"passingScorePercent":60,"isPassed":true}""")
        }
    }

    // --- submitting now -----------------------------------------------------

    @Test
    fun `a graded sitting leaves nothing queued`() = runTest {
        val store = InMemoryPendingActionStore()
        val outcome = queue(store, FakeApi()).submitQuizNow("lesson-1", listOf(QuizAnswer("q1", "o1")))

        assertTrue(outcome is QuizOutcome.Graded)
        assertEquals(80, (outcome as QuizOutcome.Graded).result.scorePercent)
        assertTrue(store.read().isEmpty())
    }

    @Test
    fun `a sitting is queued before it is sent`() = runTest {
        // The order is the promise. A student who answers underground and
        // closes the app has, from their point of view, answered.
        val store = InMemoryPendingActionStore()
        val outcome = queue(store, FakeApi(failWith = ApiError.Offline))
            .submitQuizNow("lesson-1", listOf(QuizAnswer("q1", "o1")))

        assertEquals(QuizOutcome.Held, outcome)
        assertEquals(1, store.read().size)
    }

    @Test
    fun `no score is produced when it could not be sent`() = runTest {
        // §5: 「架空の点数を見せるより、採点できないと認めるほうがましです」. There is no
        // branch here that can return a number without the server.
        val outcome = queue(InMemoryPendingActionStore(), FakeApi(failWith = ApiError.Offline))
            .submitQuizNow("lesson-1", listOf(QuizAnswer("q1", "o1")))

        assertTrue(outcome !is QuizOutcome.Graded)
    }

    @Test
    fun `a permanently refused sitting is dropped rather than left to block the queue`() = runTest {
        val store = InMemoryPendingActionStore()
        val outcome = queue(store, FakeApi(failWith = ApiError.Http(400, null)))
            .submitQuizNow("lesson-1", listOf(QuizAnswer("q1", "o1")))

        assertEquals(QuizOutcome.Rejected, outcome)
        assertTrue("a dead entry must not hold up the ones behind it", store.read().isEmpty())
    }

    @Test
    fun `a held sitting keeps its key for the later send`() = runTest {
        val store = InMemoryPendingActionStore()
        val api = FakeApi(failWith = ApiError.Offline)

        queue(store, api).submitQuizNow("lesson-1", listOf(QuizAnswer("q1", "o1")))
        val heldKey = (store.read().single() as PendingAction.SubmitQuiz).idempotencyKey

        api.failWith = null
        queue(store, api).flush()

        assertEquals(listOf(heldKey), api.idempotencyKeys)
    }
}
