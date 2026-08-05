package com.wakaroute.core.map

/**
 * What the app is able to show for one 教科.
 *
 * Three outcomes, not two, because 「まだ辺を書いていない」 and 「辺が壊れている」 call
 * for completely different words — and neither may be shown as a working map.
 */
sealed interface SubjectMapState {

    /** The graph loaded and validated. */
    data class Available(
        val subject: Subject,
        val mastery: MasteryRecord,
        /** The date the graph was authored, shown so the map is never taken as live. */
        val asOf: String,
    ) : SubjectMapState

    /**
     * No prerequisite edges have been authored for this 教科 yet.
     *
     * **An empty graph must never be substituted here.** With no edges every
     * 要素 is `Ready`, which the screens would render as 「どの要素も学習できます／
     * つまずきはありません」 — the one answer this map must never give.
     */
    data object ComingSoon : SubjectMapState

    /**
     * The graph is present but broken: a cycle, a dangling edge, a duplicate.
     *
     * Not applied in part. 共通判断規則 §1: a half-working prerequisite graph
     * sends students back to the wrong place, and showing no map is better.
     */
    data class Unavailable(val problems: List<String>) : SubjectMapState
}

interface UnderstandingMapRepository {
    fun state(subject: SchoolSubject): SubjectMapState

    /** Every 教科 in display order, so 準備中 ones still appear. */
    fun allStates(): List<Pair<SchoolSubject, SubjectMapState>> =
        SchoolSubject.entries.map { it to state(it) }
}

/**
 * The Phase 1 map: the authored graph, and no learner data at all.
 *
 * Phase 1 connects to no MANABU2 endpoint, so [MasteryRecord.Empty] is the
 * honest record — every 要素 is まだ, and that is a fact about this build rather
 * than a fact about the student. Screens must say so in those terms.
 *
 * 数学 only. The other four 教科 have content but no authored edges, and they
 * are reported [SubjectMapState.ComingSoon] rather than handed an empty graph.
 */
class BundledUnderstandingMapRepository(
    private val mastery: MasteryRecord = MasteryRecord.Empty,
) : UnderstandingMapRepository {

    private val cache = mutableMapOf<SchoolSubject, SubjectMapState>()

    override fun state(subject: SchoolSubject): SubjectMapState =
        cache.getOrPut(subject) { load(subject) }

    private fun load(subject: SchoolSubject): SubjectMapState {
        val resource = graphResources[subject] ?: return SubjectMapState.ComingSoon

        val graph = try {
            PrerequisiteGraph.bundled(resource)
        } catch (e: Exception) {
            return SubjectMapState.Unavailable(listOf(e.message ?: "graph could not be read"))
        }

        // Validated at load as well as in tests. The ids belong to another
        // system, and a course deleted there must fail loudly rather than
        // quietly drop a 要素 out of a student's map.
        val problems = graph.problems()
        if (problems.isNotEmpty()) return SubjectMapState.Unavailable(problems)

        return SubjectMapState.Available(
            subject = graph.toSubject(subject.id),
            mastery = mastery,
            asOf = graph.asOf,
        )
    }

    private companion object {
        /**
         * 数学 is the only 教科 with authored edges. Adding a key here without
         * the corresponding reviewed graph would silently publish a wrong map,
         * so this list and `core/src/main/resources/graphs` are meant to be read
         * together.
         */
        val graphResources = mapOf(SchoolSubject.Math to "prerequisites-math")
    }
}
