package com.wakaroute.core.content

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
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
