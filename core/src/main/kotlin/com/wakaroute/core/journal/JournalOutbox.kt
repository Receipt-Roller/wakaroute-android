package com.wakaroute.core.journal

import com.wakaroute.core.net.ApiError
import com.wakaroute.core.net.WakaRouteJson
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * A journal write that has not reached the server yet.
 *
 * Students write the day down at night, in bed, on whatever connection they
 * have. A paragraph about what finally made sense today is not something to
 * lose to a spinner.
 */
@Serializable
data class PendingJournalWrite(
    val id: String,
    /** `yyyy-MM-dd`, Japan. */
    val date: String,
    val payload: Payload,
    /**
     * The server refused it for good. Kept rather than retried, so one dead
     * write cannot block everything queued behind it.
     */
    val rejected: Boolean = false,
) {
    @Serializable
    sealed interface Payload {
        /** A full-replace save. Only the newest for a day survives. */
        @Serializable
        data class Diary(val draft: DiaryDraft) : Payload

        /** A delete is a write too, and must not be overtaken by an older save. */
        @Serializable
        data object DeleteDiary : Payload

        @Serializable
        data class Entry(val entry: NewDayLogEntry) : Payload
    }

    val isDiaryWrite: Boolean get() = payload !is Payload.Entry
}

interface JournalOutboxStore {
    fun read(): List<PendingJournalWrite>
    fun write(writes: List<PendingJournalWrite>)
}

class FileJournalOutboxStore(private val file: File) : JournalOutboxStore {
    private val serializer = ListSerializer(PendingJournalWrite.serializer())

    override fun read(): List<PendingJournalWrite> =
        runCatching { if (file.exists()) WakaRouteJson.decodeFromString(serializer, file.readText()) else null }
            .getOrNull().orEmpty()

    override fun write(writes: List<PendingJournalWrite>) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(WakaRouteJson.encodeToString(serializer, writes))
        }
    }
}

class InMemoryJournalOutboxStore(private var writes: List<PendingJournalWrite> = emptyList()) : JournalOutboxStore {
    override fun read() = writes
    override fun write(writes: List<PendingJournalWrite>) {
        this.writes = writes
    }
}

/**
 * Holds journal writes that could not be sent, and sends them later.
 *
 * Separate from `LearningActionQueue`, which is shaped around a lesson id. The
 * same rules: replay in order, stop at the first temporary failure so the rest
 * keep their place, and set aside what the server will refuse for ever.
 */
class JournalOutbox(
    private val store: JournalOutboxStore,
    private val client: JournalClient,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutex = Mutex()

    /** Everything still waiting, oldest first. */
    suspend fun pending(): List<PendingJournalWrite> = mutex.withLock { store.read().filterNot { it.rejected } }

    /**
     * Queues a diary save, replacing any earlier unsent one for the day.
     *
     * The endpoint replaces the whole diary, so the newer save already says
     * everything the older one did. Left in, the older one would go first, and
     * a connection dying in between would bring the old text back.
     */
    suspend fun queueDiary(draft: DiaryDraft, date: LocalDate) = replaceDiaryWrite(date, PendingJournalWrite.Payload.Diary(draft))

    suspend fun queueDiaryDeletion(date: LocalDate) = replaceDiaryWrite(date, PendingJournalWrite.Payload.DeleteDiary)

    /** Blocks accumulate: two 30-minute entries are two blocks, kept apart by their ids. */
    suspend fun queueEntry(entry: NewDayLogEntry, date: LocalDate) = mutex.withLock {
        store.write(store.read() + PendingJournalWrite(newId(), date.toString(), PendingJournalWrite.Payload.Entry(entry)))
    }

    /**
     * Throws everything away. For 学習記録の削除 and for signing in as somebody
     * else: these writes belong to the account being left, and sending them
     * afterwards would file one student's diary under another's name.
     */
    suspend fun clear() = mutex.withLock { store.write(emptyList()) }

    /** Sends what it can, oldest first. Returns how many are still waiting. */
    suspend fun flush(): Int = mutex.withLock {
        val writes = store.read()
        val sent = mutableSetOf<String>()
        val refused = mutableSetOf<String>()

        for (write in writes.filterNot { it.rejected }) {
            try {
                send(write)
                sent += write.id
            } catch (e: ApiError) {
                // Temporary: stop, so everything behind it keeps its place.
                if (e.isTransient) break
                // A day that has scrolled past the 31-day window, or a block
                // that would push the day past 24 hours.
                refused += write.id
            }
        }

        val remaining = writes
            .filterNot { it.id in sent }
            .map { if (it.id in refused) it.copy(rejected = true) else it }
        store.write(remaining)
        remaining.count { !it.rejected }
    }

    private suspend fun send(write: PendingJournalWrite) {
        val date = LocalDate.parse(write.date)
        when (val payload = write.payload) {
            is PendingJournalWrite.Payload.Diary -> client.saveDiary(payload.draft, date)
            PendingJournalWrite.Payload.DeleteDiary -> client.deleteDiary(date)
            is PendingJournalWrite.Payload.Entry -> client.addEntry(payload.entry, date)
        }
    }

    private suspend fun replaceDiaryWrite(date: LocalDate, payload: PendingJournalWrite.Payload) = mutex.withLock {
        val day = date.toString()
        val others = store.read().filterNot { it.date == day && it.isDiaryWrite && !it.rejected }
        store.write(others + PendingJournalWrite(newId(), day, payload))
    }
}
