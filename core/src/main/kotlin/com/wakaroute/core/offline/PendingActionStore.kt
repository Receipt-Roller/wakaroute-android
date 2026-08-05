package com.wakaroute.core.offline

import com.wakaroute.core.net.WakaRouteJson
import java.io.File
import kotlinx.serialization.builtins.ListSerializer

/**
 * Where the queue lives between launches.
 *
 * It has to survive the process, or the whole feature is a retry loop: a
 * student who answers a quiz underground and then closes the app has, from
 * their point of view, answered it.
 */
interface PendingActionStore {
    fun read(): List<PendingAction>
    fun write(actions: List<PendingAction>)
}

/**
 * A single JSON file, rewritten whole.
 *
 * Whole-file writes rather than an append log because the queue is small — a
 * handful of entries between two moments of signal — and because merging and
 * setting-aside both rewrite it anyway. An append log would be faster and would
 * need compaction, which is a second thing to get wrong.
 *
 * The write goes to a temporary file and is renamed over the real one. A
 * half-written queue is worse than a stale one: it loses everything, including
 * the entries that were already safely stored.
 */
class FilePendingActionStore(private val file: File) : PendingActionStore {

    override fun read(): List<PendingAction> {
        if (!file.exists()) return emptyList()

        return try {
            WakaRouteJson.decodeFromString(ListSerializer(PendingAction.serializer()), file.readText())
        } catch (e: Exception) {
            // A queue we cannot parse is a queue we cannot send. Returning empty
            // loses those records, which is bad — but retrying an unreadable
            // file forever would block every record made after it, which is
            // worse. The file is left on disk for diagnosis rather than deleted.
            emptyList()
        }
    }

    override fun write(actions: List<PendingAction>) {
        file.parentFile?.mkdirs()

        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(
            WakaRouteJson.encodeToString(ListSerializer(PendingAction.serializer()), actions),
        )
        temporary.renameTo(file)
    }
}

/** For tests. */
class InMemoryPendingActionStore(initial: List<PendingAction> = emptyList()) : PendingActionStore {
    private var actions = initial

    override fun read(): List<PendingAction> = actions

    override fun write(actions: List<PendingAction>) {
        this.actions = actions
    }
}
