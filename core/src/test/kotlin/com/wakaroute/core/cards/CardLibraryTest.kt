package com.wakaroute.core.cards

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.net.ApiError
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
import com.wakaroute.core.net.HttpResponse
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Shapes checked against wakaroute.com on 2026-10-06; the content is invented.
 *
 * The promise these protect is that cards work with no signal once a set has
 * been downloaded.
 */
class CardLibraryTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a first download is stored with its ETag`() = runTest {
        val store = InMemoryCardStore<KanjiCard>()
        val catalog = library(FakeSite(200, KANJI, etag = "\"kanji-1\""), kanji = store).kanji()

        assertEquals(listOf("山"), catalog.cards.map { it.character })
        assertEquals("\"kanji-1\"", store.read()!!.entityTag)
        assertEquals("CC-BY-SA-4.0", catalog.licenses.single().spdxId)
    }

    @Test
    fun `the stored ETag is sent back, and a 304 keeps the stored set`() = runTest {
        val stored = CardCatalog(cards = listOf(KanjiCard(id = "k1", character = "川", onReadings = listOf("セン"))), entityTag = "\"kanji-1\"")
        val site = FakeSite(304, "")

        val catalog = library(site, kanji = InMemoryCardStore(stored)).kanji()

        assertEquals("\"kanji-1\"", site.requests.single().headers["If-None-Match"])
        assertEquals(stored, catalog)
    }

    @Test
    fun `offline, the stored set is still a whole set`() = runTest {
        val stored = CardCatalog(cards = listOf(KanjiCard(id = "k1", character = "川", onReadings = listOf("セン"))))

        val catalog = library(FakeSite(failWith = ApiError.Offline), kanji = InMemoryCardStore(stored)).kanji()

        assertEquals(stored, catalog)
    }

    @Test
    fun `offline on a first run is an error, not an empty deck`() = runTest {
        try {
            library(FakeSite(failWith = ApiError.Offline)).kanji()
            fail("expected the failure to reach the screen")
        } catch (e: ApiError) {
            assertEquals(ApiError.Offline, e)
        }
    }

    @Test
    fun `the word set's list of licences is kept whole`() = runTest {
        val catalog = library(FakeSite(200, WORDS)).words()

        assertEquals(listOf("CC-BY-SA-4.0", "CC0-1.0"), catalog.licenses.map { it.spdxId })
    }

    @Test
    fun `subject cards keep their published order`() = runTest {
        // They carry no frequency rank; without the order they would be dealt by id.
        val catalog = library(FakeSite(200, SUBJECTS)).subjectCards()

        assertEquals(listOf("z-first" to 0, "a-second" to 1), catalog.cards.map { it.id to it.order })
    }

    @Test
    fun `a stored set reads back`() = runTest {
        // A store that writes and never reads looks like it works: it starts
        // empty every launch. iOS shipped that bug once.
        val file = File(folder.root, "kanji.json")
        library(FakeSite(200, KANJI, etag = "\"kanji-1\""), kanji = FileCardStore(file, KanjiCard.serializer())).kanji()

        val reloaded = FileCardStore(file, KanjiCard.serializer()).read()!!
        assertEquals("山", reloaded.cards.single().character)
        assertEquals("\"kanji-1\"", reloaded.entityTag)
    }

    @Test
    fun `answers are saved at once, and read back`() {
        val file = File(folder.root, "card-progress.json")
        library(FakeSite(), progress = FileCardProgressStore(file)).record("k1", correct = true)

        assertEquals(1, FileCardProgressStore(file).read()["k1"]!!.box)
    }

    @Test
    fun `clearing card progress leaves nothing behind`() {
        val file = File(folder.root, "card-progress.json")
        val store = FileCardProgressStore(file)
        store.write(CardProgress(mapOf("k1" to CardReview(box = 1, reviewedOnEpochDay = 1))))

        store.clear()

        assertTrue(FileCardProgressStore(file).read().reviews.isEmpty())
    }

    private fun library(
        site: FakeSite,
        kanji: CardStore<KanjiCard> = InMemoryCardStore(),
        progress: CardProgressStore = InMemoryCardProgressStore(),
    ) = CardLibrary(
        client = CardCatalogClient(site, AppEnvironment.Production),
        words = InMemoryCardStore(),
        kanji = kanji,
        subjects = InMemoryCardStore(),
        progress = progress,
        today = { LocalDate.of(2026, 10, 6) },
    )

    private class FakeSite(
        private val status: Int = 200,
        private val body: String = "{}",
        private val etag: String? = null,
        private val failWith: ApiError? = null,
    ) : HttpClient {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun send(request: HttpRequest): HttpResponse {
            requests += request
            failWith?.let { throw it }
            return HttpResponse(status, body, listOfNotNull(etag?.let { "etag" to it }).toMap())
        }
    }

    private companion object {
        const val KANJI = """
        {"schemaVersion":1,"datasetVersion":"2026.1","asOf":"2026-09-19",
         "license":{"name":"テスト","spdxId":"CC-BY-SA-4.0","url":"https://example.invalid","attribution":"テストの帰属表示"},
         "items":[{"id":"k1","character":"山","officialStage":"junior-high","recommendedGrade":1,
                   "sequence":1,"frequencyRank":"12","strokeCount":3,"onReadings":["サン"],"kunReadings":["やま"]}]}
        """

        const val WORDS = """
        {"datasetVersion":"2026.1","asOf":"2026-09-19",
         "licenses":[{"spdxId":"CC-BY-SA-4.0"},{"spdxId":"CC0-1.0"}],
         "items":[{"id":"w1","lemma":"tree","stage":"junior-high","recommendedGrade":1,"frequencyRank":500,
                   "partsOfSpeech":["名詞"],"meaningsJa":["木"],"pronunciations":[]}]}
        """

        const val SUBJECTS = """
        {"datasetVersion":"2026.2","asOf":"2026-09-20","license":{"spdxId":"Apache-2.0"},
         "items":[{"id":"z-first","subject":"math","domain":"numbers","prompt":"問い","answer":"答え"},
                  {"id":"a-second","subject":"math","domain":"numbers","prompt":"問い","answer":"答え"}]}
        """
    }
}
