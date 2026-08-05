package com.wakaroute.core.content

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
import com.wakaroute.core.net.WakaRouteJson
import com.wakaroute.core.net.retryingReads
import com.wakaroute.core.net.sendDecoding
import kotlinx.serialization.builtins.ListSerializer

/**
 * The learner-facing content and progress endpoints.
 *
 * Note which endpoints are **not** here. `GET /api/v1/me/paths` returns an
 * empty array for a device-registered learner and `GET /api/v1/me/assignments`
 * returns 140 unrelated corporate courses (LMS-DEV t-d1bea82, confirmed on
 * production 2026-08-05). Neither can tell us which 領域 a course belongs to.
 *
 * The bundled prerequisite graph answers that instead: it names the four 数学
 * paths by id, and [pathDetail] expands each into its courses. Four requests,
 * and the mapping comes from data we authored and can test — which is a better
 * position than the workaround the ticket suggests.
 */
class ContentClient(
    private val http: HttpClient,
    private val environment: AppEnvironment,
) {
    /** Every course the learner has started. Empty before they start anything. */
    suspend fun progress(): List<CourseProgress> = get("/api/v1/me/progress") { request ->
        http.sendDecoding(request, ListSerializer(CourseProgress.serializer()))
    }

    /**
     * The learner's own quiz attempts, newest first.
     *
     * Every attempt, not just the latest — the app needs the history to decide
     * which sitting counts per lesson, and that rule lives in
     * [com.wakaroute.core.map.MasteryDerivation] rather than being assumed from
     * the server's ordering.
     */
    suspend fun quizAttempts(): List<QuizAttempt> = get("/api/v1/me/quiz-attempts") { request ->
        http.sendDecoding(request, ListSerializer(QuizAttempt.serializer()))
    }

    suspend fun pathDetail(pathId: String): PathDetail = get("/api/v1/paths/$pathId") { request ->
        http.sendDecoding(request, PathDetail.serializer())
    }

    suspend fun courseDetail(courseId: String): CourseDetail = get("/api/v1/courses/$courseId") { request ->
        http.sendDecoding(request, CourseDetail.serializer())
    }

    suspend fun lessonDetail(lessonId: String): LessonDetail = get("/api/v1/lessons/$lessonId") { request ->
        http.sendDecoding(request, LessonDetail.serializer())
    }

    suspend fun courseProgress(courseId: String): CourseProgressDetail =
        get("/api/v1/me/progress/$courseId") { request ->
            http.sendDecoding(request, CourseProgressDetail.serializer())
        }

    /**
     * Records that the lesson was opened.
     *
     * Documented as safe to call on every open, and it is called that way. No
     * retry and no queue: a lost 「開いた」 costs nothing a student would notice,
     * and treating it as important would put a spinner in front of reading.
     */
    suspend fun markViewed(lessonId: String) {
        http.send(postRequest("/api/v1/lessons/$lessonId/view"))
    }

    /** Marks the lesson complete and returns the course's updated progress. */
    suspend fun markComplete(lessonId: String): LessonCompletion = http.sendDecoding(
        postRequest("/api/v1/lessons/$lessonId/complete"),
        LessonCompletion.serializer(),
    )

    /**
     * Submits quiz answers and returns the **server's** grade.
     *
     * [idempotencyKey] is required, not optional, and the caller supplies it —
     * so that a retry can present the *same* key. This endpoint creates a new
     * attempt every time it is called, which means a request that timed out and
     * was re-sent is recorded as a second sitting: it changes the student's
     * 理解度, and 修了証 conditions depend on the attempt history.
     *
     * With the same key the server returns the first response instead. That is
     * only true if the key is stored **with the queued submission** rather than
     * generated at send time — a fresh key on the retry is exactly the bug this
     * parameter exists to make impossible to write by accident.
     */
    suspend fun submitQuiz(
        lessonId: String,
        answers: List<QuizAnswer>,
        idempotencyKey: String,
    ): QuizResult {
        require(idempotencyKey.isNotBlank()) { "a quiz submission needs an Idempotency-Key" }

        return http.sendDecoding(
            postRequest(
                path = "/api/v1/lessons/$lessonId/quiz/submit",
                body = WakaRouteJson.encodeToString(QuizSubmission.serializer(), QuizSubmission(answers)),
                headers = mapOf("Idempotency-Key" to idempotencyKey),
            ),
            QuizResult.serializer(),
        )
    }

    /**
     * Records a session that has already finished.
     *
     * Deliberately not `/study-sessions/start` and `/stop`. Driving the
     * server's clock means the timer needs a connection to begin — and the
     * student this feature is for is on a train. The session runs on the
     * device and is posted whole, with a [RecordStudySession.clientSessionId]
     * the server deduplicates replays on.
     */
    suspend fun recordStudySession(session: RecordStudySession): StudySession = http.sendDecoding(
        postRequest(
            path = "/api/v1/me/study-sessions",
            body = WakaRouteJson.encodeToString(RecordStudySession.serializer(), session),
            // Belt as well as braces: clientSessionId already dedupes, and this
            // makes a resend cheap on the server rather than merely harmless.
            headers = mapOf("Idempotency-Key" to session.clientSessionId),
        ),
        StudySession.serializer(),
    )

    /** Consecutive days studied — computed server-side, so it survives a new phone. */
    suspend fun studyStreak(): StudyStreak = get("/api/v1/me/study-streak") { request ->
        http.sendDecoding(request, StudyStreak.serializer())
    }

    /** A dense day-by-day series, zero days included. */
    suspend fun studySummary(from: String, to: String): List<StudyDay> =
        get("/api/v1/me/study-summary?from=$from&to=$to&granularity=day") { request ->
            http.sendDecoding(request, ListSerializer(StudyDay.serializer()))
        }

    /**
     * `POST /api/v1/lessons/{id}/feedback` — 「わかった」/「むずかしかった」.
     *
     * `reasons` is omitted when [understood] is true, because the server
     * discards it there. Sending a value that will be thrown away makes the
     * request say something the caller did not mean.
     */
    suspend fun rateLesson(
        lessonId: String,
        understood: Boolean,
        reasons: List<String> = emptyList(),
        comment: String? = null,
    ) {
        val body = LessonFeedbackRequest(
            understood = understood,
            reasons = if (understood) emptyList() else reasons,
            comment = comment,
        )

        http.sendDecoding(
            postRequest(
                path = "/api/v1/lessons/$lessonId/feedback",
                body = WakaRouteJson.encodeToString(LessonFeedbackRequest.serializer(), body),
            ),
            LessonFeedbackAcknowledgement.serializer(),
        )
    }

    /**
     * Writes are never retried here.
     *
     * A read that fails can be repeated harmlessly; a write that timed out may
     * already have been applied. Retrying is a decision for the offline queue,
     * which has the idempotency key to do it safely.
     */
    private fun postRequest(
        path: String,
        body: String = "{}",
        headers: Map<String, String> = emptyMap(),
    ) = HttpRequest(
        method = HttpRequest.Method.POST,
        url = environment.manabu2BaseUrl.trimEnd('/') + path,
        headers = mapOf("Accept" to "application/json", "Content-Type" to "application/json") + headers,
        body = body,
    )

    private suspend fun <T> get(path: String, send: suspend (HttpRequest) -> T): T = retryingReads {
        send(
            HttpRequest(
                method = HttpRequest.Method.GET,
                url = environment.manabu2BaseUrl.trimEnd('/') + path,
                headers = mapOf("Accept" to "application/json"),
            ),
        )
    }
}
