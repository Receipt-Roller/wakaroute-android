package com.wakaroute.core.journal

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.net.ApiError
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
import com.wakaroute.core.net.HttpResponse
import com.wakaroute.core.net.ProblemDetails
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 受験日記. Shapes and refusals verified on production on 2026-10-06; content invented.
 *
 * The promise these protect: **what a student writes is not lost to the signal**,
 * and is never filed under the wrong day or the wrong account.
 */
class JournalTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val today = JournalDates.today()

    // --- dates -------------------------------------------------------------

    @Test
    fun `today and the 31 days before it are writable, nothing else`() {
        assertTrue(JournalDates.isWritable(today, today))
        assertTrue(JournalDates.isWritable(today.minusDays(31), today))
        assertFalse(JournalDates.isWritable(today.minusDays(32), today))
        assertFalse("the journal records days that happened", JournalDates.isWritable(today.plusDays(1), today))
    }

    // --- the client ----------------------------------------------------------

    @Test
    fun `a diary save sends every field, with blanks as nulls`() = runTest {
        // A full replace: a field left out is a field erased.
        val api = FakeJournalApi()
        client(api).saveDiary(DiaryDraft(achievements = "  二次関数  ", focus = 4), today)

        assertEquals("PUT /$today/diary", api.calls.single())
        assertEquals(
            """{"achievements":"二次関数","struggles":null,"tomorrowPlan":null,"focus":4,"fatigue":null}""",
            api.bodies.single(),
        )
    }

    @Test
    fun `clearing every field deletes the diary`() = runTest {
        // The server answers 400 to an empty diary.
        val api = FakeJournalApi()
        client(api).saveDiary(DiaryDraft(), today)

        assertEquals("DELETE /$today/diary", api.calls.single())
    }

    @Test
    fun `a day the server will refuse is refused before anything is sent`() = runTest {
        val api = FakeJournalApi()
        try {
            client(api).addEntry(entry("e1"), today.plusDays(1))
            fail("expected a refusal")
        } catch (e: ApiError) {
            assertEquals(JournalRefusal.DateNotWritable, JournalRefusal.of(e))
        }
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `refusals are read from the code, not the prose`() {
        val dayFull = ApiError.Http(400, ProblemDetails(code = "day_full", detail = "wording that may change"))

        assertEquals(JournalRefusal.DayFull, JournalRefusal.of(dayFull))
        assertNull(JournalRefusal.of(ApiError.Offline))
    }

    @Test
    fun `a day decodes as the server sends it`() = runTest {
        val day = client(FakeJournalApi(dayBody = DAY)).day(today)

        assertEquals("二次関数の解の公式", day.diary!!.achievements)
        assertEquals(30, day.totals.studySelfMinutes)
        assertEquals("c1", day.entries.single().clientEntryId)
    }

    // --- the outbox ----------------------------------------------------------

    @Test
    fun `a newer diary save replaces an older unsent one`() = runTest {
        val store = InMemoryJournalOutboxStore()
        val outbox = outbox(store, FakeJournalApi())

        outbox.queueDiary(DiaryDraft(achievements = "朝"), today)
        outbox.queueDiary(DiaryDraft(achievements = "夜"), today)

        val diary = store.read().single().payload as PendingJournalWrite.Payload.Diary
        assertEquals("夜", diary.draft.achievements)
    }

    @Test
    fun `time blocks accumulate`() = runTest {
        val store = InMemoryJournalOutboxStore()
        val outbox = outbox(store, FakeJournalApi())

        outbox.queueEntry(entry("e1"), today)
        outbox.queueEntry(entry("e2"), today)

        assertEquals(2, store.read().size)
    }

    @Test
    fun `a temporary failure stops the run with the order kept`() = runTest {
        val store = InMemoryJournalOutboxStore()
        val api = FakeJournalApi(failWith = ApiError.Offline)
        val outbox = outbox(store, api)
        outbox.queueEntry(entry("e1"), today)
        outbox.queueEntry(entry("e2"), today)

        assertEquals(2, outbox.flush())
        assertEquals(1, api.calls.size)

        api.failWith = null
        assertEquals(0, outbox.flush())
        assertTrue(store.read().isEmpty())
    }

    @Test
    fun `a permanently refused write is set aside, and the rest still go`() = runTest {
        val store = InMemoryJournalOutboxStore()
        val api = FakeJournalApi(refuseEntry = "e1")
        val outbox = outbox(store, api)
        outbox.queueEntry(entry("e1"), today)
        outbox.queueEntry(entry("e2"), today)

        assertEquals(0, outbox.flush())
        assertEquals(listOf("POST /$today/entries", "POST /$today/entries"), api.calls)
        assertTrue(store.read().single().rejected)
    }

    @Test
    fun `the outbox survives the process`() = runTest {
        val file = File(folder.root, "journal-outbox.json")
        outbox(FileJournalOutboxStore(file), FakeJournalApi()).queueDiary(DiaryDraft(struggles = "比例"), today)

        val reloaded = FileJournalOutboxStore(file).read().single().payload as PendingJournalWrite.Payload.Diary
        assertEquals("比例", reloaded.draft.struggles)
    }

    // --- the day as shown ---------------------------------------------------

    @Test
    fun `an unsent diary is what the student sees`() {
        val unsent = listOf(write(PendingJournalWrite.Payload.Diary(DiaryDraft(achievements = "書いた"))))

        val record = JournalDayRecord.of(today.toString(), day = null, unsent = unsent)

        assertEquals("書いた", record.diary!!.achievements)
        assertTrue(record.isDiaryUnsent)
        assertTrue(record.hasContent)
    }

    @Test
    fun `a block that already landed is not drawn or counted twice`() {
        val day = JournalDay(
            date = today.toString(),
            entries = listOf(DayLogEntry("server-1", clientEntryId = "c1", category = "self_study", durationMinutes = 30)),
            totals = DayTotals(mapOf("self_study" to 30), studySelfMinutes = 30),
        )
        val unsent = listOf(write(PendingJournalWrite.Payload.Entry(entry("c1"))))

        val record = JournalDayRecord.of(today.toString(), day, unsent)

        assertEquals(1, record.rows.size)
        assertEquals(30, record.studySelfMinutes)
    }

    @Test
    fun `queued minutes are counted`() {
        val unsent = listOf(write(PendingJournalWrite.Payload.Entry(entry("c2", minutes = 60))))

        val record = JournalDayRecord.of(today.toString(), JournalDay(today.toString()), unsent)

        assertEquals(60, record.studySelfMinutes)
        assertEquals(JournalLimits.MINUTES_PER_DAY - 60, record.remainingMinutes)
        assertFalse(record.rows.single().isSent)
    }

    @Test
    fun `the student's own record leads, unless they logged nothing`() {
        assertEquals(StudySource.Learner, headlineStudySource(selfMinutes = 40, autoMinutes = 90))
        assertEquals(StudySource.App, headlineStudySource(selfMinutes = 0, autoMinutes = 90))
        assertEquals(StudySource.Learner, headlineStudySource(selfMinutes = 0, autoMinutes = 0))
    }

    // --- helpers -----------------------------------------------------------

    private var ids = 0

    private fun client(api: FakeJournalApi) = JournalClient(api, AppEnvironment.Production)

    private fun outbox(store: JournalOutboxStore, api: FakeJournalApi) =
        JournalOutbox(store, client(api), newId = { "w${ids++}" })

    private fun entry(id: String, minutes: Int = 30) =
        NewDayLogEntry(clientEntryId = id, category = JournalCategories.SELF_STUDY, durationMinutes = minutes)

    private fun write(payload: PendingJournalWrite.Payload) = PendingJournalWrite("w${ids++}", today.toString(), payload)

    private class FakeJournalApi(
        var failWith: ApiError? = null,
        private val refuseEntry: String? = null,
        private val dayBody: String = "{}",
    ) : HttpClient {
        val calls = mutableListOf<String>()
        val bodies = mutableListOf<String>()

        override suspend fun send(request: HttpRequest): HttpResponse {
            val path = request.url.substringAfter("/api/v1/me/journal")
            calls += "${request.method} $path"
            request.body?.let { bodies += it }
            failWith?.let { throw it }

            return when {
                request.method == HttpRequest.Method.DELETE -> HttpResponse(204, "")
                request.method == HttpRequest.Method.GET -> HttpResponse(200, dayBody)
                refuseEntry != null && request.body?.contains("\"$refuseEntry\"") == true ->
                    HttpResponse(400, """{"status":400,"code":"day_full"}""")
                path.endsWith("/entries") -> HttpResponse(201, """{"entryId":"server","clientEntryId":"x"}""")
                else -> HttpResponse(201, """{"date":"2026-10-06"}""")
            }
        }
    }

    private companion object {
        const val DAY = """
        {"date":"2026-10-06",
         "diary":{"date":"2026-10-06","achievements":"二次関数の解の公式","struggles":null,"tomorrowPlan":null,
                  "focus":4,"fatigue":null,"updatedAt":"2026-10-06T00:42:13.3946781+00:00"},
         "entries":[{"entryId":"e1","clientEntryId":"c1","date":"2026-10-06","category":"self_study","isStudy":true,
                     "source":"self","durationMinutes":30,"startedAt":null,"endedAt":null,"subject":"数学",
                     "content":"p.42","createdAt":"2026-10-06T00:42:13Z","updatedAt":"2026-10-06T00:42:13Z"}],
         "autoStudy":[],"practice":{"sessions":0,"answered":0,"correct":0,"minutes":0},
         "totals":{"minutesByCategory":{"self_study":30},"studySelfMinutes":30,"studyAutoMinutes":0}}
        """
    }
}
