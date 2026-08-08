package com.wakaroute.core.map

import com.wakaroute.core.content.ContentClient
import com.wakaroute.core.net.ApiError

/**
 * The 理解マップ with the student's own record in it.
 *
 * Four requests for the 領域 (one per path, cached for the session) plus two for
 * the record. The path fetches earn their place twice over:
 *
 * 1. **Live titles.** A renamed course shows its new name; the graph's own
 *    title is a diagnostic label and a fallback, never a key.
 * 2. **Validation against reality.** The graph references course ids in another
 *    system. A course deleted there must fail loudly rather than quietly
 *    removing a 要素 from a student's map, and this is the only way to notice.
 *
 * Note what this does **not** do. LMS-DEV t-d1bea82 suggests fetching path
 * details one by one to work out which path a course belongs to, because the
 * learner endpoints cannot say. We never need to ask: the bundled graph already
 * states it, and having authored that mapping we can test it — which is a
 * better position than deriving it from an API that currently returns an empty
 * `/me/paths` and 140 unrelated corporate courses from `/me/assignments`.
 */
class LiveUnderstandingMap(
    private val content: ContentClient,
    private val structure: UnderstandingMapRepository = bundledUnderstandingMap(),
    /**
     * The same catalogue [structure] draws from.
     *
     * Passed rather than looked up, so a 教科 published at runtime is overlaid
     * with live titles like any other. Reaching for the bundled resource map
     * here — as this once did — silently limits the live layer to 数学.
     */
    private val catalogue: PrerequisiteGraphCatalogue = BundledGraphCatalogue(),
) {
    /** Course titles and the set of ids that really exist, held for the session. */
    private var liveContent: LiveContent? = null

    private data class LiveContent(
        val titlesByCourseId: Map<String, String>,
        val courseIds: Set<String>,
    )

    /**
     * Loads one 教科.
     *
     * Degrades in one direction only: a network failure costs the student's
     * record and the live titles, never the structure. The result then says
     * [LearnerProgress.Unavailable] rather than pretending to an empty record,
     * because those are different things and only one of them is about them.
     */
    suspend fun load(subject: SchoolSubject): SubjectMapState {
        val current = structure.state(subject)
        if (current !is SubjectMapState.Available) return current

        val graph = catalogue.graph(subject) ?: return current

        val live = try {
            liveContent ?: fetchLiveContent(graph).also { liveContent = it }
        } catch (e: ApiError) {
            // The structure is still worth reading, and it is bundled — this
            // screen works on a train. Titles fall back to the authored ones.
            return current.copy(progress = LearnerProgress.Unavailable)
        }

        // The graph says a 要素 exists that MANABU2 no longer has. Half a
        // prerequisite graph sends students back to the wrong place, so the
        // whole map is withheld.
        val problems = graph.problems(liveCourseIds = live.courseIds)
        if (problems.isNotEmpty()) return SubjectMapState.Unavailable(problems)

        val withLiveTitles = graph.toSubject(subject.id, titlesByCourseId = live.titlesByCourseId)

        val record = try {
            MasteryDerivation.record(
                progress = content.progress(),
                quizAttempts = content.quizAttempts(),
            )
        } catch (e: ApiError) {
            return SubjectMapState.Available(withLiveTitles, LearnerProgress.Unavailable, graph.asOf)
        }

        return SubjectMapState.Available(withLiveTitles, LearnerProgress.Known(record), graph.asOf)
    }

    /**
     * Expands the graph's 領域 into their courses.
     *
     * One request per 領域 — four for 数学. Cached for the session because the
     * catalogue does not change while a student is using the app, and paying
     * four requests every time they open the map would be felt on a phone.
     */
    private suspend fun fetchLiveContent(graph: PrerequisiteGraph): LiveContent {
        val titles = mutableMapOf<String, String>()
        val ids = mutableSetOf<String>()

        for (domain in graph.domains) {
            for (course in content.pathDetail(domain.pathId).courses) {
                ids += course.id
                if (course.title.isNotBlank()) titles[course.id] = course.title
            }
        }

        return LiveContent(titlesByCourseId = titles, courseIds = ids)
    }
}
