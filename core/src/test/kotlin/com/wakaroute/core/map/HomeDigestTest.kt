package com.wakaroute.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wording of 「つぎにやること」.
 *
 * 共通判断規則 §3 records that iOS shipped the same mistake three times here.
 * The failure is always the same shape: a student who has not begun is told to
 * go back, because 「まだ始めていない」 and 「やって間違えた」 were the same state in
 * the model.
 */
class HomeDigestTest {

    private val subject = PrerequisiteGraph.math().toSubject(SchoolSubject.Math.id)
    private val positiveNegative = subject.elements.first { it.name == "正の数・負の数" }

    @Test
    fun `a student who has not begun is never told to go back`() {
        // The mistake, three times over. On a first launch every row is like
        // this, and 「もう一度ここを固めると」 would be the first thing the app ever
        // said to them.
        val steps = HomeDigest.nextSteps(subject, MasteryRecord.Empty)

        assertTrue(steps.isNotEmpty())
        assertTrue(steps.all { it.tone == NextStep.Tone.NotStarted })
        assertTrue(steps.all { it.message.startsWith("ここから始めると") })
        assertTrue(steps.none { it.message.contains("もう一度") })
    }

    @Test
    fun `being blocked is not being stuck`() {
        // 23 of the 26 要素 are blocked on day one. If blocked drove the tone,
        // every row would be a warning for every student, permanently.
        val focus = StudyFocus(subject, MasteryRecord.Empty)
        val blocked = subject.elements.count { focus.readiness(it) is ElementReadiness.Blocked }

        assertEquals(23, blocked)
        assertTrue(HomeDigest.nextSteps(subject, MasteryRecord.Empty).none { it.tone == NextStep.Tone.Stumbling })
    }

    @Test
    fun `partway through reads as in progress, not as a problem`() {
        // Level 1 with no failed quiz: they are reading. Nothing has gone wrong.
        val partway = MasteryRecord(levels = mapOf(positiveNegative.id to MasteryLevel.UnderstandsMeaning))
        val step = HomeDigest.nextSteps(subject, partway).first { it.element.id == positiveNegative.id }

        assertEquals(NextStep.Tone.InProgress, step.tone)
        assertTrue(step.message.startsWith("ここを終えると"))
    }

    @Test
    fun `the same level with a failed quiz reads as a stumble`() {
        // Identical level, opposite meaning. This is the distinction the whole
        // struggling flag exists for.
        val missed = MasteryRecord(
            levels = mapOf(positiveNegative.id to MasteryLevel.UnderstandsMeaning),
            struggling = setOf(positiveNegative.id),
        )
        val step = HomeDigest.nextSteps(subject, missed).first { it.element.id == positiveNegative.id }

        assertEquals(NextStep.Tone.Stumbling, step.tone)
        assertTrue(step.message.startsWith("もう一度ここを固めると"))
    }

    @Test
    fun `every row says where it leads, not what was not done`() {
        val steps = HomeDigest.nextSteps(subject, MasteryRecord.Empty)

        assertTrue(steps.all { it.message.endsWith("に進めます") })
        // Nothing that reads as an accusation.
        assertTrue(steps.none { it.message.contains("できていません") })
        assertTrue(steps.none { it.message.contains("未達成") })
    }

    @Test
    fun `one row per 要素`() {
        // §4: 「同じコースは1行だけ」. Two rows saying different things about one
        // course is worse than one row.
        val steps = HomeDigest.nextSteps(subject, MasteryRecord.Empty, limit = 10)

        assertEquals(steps.size, steps.map { it.element.id }.toSet().size)
    }

    @Test
    fun `rows are capped and ordered by what they unblock`() {
        val steps = HomeDigest.nextSteps(subject, MasteryRecord.Empty, limit = 2)

        assertEquals(2, steps.size)
        assertEquals("正の数・負の数", steps.first().element.name)
        assertTrue(steps.zipWithNext().all { (a, b) -> a.unblocks.size >= b.unblocks.size })
    }

    @Test
    fun `a step that unblocks nothing still reads sensibly`() {
        // Reachable once the graph is mostly solid: nothing left to trace back
        // to, so there is no 「◯◯に進めます」 to offer.
        val step = NextStep(positiveNegative, NextStep.Tone.NotStarted, unblocks = emptyList())

        assertEquals("ここから始められます", step.message)
    }
}
