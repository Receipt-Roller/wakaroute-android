package com.wakaroute.core.map

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.net.ApiError
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
import com.wakaroute.core.net.WakaRouteJson
import com.wakaroute.core.net.retryingReads
import com.wakaroute.core.net.sendDecoding
import java.io.File
import kotlinx.serialization.Serializable

/**
 * `GET /api/prerequisites` — every 教科 whose edges have been authored.
 *
 * A 教科 that is absent has no map yet. That is the whole switch: publishing a
 * graph for 国語 makes it appear in the app, and removing it takes it away
 * again, with no release either way.
 */
@Serializable
data class PublishedGraphs(
    /** When the set as a whole was published. Diagnostic; each graph carries its own. */
    val asOf: String = "",
    val graphs: List<PrerequisiteGraph> = emptyList(),
)

/**
 * Fetches the published graphs.
 *
 * **Unauthenticated, from wakaroute.com**, like the school catalogue and for
 * the same two reasons: the prerequisite structure is not learner data, and
 * going through the authenticated client would make an anonymous student's map
 * depend on registration having happened — which is precisely the call that
 * creates an account.
 */
class PrerequisiteGraphClient(
    private val http: HttpClient,
    private val environment: AppEnvironment,
) {
    suspend fun published(): PublishedGraphs = retryingReads {
        http.sendDecoding(
            HttpRequest(
                method = HttpRequest.Method.GET,
                url = environment.wakarouteBaseUrl.trimEnd('/') + "/api/prerequisites",
                headers = mapOf("Accept" to "application/json"),
            ),
            PublishedGraphs.serializer(),
        )
    }
}

/** Where fetched graphs live between launches. */
interface PrerequisiteGraphStore {
    fun read(): List<PrerequisiteGraph>
    fun write(graphs: List<PrerequisiteGraph>)
}

class InMemoryPrerequisiteGraphStore(
    private var graphs: List<PrerequisiteGraph> = emptyList(),
) : PrerequisiteGraphStore {
    override fun read(): List<PrerequisiteGraph> = graphs
    override fun write(graphs: List<PrerequisiteGraph>) {
        this.graphs = graphs
    }
}

/**
 * Kept on disk so the map is right on the **next** launch, before any fetch.
 *
 * In `filesDir` rather than the cache directory: a student who has seen 国語
 * should not find it gone because the system reclaimed space overnight. It
 * would come back on the next fetch, but 「昨日あった教科が消えている」 is not
 * something to make a 中学生 sit through.
 */
class FilePrerequisiteGraphStore(private val file: File) : PrerequisiteGraphStore {

    override fun read(): List<PrerequisiteGraph> = try {
        if (!file.exists()) emptyList() else WakaRouteJson.decodeFromString(
            PublishedGraphs.serializer(),
            file.readText(),
        ).graphs
    } catch (e: Exception) {
        // A truncated or half-written file costs a fetch, never a crash on
        // launch. The bundled graph carries the app until then.
        emptyList()
    }

    override fun write(graphs: List<PrerequisiteGraph>) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(
                WakaRouteJson.encodeToString(PublishedGraphs.serializer(), PublishedGraphs(graphs = graphs)),
            )
        } catch (e: Exception) {
            // Losing the write costs one fetch next launch.
        }
    }
}

/**
 * Brings the stored graphs up to date.
 *
 * **Validation happens here, before anything is stored.** A graph that fails
 * is dropped and the previous one is kept, so a bad publish cannot take a 教科
 * away from students who already had it — and cannot half-apply either, which
 * 共通判断規則 §1 rules out: a partly-wrong prerequisite graph sends a student
 * back to the wrong place.
 */
class PrerequisiteGraphSync(
    private val client: PrerequisiteGraphClient,
    private val store: PrerequisiteGraphStore,
) {
    data class Result(
        val accepted: List<String> = emptyList(),
        /** Graphs the server published that this app refused, with the reason. */
        val rejected: Map<String, List<String>> = emptyMap(),
        val failure: ApiError? = null,
    )

    suspend fun refresh(): Result {
        val published = try {
            client.published()
        } catch (e: ApiError) {
            // Offline, or the endpoint does not exist yet. Neither is worth
            // telling a student about: they keep the graphs they already have.
            return Result(failure = e)
        }

        val accepted = mutableListOf<PrerequisiteGraph>()
        val rejected = mutableMapOf<String, List<String>>()

        for (graph in published.graphs) {
            val problems = graph.problems()
            if (problems.isEmpty()) accepted += graph else rejected[graph.subject] = problems
        }

        // Only when the server answered. An empty list from a working endpoint
        // is a real answer — nothing is published — and must be able to clear
        // a 教科 that was withdrawn.
        val kept = store.read().filter { previous ->
            rejected.containsKey(previous.subject) && accepted.none { it.subject == previous.subject }
        }

        store.write(accepted + kept)

        return Result(accepted = accepted.map { it.subject }, rejected = rejected)
    }
}
