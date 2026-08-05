package com.wakaroute.app.feature.schools

import com.wakaroute.core.net.ApiError
import com.wakaroute.core.schools.School
import com.wakaroute.core.schools.SchoolDetail
import com.wakaroute.core.schools.SchoolOwnership
import com.wakaroute.core.schools.SchoolSearchPage
import com.wakaroute.core.schools.SchoolSearchQuery
import com.wakaroute.core.schools.SchoolsRepository
import com.wakaroute.core.schools.SearchPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The 読み込み中・成功・0件・失敗・再試行 state machine required by §7.
 *
 * Runs on the JVM with no device, which is why `SearchPreferences` is an
 * interface in `core` rather than the Android `DataStore` class directly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SchoolSearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a page of results becomes a success state`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeSchools(page(listOf(school("TEST-0000001"), school("TEST-0000002")))))
        advanceUntilIdle()

        val state = viewModel.state.value as SearchUiState.Success
        assertEquals(2, state.schools.size)
        assertEquals("2025-05-01", state.asOf)
    }

    @Test
    fun `no results is its own state, not an empty success`() = runTest(dispatcher) {
        // So the screen can say 0件 plainly, and offer to clear the filters —
        // rather than showing an empty list that looks like a broken app.
        val viewModel = viewModel(FakeSchools(page(emptyList())))
        advanceUntilIdle()

        assertEquals(SearchUiState.Empty, viewModel.state.value)
    }

    @Test
    fun `each kind of failure keeps its own advice`() = runTest(dispatcher) {
        // 「電波がない」 and 「サーバーが落ちている」 lead a student to different next
        // steps. One generic error message would tell them nothing.
        val cases = mapOf(
            ApiError.Offline to SearchFailure.Offline,
            ApiError.TimedOut to SearchFailure.TimedOut,
            ApiError.Http(503, null) to SearchFailure.Server,
            ApiError.Http(429, null) to SearchFailure.Server,
            ApiError.Http(400, null) to SearchFailure.Rejected,
            ApiError.Decoding("bad shape") to SearchFailure.Unreadable,
        )

        for ((error, expected) in cases) {
            val viewModel = viewModel(FakeSchools(error = error))
            advanceUntilIdle()

            assertEquals(expected, (viewModel.state.value as SearchUiState.Failed).failure)
        }
    }

    @Test
    fun `only failures that could succeed on a second try offer a retry`() {
        assertTrue(SearchFailure.Offline.canRetry)
        assertTrue(SearchFailure.TimedOut.canRetry)
        assertTrue(SearchFailure.Server.canRetry)

        // Asking for the same rejected thing again cannot help, and a button
        // that never works is worse than no button.
        assertTrue(!SearchFailure.Rejected.canRetry)
        assertTrue(!SearchFailure.Unreadable.canRetry)
    }

    @Test
    fun `the last conditions are restored on launch`() {
        runTest(dispatcher) {
            val preferences = FakeSearchPreferences(
                SchoolSearchQuery(keyword = "テスト", prefectureCode = "13", ownership = SchoolOwnership.Public),
            )
            val schools = FakeSchools(page(listOf(school("TEST-0000001"))))
            val viewModel = viewModel(schools, preferences)
            advanceUntilIdle()

            assertEquals("テスト", viewModel.query.value.keyword)
            assertEquals("13", viewModel.query.value.prefectureCode)
            // And the restored conditions are actually applied to the request,
            // rather than being shown while the app searches for everything.
            assertEquals("テスト", schools.queries.first().keyword)
        }
    }

    @Test
    fun `clearing the filters clears what was stored, not just the screen`() = runTest(dispatcher) {
        // Otherwise the filters come back on the next launch, and the student
        // has no way to tell why their search is narrow.
        val preferences = FakeSearchPreferences(SchoolSearchQuery(keyword = "テスト"))
        val viewModel = viewModel(FakeSchools(page(listOf(school("TEST-0000001")))), preferences)
        advanceUntilIdle()

        viewModel.clearFilters()
        advanceUntilIdle()

        assertTrue(viewModel.query.value.isEmpty)
        assertTrue(preferences.stored.value.isEmpty)
    }

    @Test
    fun `a search always starts from page one`() = runTest(dispatcher) {
        // Changing the keyword while on page 4 and keeping the page number
        // shows a student the middle of a list they have never seen.
        val schools = FakeSchools(page(listOf(school("TEST-0000001")), totalPages = 5))
        val viewModel = viewModel(schools)
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        viewModel.setKeyword("べつの学校")
        viewModel.search()
        advanceUntilIdle()

        assertEquals(1, schools.queries.last().normalizedPage)
    }

    @Test
    fun `a failure while paging keeps the results already on screen`() = runTest(dispatcher) {
        // Replacing a working list with an error because page 2 timed out takes
        // away what the student already had.
        val schools = FakeSchools(
            page(listOf(school("TEST-0000001")), totalPages = 5),
            errorAfterFirst = ApiError.TimedOut,
        )
        val viewModel = viewModel(schools)
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        val state = viewModel.state.value as SearchUiState.Success
        assertEquals(1, state.schools.size)
        assertTrue(!state.isLoadingMore)
    }

    @Test
    fun `results are appended when paging succeeds`() = runTest(dispatcher) {
        val schools = FakeSchools(page(listOf(school("TEST-0000001")), totalPages = 5))
        val viewModel = viewModel(schools)
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(2, (viewModel.state.value as SearchUiState.Success).schools.size)
    }

    // --- helpers -----------------------------------------------------------

    private fun viewModel(
        schools: SchoolsRepository,
        preferences: SearchPreferences = FakeSearchPreferences(),
    ) = SchoolSearchViewModel(schools, preferences)

    /** Invented ids only. This repository is public. */
    private fun school(id: String) = School(id = id, name = "テスト高等学校 $id")

    private fun page(items: List<School>, totalPages: Int = 1) = SchoolSearchPage(
        asOf = "2025-05-01",
        totalCount = items.size,
        page = 1,
        pageSize = 24,
        totalPages = totalPages,
        items = items,
    )

    private class FakeSchools(
        private val page: SchoolSearchPage? = null,
        private val error: ApiError? = null,
        private val errorAfterFirst: ApiError? = null,
    ) : SchoolsRepository {
        val queries = mutableListOf<SchoolSearchQuery>()

        override suspend fun search(query: SchoolSearchQuery): SchoolSearchPage {
            queries += query
            error?.let { throw it }
            if (queries.size > 1) errorAfterFirst?.let { throw it }
            return page ?: error("no page configured")
        }

        override suspend fun detail(id: String): SchoolDetail = error("not used")
    }

    private class FakeSearchPreferences(
        initial: SchoolSearchQuery = SchoolSearchQuery(),
    ) : SearchPreferences {
        val stored = MutableStateFlow(initial)

        override val lastSearch: Flow<SchoolSearchQuery> = stored

        override suspend fun saveSearch(query: SchoolSearchQuery) {
            stored.value = query
        }

        override suspend fun clearSearch() {
            stored.value = SchoolSearchQuery()
        }
    }
}
