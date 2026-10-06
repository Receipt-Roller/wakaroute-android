package com.wakaroute.core.cards

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.net.ApiError
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
import com.wakaroute.core.net.WakaRouteJson
import com.wakaroute.core.net.retryingReads
import java.io.File
import java.time.LocalDate
import kotlinx.serialization.KSerializer

/**
 * Fetches the card sets from **wakaroute.com, not MANABU2** — no token, the
 * same arrangement as 高校検索.
 *
 * Each set comes whole in one response with an `ETag` (2,450 words, 1,110
 * kanji, 96 subject cards), so the device keeps the lot and only asks whether
 * it has changed. That is what lets the cards work with no signal.
 */
class CardCatalogClient(
    private val http: HttpClient,
    private val environment: AppEnvironment,
) {
    /** Null when the server confirmed the stored copy is current (304). */
    suspend fun words(knownEntityTag: String?): CardCatalog<WordCard>? =
        fetch("/api/v1/english-words/dataset", WordCard.serializer(), knownEntityTag) { dataset, tag -> dataset.toCatalog(tag) }

    suspend fun kanji(knownEntityTag: String?): CardCatalog<KanjiCard>? =
        fetch("/api/v1/kanji/dataset", KanjiCard.serializer(), knownEntityTag) { dataset, tag -> dataset.toCatalog(tag) }

    suspend fun subjectCards(knownEntityTag: String?): CardCatalog<SubjectCard>? =
        fetch("/api/v1/study-cards/dataset", SubjectCard.serializer(), knownEntityTag) { dataset, tag ->
            dataset.toCatalog(tag, dataset.items.mapIndexed { index, card -> card.copy(order = index) })
        }

    private suspend fun <Card> fetch(
        path: String,
        cardSerializer: KSerializer<Card>,
        knownEntityTag: String?,
        toCatalog: (CardDataset<Card>, entityTag: String) -> CardCatalog<Card>,
    ): CardCatalog<Card>? = retryingReads {
        val headers = buildMap {
            put("Accept", "application/json")
            if (!knownEntityTag.isNullOrEmpty()) put("If-None-Match", knownEntityTag)
        }
        val response = try {
            http.send(HttpRequest(HttpRequest.Method.GET, environment.wakarouteBaseUrl.trimEnd('/') + path, headers))
        } catch (e: ApiError) {
            throw e
        } catch (e: Exception) {
            throw ApiError.Unknown(e.message ?: e::class.java.simpleName)
        }

        when (response.status) {
            304 -> null
            in 200..299 -> {
                val dataset = try {
                    WakaRouteJson.decodeFromString(CardDataset.serializer(cardSerializer), response.body)
                } catch (e: Exception) {
                    throw ApiError.Decoding(e.message ?: e::class.java.simpleName)
                }
                toCatalog(dataset, response.headers["etag"].orEmpty())
            }
            else -> throw ApiError.Http(response.status, null)
        }
    }
}

/** Keeps one card set on the device between launches. */
interface CardStore<Card> {
    fun read(): CardCatalog<Card>?
    fun write(catalog: CardCatalog<Card>)
}

class FileCardStore<Card>(
    private val file: File,
    cardSerializer: KSerializer<Card>,
) : CardStore<Card> {
    private val serializer = CardCatalog.serializer(cardSerializer)

    // A truncated file costs a download, never a crash on launch.
    override fun read(): CardCatalog<Card>? =
        runCatching { if (file.exists()) WakaRouteJson.decodeFromString(serializer, file.readText()) else null }
            .getOrNull()

    override fun write(catalog: CardCatalog<Card>) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(WakaRouteJson.encodeToString(serializer, catalog))
        }
    }
}

class InMemoryCardStore<Card>(private var catalog: CardCatalog<Card>? = null) : CardStore<Card> {
    override fun read() = catalog
    override fun write(catalog: CardCatalog<Card>) {
        this.catalog = catalog
    }
}

/** Card progress. Only on this device; see [CardProgress]. */
interface CardProgressStore {
    fun read(): CardProgress
    fun write(progress: CardProgress)

    /** For 学習記録の削除: the student asked for everything to go. */
    fun clear()
}

class FileCardProgressStore(private val file: File) : CardProgressStore {
    override fun read(): CardProgress =
        runCatching { if (file.exists()) WakaRouteJson.decodeFromString(CardProgress.serializer(), file.readText()) else null }
            .getOrNull() ?: CardProgress()

    override fun write(progress: CardProgress) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(WakaRouteJson.encodeToString(CardProgress.serializer(), progress))
        }
    }

    override fun clear() {
        file.delete()
    }
}

class InMemoryCardProgressStore(private var progress: CardProgress = CardProgress()) : CardProgressStore {
    override fun read() = progress
    override fun write(progress: CardProgress) {
        this.progress = progress
    }

    override fun clear() {
        progress = CardProgress()
    }
}

/**
 * The card sets and what the student has done with them.
 *
 * Answers from the device whenever it can. The network is only asked whether
 * the stored copy is stale, so being offline costs nothing once a set has been
 * downloaded.
 */
class CardLibrary(
    private val client: CardCatalogClient,
    private val words: CardStore<WordCard>,
    private val kanji: CardStore<KanjiCard>,
    private val subjects: CardStore<SubjectCard>,
    private val progress: CardProgressStore,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    suspend fun words(): CardCatalog<WordCard> = refreshed(words) { client.words(it) }

    suspend fun kanji(): CardCatalog<KanjiCard> = refreshed(kanji) { client.kanji(it) }

    suspend fun subjectCards(): CardCatalog<SubjectCard> = refreshed(subjects) { client.subjectCards(it) }

    fun progress(): CardProgress = progress.read()

    /** Saved at once: a sitting cut short by a closed app keeps what was answered. */
    @Synchronized
    fun record(cardId: String, correct: Boolean): CardProgress {
        val current = progress.read()
        val updated = current.recording(cardId, CardScheduler.answering(current[cardId], correct, today()))
        progress.write(updated)
        return updated
    }

    companion object {
        /** The real library: each set and the progress in its own file under [directory]. */
        fun onDisk(directory: File, client: CardCatalogClient, progress: CardProgressStore) = CardLibrary(
            client = client,
            words = FileCardStore(File(directory, "words.json"), WordCard.serializer()),
            kanji = FileCardStore(File(directory, "kanji.json"), KanjiCard.serializer()),
            subjects = FileCardStore(File(directory, "subjects.json"), SubjectCard.serializer()),
            progress = progress,
        )
    }

    private suspend fun <Card> refreshed(
        store: CardStore<Card>,
        fetch: suspend (String?) -> CardCatalog<Card>?,
    ): CardCatalog<Card> {
        val stored = store.read()
        return try {
            fetch(stored?.entityTag)
                ?.also(store::write)
                ?: stored
                ?: throw ApiError.Unknown("304 with nothing stored")
        } catch (e: ApiError) {
            // Offline, or the server is having a bad day. A stored set is still
            // a complete set; only a first run has nothing to fall back on.
            stored ?: throw e
        }
    }
}
