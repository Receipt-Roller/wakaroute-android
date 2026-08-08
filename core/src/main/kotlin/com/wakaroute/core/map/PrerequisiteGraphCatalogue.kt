package com.wakaroute.core.map

/**
 * Where a 教科's prerequisite graph comes from.
 *
 * Extracted so the answer can change without the map changing. 数学 ships in
 * the app's resources; 国語・英語・理科・社会 are meant to appear **when the
 * edges are authored, without a new release** — and that is only possible if
 * nothing downstream knows which of the two it got.
 */
interface PrerequisiteGraphCatalogue {

    /** The graph for [subject], or null when none has been authored yet. */
    fun graph(subject: SchoolSubject): PrerequisiteGraph?
}

/**
 * The graphs compiled into the app.
 *
 * 数学 only, and the map below is meant to be read together with
 * `core/src/main/resources/graphs` — a key added here without the reviewed
 * graph beside it publishes a wrong map.
 *
 * This stays after the published catalogue exists, for two reasons: it is what
 * a student sees on their first launch before anything is fetched, and it is
 * what they keep seeing when the network is gone.
 */
class BundledGraphCatalogue : PrerequisiteGraphCatalogue {

    private val cache = mutableMapOf<SchoolSubject, PrerequisiteGraph?>()

    override fun graph(subject: SchoolSubject): PrerequisiteGraph? = cache.getOrPut(subject) {
        val resource = resources[subject] ?: return@getOrPut null
        try {
            PrerequisiteGraph.bundled(resource)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        val resources = mapOf(SchoolSubject.Math to "prerequisites-math")
    }
}

/**
 * Server-authored graphs, with the bundled ones underneath.
 *
 * This is what lets a 教科 go live without an app update: the edges are
 * published, the app fetches them, and the 準備中 chip becomes a map. Nothing
 * here is scheduled — [PrerequisiteGraphSync] does the fetching, and this only
 * decides what to hand out once it has.
 *
 * **A published graph that fails validation is discarded rather than shown.**
 * The alternative — no map at all — punishes a student for an authoring
 * mistake made on the other side of the network, when a validated graph is
 * sitting right here. The one it falls back to may be older, and the screen
 * already prints its 作成日, so the staleness is visible rather than hidden.
 */
class PublishedGraphCatalogue(
    private val store: PrerequisiteGraphStore,
    private val bundled: PrerequisiteGraphCatalogue = BundledGraphCatalogue(),
) : PrerequisiteGraphCatalogue {

    override fun graph(subject: SchoolSubject): PrerequisiteGraph? {
        val published = store.read().firstOrNull { it.subject == subject.label }

        // Validated here as well as at fetch time. The stored copy was written
        // by an older build of this app, and the rules it was checked against
        // are the ones that build had.
        if (published != null && published.problems().isEmpty()) return published

        return bundled.graph(subject)
    }
}
