package com.wakaroute.core.map

import com.wakaroute.core.net.WakaRouteJson
import kotlinx.serialization.Serializable

/**
 * The prerequisite graph for one subject, as authored.
 *
 * MANABU2 has no prerequisite concept — its only ordering is a linear
 * `orderIndex` within a path. A sequence can say 「次は一次関数です」; it cannot say
 * 「一次関数で止まっているのは文字を用いた式が原因です」, which is the whole of
 * principle #1 of the サービス仕様. So the edges live on the ワカルート side.
 *
 * Shipped in the module's resources today. The shape matches what
 * `GET /api/prerequisites?subject=math` will serve, so moving to the network
 * later changes where the bytes come from and nothing else.
 */
@Serializable
data class PrerequisiteGraph(
    val asOf: String,
    val subject: String,
    val domains: List<Domain>,
    val elements: List<Element>,
) {
    @Serializable
    data class Domain(
        /** The letter shown on the map (A–D). */
        val code: String,
        val name: String,
        /** The MANABU2 path this 領域 corresponds to. */
        val pathId: String,
    )

    @Serializable
    data class Element(
        val courseId: String,
        /**
         * For review and diagnostics only. **Never** matched against anything:
         * the 開発ガイド and サービス仕様 §6 both forbid names as keys, and a
         * course is free to be renamed at any time.
         */
        val title: String,
        val domain: String,
        val grade: Int? = null,
        val requires: List<String> = emptyList(),
    )

    /**
     * Builds the map's [Subject].
     *
     * [titlesByCourseId] lets live content supply the current name, so a renamed
     * course shows its new name; the graph's own `title` is only a fallback.
     * Phase 1 has no content client, so it is always the fallback here.
     */
    fun toSubject(id: SubjectId, titlesByCourseId: Map<String, String> = emptyMap()): Subject {
        val known = elements.map { it.courseId }.toSet()

        return Subject(
            id = id,
            name = subject,
            domains = domains.map { LearningDomain(code = it.code, name = it.name) },
            elements = elements.map { element ->
                LearningElement(
                    id = ElementId(element.courseId),
                    name = titlesByCourseId[element.courseId] ?: element.title,
                    domainCode = element.domain,
                    grade = element.grade,
                    // An edge pointing at a course outside the graph would block
                    // its dependant forever with nothing to show for it, so it
                    // is dropped here and reported by `problems()`.
                    prerequisiteIds = element.requires
                        .filter { it in known }
                        .map { ElementId(it) },
                )
            },
        )
    }

    /**
     * Everything wrong with the graph, in words a human can act on.
     *
     * Checked on load, not only in tests: the graph references course ids that
     * live in another system, and a course deleted there must fail loudly
     * instead of quietly removing a 要素 from a student's map.
     *
     * A graph with problems is **not** applied in part. 共通判断規則 §1:
     * a half-working prerequisite graph sends students back to the wrong place,
     * and no map is better than a wrong one.
     */
    fun problems(liveCourseIds: Set<String>? = null): List<String> = buildList {
        val ids = elements.map { it.courseId }
        ids.groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys.sorted()
            .forEach { add("course $it appears more than once") }

        val known = ids.toSet()
        val declaredDomains = domains.map { it.code }.toSet()
        for (element in elements) {
            element.requires
                .filterNot { it in known }
                .forEach { add("${element.title} requires $it, which is not in the graph") }

            if (element.domain !in declaredDomains) {
                add("${element.title} is in domain ${element.domain}, which is not declared")
            }
        }

        firstCycle()?.let { add("cycle: ${it.joinToString(" → ")}") }

        liveCourseIds?.let { live ->
            elements
                .filterNot { it.courseId in live }
                .forEach { add("${it.title} (${it.courseId}) no longer exists in MANABU2") }
        }
    }

    /**
     * Depth-first search for a back edge.
     *
     * Returns the courses involved rather than a boolean, because 「循環があります」
     * is not something anyone can act on.
     */
    private fun firstCycle(): List<String>? {
        val requires = elements.associate { it.courseId to it.requires }
        val titles = elements.associate { it.courseId to it.title }
        val settled = mutableSetOf<String>()
        val onPath = mutableListOf<String>()

        fun walk(id: String): List<String>? {
            val index = onPath.indexOf(id)
            if (index >= 0) return (onPath.subList(index, onPath.size) + id).map { titles[it] ?: it }
            if (id in settled) return null

            onPath.add(id)
            for (next in requires[id].orEmpty()) {
                walk(next)?.let { return it }
            }
            onPath.removeAt(onPath.lastIndex)
            settled.add(id)
            return null
        }

        for (element in elements) {
            walk(element.courseId)?.let { return it }
        }
        return null
    }

    companion object {
        /**
         * Loads a bundled graph.
         *
         * Read through the class loader rather than an Android asset manager so
         * the same code path runs under `./gradlew :core:test`. A graph that can
         * only be loaded on a device is a graph whose validation never runs in CI.
         */
        fun bundled(name: String): PrerequisiteGraph {
            val stream = PrerequisiteGraph::class.java.getResourceAsStream("/graphs/$name.json")
                ?: error("Bundled prerequisite graph '$name' is missing from core resources.")
            return WakaRouteJson.decodeFromString(serializer(), stream.bufferedReader().use { it.readText() })
        }

        fun math(): PrerequisiteGraph = bundled("prerequisites-math")
    }
}
