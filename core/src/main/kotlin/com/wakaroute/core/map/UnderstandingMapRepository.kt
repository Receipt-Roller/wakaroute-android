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
 * even offline.
 *
 * Which graph it gets is [catalogue]'s decision, not this class's. That is what
 * lets 国語・英語・理科・社会 appear when their edges are published rather than
 * when the next APK ships.
 */
class GraphUnderstandingMapRepository(
    private val catalogue: PrerequisiteGraphCatalogue = BundledGraphCatalogue(),
    private val progress: LearnerProgress = LearnerProgress.NotConnected,
) : UnderstandingMapRepository {

    override fun state(subject: SchoolSubject): SubjectMapState {
        val graph = catalogue.graph(subject) ?: return SubjectMapState.ComingSoon

        // **Content being ready is not the same as edges being authored.**
        // A graph can list every 要素 in 国語 and say nothing about what depends
        // on what — and with no edges every 要素 is `Ready`, so the screens would
        // answer 「どこからでも学べます／つまずきはありません」 for a 教科 nobody has
        // mapped. That is the one thing this map must never say, so a graph
        // without a single prerequisite is treated as not authored yet.
        if (graph.elements.none { it.requires.isNotEmpty() }) return SubjectMapState.ComingSoon

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
}

/** The app's own graphs, with nothing fetched. Previews, tests, first launch. */
fun bundledUnderstandingMap(progress: LearnerProgress = LearnerProgress.NotConnected) =
    GraphUnderstandingMapRepository(BundledGraphCatalogue(), progress)
