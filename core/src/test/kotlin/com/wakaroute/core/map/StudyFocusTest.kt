package com.wakaroute.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules iOS reached after three production mistakes of the same shape.
 *
 * All three came from one cause: a state that is correct in the model —
 * `blocked`, level 1 — standing in for situations that mean different things to
 * a student. These tests exist to keep the Android client from rediscovering it.
 */
class StudyFocusTest {

    private val subject = PrerequisiteGraph.math().toSubject(SchoolSubject.Math.id)
    private val squareRoot = subject.elements.first { it.name == "平方根" }
    private val positiveNegative = subject.elements.first { it.name == "正の数・負の数" }
    private val letterExpressions = subject.elements.first { it.name == "文字を用いた式" }
    private val linearFunction = subject.elements.first { it.name == "一次関数" }

    @Test
    fun `on a first launch almost everything is blocked and none of it is a stumble`() {
        val focus = StudyFocus(subject, MasteryRecord.Empty)

        val blocked = subject.elements.count { focus.readiness(it) is ElementReadiness.Blocked }
        assertEquals("23 of the 26 数学 要素 start blocked — that is normal", 23, blocked)

        // The whole point. Blocked is the ordinary condition of work not yet
        // reached; calling it つまずき lights a warning on every 領域 for every
        // student, permanently, and tells beginners they have already failed.
        assertTrue(subject.elements.none(focus::isStumbling))
    }

    @Test
    fun `a stumble needs a failed quiz, not merely a low level`() {
        // 正の数・負の数 is partway through: read, not finished, nothing missed.
        val partway = MasteryRecord(levels = mapOf(positiveNegative.id to MasteryLevel.UnderstandsMeaning))
        assertFalse(StudyFocus(subject, partway).isStumbling(letterExpressions))

        // Same level, but the quiz was sat and missed. Only this is a stumble.
        val missed = MasteryRecord(
            levels = mapOf(positiveNegative.id to MasteryLevel.UnderstandsMeaning),
            struggling = setOf(positiveNegative.id),
        )
        assertTrue(StudyFocus(subject, missed).isStumbling(letterExpressions))
    }

    @Test
    fun `root causes point at the earliest gap, not the symptom`() {
        // A student stuck on 一次関数 with nothing behind it done. Telling them
        // to practise 一次関数 is useless when the real gap is further back.
        val causes = StudyFocus(subject, MasteryRecord.Empty).rootCauses(linearFunction)

        assertTrue(causes.isNotEmpty())
        assertTrue(
            "root causes should be things that can actually be studied now",
            causes.all { StudyFocus(subject, MasteryRecord.Empty).readiness(it) is ElementReadiness.Ready },
        )
        assertTrue(causes.map { it.name }.contains("正の数・負の数"))
    }

    @Test
    fun `a mastered prerequisite stops the walk backwards`() {
        val solid = MasteryRecord(
            levels = subject.elements
                .filter { it.name in setOf("正の数・負の数", "文字を用いた式", "一次方程式", "変数と関数", "比例・反比例") }
                .associate { it.id to MasteryLevel.SolvesBasics },
        )

        assertEquals(ElementReadiness.Ready, StudyFocus(subject, solid).readiness(linearFunction))
    }

    @Test
    fun `recommendations rank by how much they unblock`() {
        val focus = StudyFocus(subject, MasteryRecord.Empty)
        val recommendations = focus.recommendations(limit = 3)

        assertEquals(3, recommendations.size)

        // Sorting by level alone would put every untouched 要素 above the one
        // holding the subject up — the "study the symptom" behaviour the map
        // exists to prevent.
        assertEquals("正の数・負の数", recommendations.first().element.name)
        assertTrue(
            recommendations.zipWithNext().all { (a, b) -> a.unblocks.size >= b.unblocks.size },
        )
    }

    @Test
    fun `recommendation order is stable across runs`() {
        // Ties break on id. Without that the advice reshuffles between launches
        // and a student cannot tell whether it changed or the app did.
        val focus = StudyFocus(subject, MasteryRecord.Empty)
        assertEquals(
            focus.recommendations(limit = 5).map { it.element.id },
            focus.recommendations(limit = 5).map { it.element.id },
        )
    }

    @Test
    fun `the same element never appears twice in one list`() {
        val focus = StudyFocus(subject, MasteryRecord.Empty)
        val ids = focus.recommendations(limit = 10).map { it.element.id }

        assertEquals("one 要素, one row — two rows saying different things is worse than one", ids.size, ids.toSet().size)
    }

    @Test
    fun `nothing is ever reported as mastered`() {
        // 習得 needs level 4, which needs a 確認テスト that does not exist. A ring
        // built on mastered would read 0 for a student who finished everything.
        val everythingMeasured = MasteryRecord(
            levels = subject.elements.associate { it.id to MasteryLevel.HighestMeasurable },
        )
        val focus = StudyFocus(subject, everythingMeasured)

        assertTrue(subject.elements.none { focus.readiness(it) is ElementReadiness.Mastered })
        assertEquals(
            26,
            SubjectProgress.of(subject, everythingMeasured).solidCount,
        )
    }

    @Test
    fun `a solid prerequisite unblocks what sits on it`() {
        val focus = StudyFocus(
            subject,
            MasteryRecord(levels = mapOf(subject.elements.first { it.name == "式の計算" }.id to MasteryLevel.SolvesBasics)),
        )
        assertEquals(ElementReadiness.Ready, focus.readiness(squareRoot))
    }
}
