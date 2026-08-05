package com.wakaroute.core.content

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.net.ApiError
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
import com.wakaroute.core.net.HttpResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Shapes verified against production on 2026-08-05.
 *
 * The quiz submission tests are the ones that matter. Getting the idempotency
 * key wrong records a re-sent submission as a second sitting — which changes
 * the student's 理解度 and the 修了証 conditions, and looks like nothing at all
 * from the client.
 */
class ContentClientTest {

    @Test
    fun `a lesson decodes with its quiz and no answer key`() = runTest {
        val http = FakeApi(LESSON)
        val lesson = client(http).lessonDetail("lesson-1")

        assertEquals("0より小さい数はどこにある？", lesson.title)
        assertEquals(5, lesson.quiz!!.questions.single().options.size)

        // Grading is the server's. Nothing here could compute a score even if
        // someone wanted to, which is what §5 asks for.
        val body = http.requests.single()
        assertTrue(body.url.endsWith("/api/v1/lessons/lesson-1"))
    }

    @Test
    fun `a course decodes and its lessons come out in teaching order`() = runTest {
        val course = client(FakeApi(COURSE)).courseDetail("course-1")

        assertEquals(
            listOf("2つ目のまとまりの1", "1つ目のまとまりの1", "1つ目のまとまりの2").sorted(),
            course.lessons.map { it.title }.sorted(),
        )
        // Sections out of order in the payload; teaching order restored.
        assertEquals("1つ目のまとまりの1", course.lessons.first().title)
        assertEquals("2つ目のまとまりの1", course.lessons.last().title)
    }

    @Test
    fun `submitting a quiz carries the caller's idempotency key`() = runTest {
        val http = FakeApi(QUIZ_RESULT)
        client(http).submitQuiz(
            lessonId = "lesson-1",
            answers = listOf(QuizAnswer(questionId = "q1", optionId = "o3")),
            idempotencyKey = "key-1",
        )

        assertEquals("key-1", http.requests.single().headers["Idempotency-Key"])
    }

    @Test
    fun `a resend presents the same key rather than a new one`() = runTest {
        // The whole point of the parameter. This endpoint creates a new attempt
        // on every call; the same key makes the server return the first result
        // instead of recording a second sitting.
        val http = FakeApi(QUIZ_RESULT)
        val client = client(http)
        val answers = listOf(QuizAnswer(questionId = "q1", optionId = "o3"))

        client.submitQuiz("lesson-1", answers, idempotencyKey = "key-1")
        client.submitQuiz("lesson-1", answers, idempotencyKey = "key-1")

        assertEquals(listOf("key-1", "key-1"), http.requests.map { it.headers["Idempotency-Key"] })
    }

    @Test
    fun `a submission without a key is refused before it leaves the device`() = runTest {
        val http = FakeApi(QUIZ_RESULT)

        try {
            client(http).submitQuiz("lesson-1", emptyList(), idempotencyKey = "  ")
            fail("expected a blank key to be rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Idempotency-Key"))
        }

