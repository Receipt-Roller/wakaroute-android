package com.wakaroute.core.map

import com.wakaroute.core.net.WakaRouteJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The graph is data, and data can be wrong in ways the compiler cannot see.
 *
 * iOS validates it at build time for the same reason: a cycle or a dangling
 * edge would otherwise reach a student as a map that sends them backwards
 * forever, and the symptom on the device looks nothing like the cause.
 */
class PrerequisiteGraphTest {

    @Test
    fun `the shipped math graph has no problems`() {
        val problems = PrerequisiteGraph.math().problems()
        assertEquals("The bundled 数学 graph must be valid: $problems", emptyList<String>(), problems)
    }

    @Test
    fun `the math graph covers four domains and twenty six elements`() {
        val graph = PrerequisiteGraph.math()
        assertEquals(4, graph.domains.size)
        assertEquals(26, graph.elements.size)
    }

    @Test
    fun `prerequisites cross domain boundaries`() {
        // The reason a graph is needed at all rather than an ordering index.
        // 三平方の定理 sits in 図形 but needs 平方根 from 数と式.
        val subject = PrerequisiteGraph.math().toSubject(SchoolSubject.Math.id)
        val pythagoras = subject.elements.first { it.name == "三平方の定理" }
        val squareRoot = subject.elements.first { it.name == "平方根" }

        assertEquals("B", pythagoras.domainCode)
        assertEquals("A", squareRoot.domainCode)
        assertTrue(squareRoot.id in pythagoras.prerequisiteIds)
    }

    @Test
    fun `a cycle is reported rather than hung on`() {
        val problems = graphOf(
            """{"courseId":"a","title":"A","domain":"A","requires":["b"]}""",
            """{"courseId":"b","title":"B","domain":"A","requires":["a"]}""",
        ).problems()

        assertTrue("Expected a cycle in $problems", problems.any { it.startsWith("cycle:") })
    }

    @Test
    fun `an edge to a course outside the graph is reported and dropped`() {
        val graph = graphOf(
            """{"courseId":"a","title":"A","domain":"A","requires":["ghost"]}""",
        )

        assertTrue(graph.problems().any { it.contains("ghost") })

        // Dropped rather than kept: a prerequisite that can never be satisfied
        // would block its dependant forever with nothing to explain why.
        val element = graph.toSubject(SubjectId("test")).elements.single()
        assertEquals(emptyList<ElementId>(), element.prerequisiteIds)
    }

    @Test
    fun `a duplicated course is reported`() {
        val problems = graphOf(
            """{"courseId":"a","title":"A","domain":"A","requires":[]}""",
            """{"courseId":"a","title":"A again","domain":"A","requires":[]}""",
        ).problems()

        assertTrue(problems.any { it.contains("more than once") })
    }

    @Test
    fun `an undeclared domain is reported`() {
        val problems = graphOf(
            """{"courseId":"a","title":"A","domain":"Z","requires":[]}""",
        ).problems()

        assertTrue(problems.any { it.contains("domain Z") })
    }

    @Test
    fun `a course missing from live content is reported`() {
        val problems = graphOf(
            """{"courseId":"a","title":"A","domain":"A","requires":[]}""",
        ).problems(liveCourseIds = emptySet())

        assertTrue(problems.any { it.contains("no longer exists") })
    }

    @Test
    fun `live titles win over the authored ones`() {
        // Names change. The graph's title is a diagnostic label, never a key,
        // so a rename must show through rather than fork the 要素.
        val graph = graphOf("""{"courseId":"a","title":"old name","domain":"A","requires":[]}""")
        val subject = graph.toSubject(SubjectId("test"), titlesByCourseId = mapOf("a" to "new name"))

        assertEquals("new name", subject.elements.single().name)
        assertEquals(ElementId("a"), subject.elements.single().id)
    }

    @Test
    fun `the bundled graph loads through the class loader`() {
        // It is loaded as a JVM resource rather than an Android asset precisely
        // so this test can run without a device.
        assertNotNull(PrerequisiteGraph::class.java.getResourceAsStream("/graphs/prerequisites-math.json"))
    }

    private fun graphOf(vararg elements: String): PrerequisiteGraph =
        WakaRouteJson.decodeFromString(
            PrerequisiteGraph.serializer(),
            """
            {
              "asOf": "2026-08-05",
              "subject": "テスト",
              "domains": [{ "code": "A", "name": "テスト領域", "pathId": "p" }],
              "elements": [${elements.joinToString(",")}]
            }
            """.trimIndent(),
        )
}
