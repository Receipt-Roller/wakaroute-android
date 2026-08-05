package com.wakaroute.core.map

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.content.ContentClient
import com.wakaroute.core.net.ApiError
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
import com.wakaroute.core.net.HttpResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The map with real data behind it.
 *
 * The rule under test throughout is that this **degrades in one direction
 * only**: a failure costs the student's record, never the structure, and the
 * result then says so rather than presenting an empty record as though it were
 * theirs.
 */
class LiveUnderstandingMapTest {

    private val graph = PrerequisiteGraph.math()
    private val courseIds = graph.elements.map { it.courseId }
    private val positiveNegative = graph.elements.first { it.title == "正の数・負の数" }.courseId

    @Test
    fun `titles come from live content, not the graph`() = runTest {
        // A course is free to be renamed, and the graph's title is a
        // diagnostic label. The id is the key and does not change.
        val api = FakeApi(renamed = mapOf(positiveNegative to "正負の数（新カリキュラム）"))
        val state = LiveUnderstandingMap(api.content()).load(SchoolSubject.Math) as SubjectMapState.Available

        assertEquals(
            "正負の数（新カリキュラム）",
            state.subject.element(ElementId(positiveNegative))!!.name,
        )
    }

    @Test
    fun `a course deleted in MANABU2 withholds the whole map`() = runTest {
        // Half a prerequisite graph sends students back to the wrong place.
        val api = FakeApi(missing = setOf(positiveNegative))
        val state = LiveUnderstandingMap(api.content()).load(SchoolSubject.Math)

        assertTrue(state is SubjectMapState.Unavailable)
        assertTrue((state as SubjectMapState.Unavailable).problems.single().contains("no longer exists"))
    }

    @Test
    fun `a real record is derived and carried through`() = runTest {
        val api = FakeApi(
            progress = """[{"courseId":"$positiveNegative","totalLessons":23,"completedLessons":23,"isCompleted":true}]""",
        )
        val state = LiveUnderstandingMap(api.content()).load(SchoolSubject.Math) as SubjectMapState.Available

        val progress = state.progress as LearnerProgress.Known
        assertEquals(MasteryLevel.SolvesBasics, progress.record[ElementId(positiveNegative)])
    }

    @Test
    fun `a network failure costs the record, never the structure`() = runTest {
        // The 要素 and their prerequisites are worth reading on a train, which
        // is why they are bundled rather than fetched.
        val api = FakeApi(failEverything = true)
        val state = LiveUnderstandingMap(api.content()).load(SchoolSubject.Math) as SubjectMapState.Available

        assertEquals(26, state.subject.elements.size)
        assertEquals(LearnerProgress.Unavailable, state.progress)
    }

    @Test
    fun `an unreadable record is never presented as an empty one`() = runTest {
        // The distinction this whole type exists for: "we could not read it"
        // and "you have done nothing" are different claims, and only one of
        // them is about the student.
        val api = FakeApi(failProgress = true)
        val state = LiveUnderstandingMap(api.content()).load(SchoolSubject.Math) as SubjectMapState.Available

        assertEquals(LearnerProgress.Unavailable, state.progress)
        assertTrue(state.progress !is LearnerProgress.Known)
    }

    @Test
    fun `subjects with no authored edges stay 準備中 without any request`() = runTest {
        val api = FakeApi()
        val state = LiveUnderstandingMap(api.content()).load(SchoolSubject.Japanese)

        assertEquals(SubjectMapState.ComingSoon, state)
        assertEquals("no network call for a subject we cannot map", 0, api.requests.size)
    }

    @Test
    fun `path details are fetched once and reused`() = runTest {
        // Four requests per map open would be felt on a phone, and the
        // catalogue does not change mid-session.
        val api = FakeApi()
        val map = LiveUnderstandingMap(api.content())

        map.load(SchoolSubject.Math)
        map.load(SchoolSubject.Math)

        assertEquals(4, api.requests.count { it.contains("/paths/") })
    }

    @Test
    fun `the learner endpoints that cannot answer this are never called`() = runTest {
        // /me/paths returns empty and /me/assignments returns 140 unrelated
        // corporate courses (t-d1bea82). The bundled graph answers instead.
        val api = FakeApi()
        LiveUnderstandingMap(api.content()).load(SchoolSubject.Math)

        assertTrue(api.requests.none { it.contains("/me/paths") })
        assertTrue(api.requests.none { it.contains("/me/assignments") })
    }

    // --- helpers -----------------------------------------------------------

    private inner class FakeApi(
        private val renamed: Map<String, String> = emptyMap(),
        private val missing: Set<String> = emptySet(),
        private val progress: String = "[]",
        private val failProgress: Boolean = false,
        private val failEverything: Boolean = false,
    ) : HttpClient {
        val requests = mutableListOf<String>()

        fun content() = ContentClient(this, AppEnvironment.Production)

        override suspend fun send(request: HttpRequest): HttpResponse {
            val path = request.url.substringAfter("api.manabu2.com")
            requests += path

            if (failEverything) throw ApiError.Offline

            return when {
                path.startsWith("/api/v1/paths/") -> HttpResponse(200, pathBody(path.substringAfterLast('/')))
                path == "/api/v1/me/progress" -> {
                    if (failProgress) throw ApiError.Offline
                    HttpResponse(200, progress)
                }
                path == "/api/v1/me/quiz-attempts" -> HttpResponse(200, "[]")
                else -> error("unexpected request to $path")
            }
        }

        private fun pathBody(pathId: String): String {
            val domain = graph.domains.first { it.pathId == pathId }
            val courses = graph.elements
                .filter { it.domain == domain.code && it.courseId !in missing }
                .joinToString(",") { element ->
                    val title = renamed[element.courseId] ?: element.title
                    """{"id":"${element.courseId}","title":"$title","lessonCount":5}"""
                }

            return """{"id":"$pathId","name":"${domain.name}","labels":[],
                       "courseCount":${courseIds.size},"courses":[$courses]}"""
        }
    }
}
