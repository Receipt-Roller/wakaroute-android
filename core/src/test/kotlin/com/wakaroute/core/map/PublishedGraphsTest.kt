package com.wakaroute.core.map

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.net.ApiError
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
import com.wakaroute.core.net.HttpResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Publishing a 教科 without shipping an app.
 *
 * 国語・英語・理科・社会 have content in MANABU2 and no authored prerequisite
 * edges. When the edges are written, the map should appear on phones already
 * installed — which means the app must take the graph from the server and must
 * be **exactly as suspicious of it** as of the one it ships with.
 *
 * The rule these are really protecting is 共通判断規則 §1: a 教科 with no edges
 * must never be drawn, because with no edges every 要素 is `Ready` and the map
 * answers 「つまずきはありません」 for a subject nobody has mapped.
 */
class PublishedGraphsTest {

    @Test
    fun `a published subject appears without a new build`() = runTest {
        // Nothing about 国語 is compiled into the app. This is the whole feature.
        val store = InMemoryPrerequisiteGraphStore()
        val catalogue = PublishedGraphCatalogue(store)
        val repository = GraphUnderstandingMapRepository(catalogue)

        assertEquals(SubjectMapState.ComingSoon, repository.state(SchoolSubject.Japanese))

        sync(store, serving(japanese(withEdges = true))).refresh()

        val state = repository.state(SchoolSubject.Japanese)
        assertTrue("国語 should be drawable once its edges are published", state is SubjectMapState.Available)
        assertEquals(2, (state as SubjectMapState.Available).subject.elements.size)
    }

    @Test
    fun `a subject whose elements have no prerequisites stays 準備中`() = runTest {
        // The trap. Content being ready in MANABU2 is not the same as the edges
        // being authored, and a graph that lists every 要素 and no dependency
        // between them is the easiest thing in the world to publish by mistake.
        //
        // Drawn, it would tell a 中学生 that nothing in 国語 is in their way.
        val store = InMemoryPrerequisiteGraphStore()
        sync(store, serving(japanese(withEdges = false))).refresh()

        val state = GraphUnderstandingMapRepository(PublishedGraphCatalogue(store))
            .state(SchoolSubject.Japanese)

        assertEquals(SubjectMapState.ComingSoon, state)
    }

    @Test
    fun `a broken published graph is refused before it is stored`() = runTest {
        val store = InMemoryPrerequisiteGraphStore()
        val result = sync(store, serving(japaneseWithDanglingEdge())).refresh()

        assertTrue("国語 should have been rejected", result.rejected.containsKey("国語"))
        assertTrue("nothing broken may reach the store", store.read().isEmpty())
    }

    @Test
    fun `a bad publish cannot take away a subject students already had`() = runTest {
        // Someone republishes 国語 with a dangling edge. The students who
        // already have the good copy keep it rather than watching a 教科
        // disappear because of a mistake made elsewhere.
        val store = InMemoryPrerequisiteGraphStore()
        sync(store, serving(japanese(withEdges = true))).refresh()

        sync(store, serving(japaneseWithDanglingEdge())).refresh()

        val state = GraphUnderstandingMapRepository(PublishedGraphCatalogue(store))
            .state(SchoolSubject.Japanese)
        assertTrue(state is SubjectMapState.Available)
    }

    @Test
    fun `a withdrawn subject goes back to 準備中`() = runTest {
        // An empty list from a working endpoint is a real answer, not a
        // failure, and has to be able to unpublish.
        val store = InMemoryPrerequisiteGraphStore()
        sync(store, serving(japanese(withEdges = true))).refresh()

        sync(store, serving()).refresh()

        assertEquals(
            SubjectMapState.ComingSoon,
            GraphUnderstandingMapRepository(PublishedGraphCatalogue(store)).state(SchoolSubject.Japanese),
        )
    }

    @Test
    fun `being offline keeps what was already published`() = runTest {
        val store = InMemoryPrerequisiteGraphStore()
        sync(store, serving(japanese(withEdges = true))).refresh()

        val result = sync(store, Offline).refresh()

        assertEquals(ApiError.Offline, result.failure)
        assertTrue(
            "an unreachable server must not empty the map",
            GraphUnderstandingMapRepository(PublishedGraphCatalogue(store))
                .state(SchoolSubject.Japanese) is SubjectMapState.Available,
        )
    }

    @Test
    fun `数学 still works with nothing published`() = runTest {
        // The bundled graph is what a student sees on first launch, before any
        // fetch, and on a phone that never reaches the network again.
        val state = GraphUnderstandingMapRepository(
            PublishedGraphCatalogue(InMemoryPrerequisiteGraphStore()),
        ).state(SchoolSubject.Math)

        assertTrue(state is SubjectMapState.Available)
    }

    @Test
    fun `a published 数学 graph overrides the bundled one`() = runTest {
        // Otherwise a correction to the edges would need a release, which is
        // the thing this whole path exists to avoid.
        val store = InMemoryPrerequisiteGraphStore()
        sync(store, serving(math(asOf = "2099-01-01"))).refresh()

        val state = GraphUnderstandingMapRepository(PublishedGraphCatalogue(store))
            .state(SchoolSubject.Math)

        assertEquals("2099-01-01", (state as SubjectMapState.Available).asOf)
    }

    // --- helpers -----------------------------------------------------------

    private fun sync(store: PrerequisiteGraphStore, http: HttpClient) =
        PrerequisiteGraphSync(PrerequisiteGraphClient(http, AppEnvironment.Production), store)

    private fun serving(vararg graphs: PrerequisiteGraph) = object : HttpClient {
        override suspend fun send(request: HttpRequest) = HttpResponse(
            200,
            com.wakaroute.core.net.WakaRouteJson.encodeToString(
                PublishedGraphs.serializer(),
                PublishedGraphs(asOf = "2026-08-08", graphs = graphs.toList()),
            ),
        )
    }

    private object Offline : HttpClient {
        override suspend fun send(request: HttpRequest): HttpResponse = throw ApiError.Offline
    }

    /** Invented ids: the repository is public and must carry no real ones. */
    private fun japanese(withEdges: Boolean) = PrerequisiteGraph(
        asOf = "2026-08-08",
        subject = "国語",
        domains = listOf(PrerequisiteGraph.Domain("A", "読む", "TEST-PATH-0000001")),
        elements = listOf(
            PrerequisiteGraph.Element("TEST-COURSE-0000001", "説明文を読む", "A", 1),
            PrerequisiteGraph.Element(
                courseId = "TEST-COURSE-0000002",
                title = "論説文を読む",
                domain = "A",
                grade = 2,
                requires = if (withEdges) listOf("TEST-COURSE-0000001") else emptyList(),
            ),
        ),
    )

    private fun japaneseWithDanglingEdge() = japanese(withEdges = true).let {
        it.copy(
            elements = it.elements.map { element ->
                element.copy(requires = element.requires.map { _ -> "TEST-COURSE-9999999" })
            },
        )
    }

    private fun math(asOf: String) = PrerequisiteGraph.math().copy(asOf = asOf)
}
