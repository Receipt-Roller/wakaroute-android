package com.wakaroute.core.map

/**
 * How much the app knows about **this student** right now.
 *
 * Three states, because collapsing them is how a screen ends up telling a
 * student something false about themselves:
 *
 * - [NotConnected] — no account yet. Nothing has been recorded anywhere.
 * - [Unavailable] — there is a record; we could not read it.
 * - [Known] — this is what they have done.
 *
 * [NotConnected] and [Unavailable] both leave every 要素 at まだ, and **that is
 * a fact about the app, not about the student.** A screen that renders either
 * as an ordinary empty record is claiming they have done nothing. Say which
 * one it is.
 */
sealed interface LearnerProgress {
    data object NotConnected : LearnerProgress
    data object Unavailable : LearnerProgress
    data class Known(val record: MasteryRecord) : LearnerProgress

    /**
     * The record to draw the structure with.
     *
     * Empty for the two states that have none — safe for counting and layout,
     * and **not** a substitute for saying which state it is. The name is
     * deliberately awkward: reaching for it should feel like a decision.
     */
    val recordOrEmpty: MasteryRecord
        get() = (this as? Known)?.record ?: MasteryRecord.Empty
}

/** What the app is able to show for one 教科. */
sealed interface SubjectMapState {

    /** The graph loaded and validated. */
    data class Available(
        val subject: Subject,
        val progress: LearnerProgress,
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
     * The graph is present but broken: a cycle, a dangling edge, a duplicate,
     * or a course that no longer exists in MANABU2.
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
 * The structure alone: the authored graph, and no learner data.
 *
 * Correct on its own before an account exists, and the fallback when the
 * network is unreachable — the 要素 and their prerequisites are worth reading
 * even offline, which is why they are bundled rather than fetched.
 *
 * 数学 only. The other four 教科 have content but no authored edges, and they
 * are reported [SubjectMapState.ComingSoon] rather than handed an empty graph.
 */
class BundledUnderstandingMapRepository(
    private val progress: LearnerProgress = LearnerProgress.NotConnected,
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
            progress = progress,
            asOf = graph.asOf,
        )
    }

    companion object {
        /**
         * 数学 is the only 教科 with authored edges. Adding a key here without
         * the corresponding reviewed graph would silently publish a wrong map,
         * so this list and `core/src/main/resources/graphs` are meant to be
         * read together.
         */
        val graphResources = mapOf(SchoolSubject.Math to "prerequisites-math")
    }
}
