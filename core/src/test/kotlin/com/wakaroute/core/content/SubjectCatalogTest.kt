package com.wakaroute.core.content

import com.wakaroute.core.map.SchoolSubject
import org.junit.Assert.assertEquals
import org.junit.Test

/** The same rules as iOS's `SubjectCatalogTests`, so the two apps group alike. */
class SubjectCatalogTest {

    @Test
    fun `paths are grouped by their label, in display order`() {
        val grouped = SubjectCatalog.group(
            listOf(
                path("p1", listOf("数学", "数と式")),
                path("p2", listOf("国語", "読む")),
                path("p3", listOf("数学", "図形")),
            ),
        )

        assertEquals(listOf(SchoolSubject.Japanese, SchoolSubject.Math), grouped.map { it.subject })
        assertEquals(listOf("p1", "p3"), grouped[1].paths.map { it.id })
    }

    @Test
    fun `the name is never parsed`() {
        // 「数学・数と式」 with no label belongs to nothing.
        val grouped = SubjectCatalog.group(listOf(path("p1", emptyList(), name = "数学・数と式")))

        assertEquals(emptyList<SubjectPaths>(), grouped)
    }

    @Test
    fun `a stray space in a label does not drop the path`() {
        val grouped = SubjectCatalog.group(listOf(path("p1", listOf("理科　"))))

        assertEquals(SchoolSubject.Science, grouped.single().subject)
    }

    @Test
    fun `a subject with nothing to open is not shown`() {
        // A path with no courses is not there yet, and neither is its 教科.
        val grouped = SubjectCatalog.group(listOf(path("p1", listOf("社会", "地理"), courseCount = 0)))

        assertEquals(emptyList<SubjectPaths>(), grouped)
    }

    @Test
    fun `the domain name is the label that is not a subject`() {
        assertEquals("数と式", SubjectCatalog.domainName(path("p1", listOf("数学", "数と式"))))
        assertEquals("名前", SubjectCatalog.domainName(path("p1", listOf("数学"), name = "名前")))
    }

    @Test
    fun `course counts add up across a subject's paths`() {
        val grouped = SubjectCatalog.group(
            listOf(path("p1", listOf("英語"), courseCount = 7), path("p2", listOf("英語"), courseCount = 5)),
        )

        assertEquals(12, grouped.single().courseCount)
    }

    private fun path(id: String, labels: List<String>, name: String = "領域", courseCount: Int = 3) =
        PathSummary(id = id, name = name, labels = labels, courseCount = courseCount)
}
