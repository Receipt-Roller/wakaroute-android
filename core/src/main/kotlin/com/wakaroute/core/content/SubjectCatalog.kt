package com.wakaroute.core.content

import com.wakaroute.core.map.SchoolSubject

/** One 教科 and the paths (領域) that belong to it, in the order the server lists them. */
data class SubjectPaths(val subject: SchoolSubject, val paths: List<PathSummary>) {
    val courseCount: Int get() = paths.sumOf { it.courseCount }
}

/**
 * Sorts paths under the five 教科 by their labels — the same rule as iOS's
 * `SubjectCatalog`.
 *
 * Matching is on the label, never the path name. A path named 「数学・数と式」 is
 * only 数学 because it carries the 数学 label; splitting the name would drop a
 * whole subject the first time someone renamed a path.
 */
object SubjectCatalog {

    /**
     * Only 教科 that have something to open, in display order.
     *
     * A 教科 with no paths, or paths with no courses, is left out rather than
     * shown as 準備中 — what is not there is not shown.
     */
    fun group(paths: List<PathSummary>): List<SubjectPaths> =
        SchoolSubject.entries
            .map { subject ->
                SubjectPaths(
                    subject = subject,
                    paths = paths.filter { it.courseCount > 0 && it.belongsTo(subject) },
                )
            }
            .filter { it.paths.isNotEmpty() }

    /** The 領域 label — whichever label is not a 教科 — or the path name without one. */
    fun domainName(path: PathSummary): String {
        val subjects = SchoolSubject.entries.map { normalise(it.label) }.toSet()
        return path.labels.firstOrNull { normalise(it) !in subjects } ?: path.name
    }

    private fun PathSummary.belongsTo(subject: SchoolSubject) =
        labels.any { normalise(it) == normalise(subject.label) }

    /**
     * Ignores half- and full-width spaces. A label typed as 「数学 」 would
     * otherwise drop out of its subject silently.
     */
    private fun normalise(label: String) = label.filterNot { it.isWhitespace() || it == '　' }
}