        assertEquals("nothing may reach the network without a key", 0, http.requests.size)
    }

    @Test
    fun `the graded result is the server's, decoded as sent`() = runTest {
        val result = client(FakeApi(QUIZ_RESULT)).submitQuiz(
            "lesson-1",
            listOf(QuizAnswer("q1", "o3")),
            idempotencyKey = "key-1",
        )

        assertEquals(80, result.scorePercent)
        assertEquals(60, result.passingScorePercent)
        assertTrue(result.isPassed)
    }

    @Test
    fun `writes are not retried`() = runTest {
        // A read that fails can be repeated harmlessly. A write that timed out
        // may already have been applied, and deciding what to do about that
        // belongs to the queue that holds the idempotency key.
        val http = FakeApi(QUIZ_RESULT, failWith = ApiError.TimedOut)

        try {
            client(http).submitQuiz("lesson-1", listOf(QuizAnswer("q1", "o3")), "key-1")
            fail("expected the timeout to propagate")
        } catch (e: ApiError) {
            assertEquals(ApiError.TimedOut, e)
        }

        assertEquals(1, http.requests.size)
    }

    @Test
    fun `reads are retried`() = runTest {
        val http = FakeApi(LESSON, failWith = ApiError.TimedOut, failuresBeforeSuccess = 1)
        client(http).lessonDetail("lesson-1")

        assertEquals(2, http.requests.size)
    }

    @Test
    fun `a quiz never sat is distinguishable from one failed`() = runTest {
        // Three states, not two. §3 turns on exactly this: a quiz not yet taken
        // is not a stumble.
        val detail = client(FakeApi(COURSE_PROGRESS)).courseProgress("course-1")

        assertNull(detail.forLesson("lesson-untouched")!!.latestQuizPassed)
        assertEquals(false, detail.forLesson("lesson-missed")!!.latestQuizPassed)
        assertEquals(true, detail.forLesson("lesson-passed")!!.latestQuizPassed)
    }

    // --- helpers -----------------------------------------------------------

    private fun client(http: HttpClient) = ContentClient(http, AppEnvironment.Production)

    private class FakeApi(
        private val body: String,
        private val failWith: ApiError? = null,
        private val failuresBeforeSuccess: Int = Int.MAX_VALUE,
    ) : HttpClient {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun send(request: HttpRequest): HttpResponse {
            requests += request
            if (failWith != null && requests.size <= failuresBeforeSuccess) throw failWith
            return HttpResponse(200, body)
        }
    }

    private companion object {
        /** Structure copied from production; wording written here. */
        const val LESSON = """
        {
          "id":"lesson-1","sectionId":"section-1","courseId":"course-1",
          "title":"0より小さい数はどこにある？","summary":"負の数が必要になる理由を理解する。",
          "bodyHtml":"<h1>0より小さい数はどこにある？</h1><p>本文です。</p>",
          "videoUrl":null,"slidesEmbedUrl":null,"orderIndex":0,"culture":"ja-JP","materials":[],
          "quiz":{"id":"quiz-1","title":"確認クイズ","instructions":null,
                  "passingScorePercent":60,"isRequired":false,
                  "questions":[{"id":"q1","questionText":"負の数とは？","imageUrl":null,
                                "questionType":"MultipleChoiceSingle","orderIndex":0,
                                "options":[{"id":"o1","text":"あ","orderIndex":1},
                                           {"id":"o2","text":"い","orderIndex":2},
                                           {"id":"o3","text":"う","orderIndex":3},
                                           {"id":"o4","text":"え","orderIndex":4},
                                           {"id":"o5","text":"お","orderIndex":5}]}]}
        }
        """

        const val COURSE = """
        {
          "id":"course-1","title":"テスト要素","lessonCount":3,
          "sections":[
            {"id":"s2","title":"2つ目","orderIndex":1,
             "lessons":[{"id":"l3","sectionId":"s2","title":"2つ目のまとまりの1","orderIndex":0,"hasQuiz":false}]},
            {"id":"s1","title":"1つ目","orderIndex":0,
             "lessons":[{"id":"l2","sectionId":"s1","title":"1つ目のまとまりの2","orderIndex":1,"hasQuiz":false},
                        {"id":"l1","sectionId":"s1","title":"1つ目のまとまりの1","orderIndex":0,"hasQuiz":true}]}
          ]
        }
        """

        const val QUIZ_RESULT = """
        {"attemptId":"attempt-1","quizId":"quiz-1","scorePercent":80,
         "passingScorePercent":60,"isPassed":true,"completedAt":"2026-08-05T10:00:00.1234567+00:00"}
        """

        const val COURSE_PROGRESS = """
        {
          "courseId":"course-1","courseTitle":"テスト要素","totalLessons":3,"completedLessons":2,
          "isCompleted":false,
          "lessons":[
            {"lessonId":"lesson-untouched","title":"まだ","isViewed":false,"isCompleted":false,
             "latestQuizPassed":null,"latestQuizScorePercent":null},
            {"lessonId":"lesson-missed","title":"落とした","isViewed":true,"isCompleted":true,
             "latestQuizPassed":false,"latestQuizScorePercent":40},
            {"lessonId":"lesson-passed","title":"受かった","isViewed":true,"isCompleted":true,
             "latestQuizPassed":true,"latestQuizScorePercent":90}
          ]
        }
        """
    }
}
