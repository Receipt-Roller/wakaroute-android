package com.wakaroute.core.map

import org.junit.Assert.assertEquals
import org.junit.Test

class DomainProgressTest {

    private val subject = PrerequisiteGraph.math().toSubject(SchoolSubject.Math.id)

    private fun progress(code: String, mastery: MasteryRecord) =
        DomainProgress.of(subject.domain(code)!!, subject, mastery)

    @Test
    fun `an untouched domain reads as これから, not つまずき`() {
        // 関数 begins entirely blocked, because everything in it needs 数と式.
        // A 中3 opening it for the first time must not be told they have already
        // failed at it.
        assertEquals(DomainProgress.Standing.NotStarted, progress("C", MasteryRecord.Empty).standing)
    }

    @Test
    fun `a domain being worked through without failures reads as 学習中`() {
        val started = MasteryRecord(
            levels = mapOf(subject.elements.first { it.name == "正の数・負の数" }.id to MasteryLevel.UnderstandsMeaning),
        )
        assertEquals(DomainProgress.Standing.InProgress, progress("A", started).standing)
    }

    @Test
    fun `つまずき requires a failed quiz on a prerequisite`() {
        val missed = MasteryRecord(
            levels = mapOf(subject.elements.first { it.name == "正の数・負の数" }.id to MasteryLevel.UnderstandsMeaning),
            struggling = setOf(subject.elements.first { it.name == "正の数・負の数" }.id),
        )
        assertEquals(DomainProgress.Standing.Stumbling, progress("A", missed).standing)
    }

    @Test
    fun `strength is measured against 基本を解ける, not 習得`() {
        // Nothing can reach 習得 while no 確認テスト exists. A threshold on a
        // 習得 fraction would hold every 領域 at 学習中 forever.
        val allSolid = MasteryRecord(
            levels = subject.elementsIn("D").associate { it.id to MasteryLevel.SolvesBasics },
        )
        val domain = progress("D", allSolid)

        assertEquals(DomainProgress.Standing.Strong, domain.standing)
        assertEquals(1.0, domain.solidFraction, 0.0)
    }

    @Test
    fun `level counts cover every element exactly once`() {
        val domain = progress("A", MasteryRecord.Empty)
        assertEquals(domain.totalElements, domain.levelCounts.sumOf { it.second })
    }

    @Test
    fun `a first launch shows no stumbles anywhere`() {
        val standings = subject.domains.map { progress(it.code, MasteryRecord.Empty).standing }
        assertEquals(List(4) { DomainProgress.Standing.NotStarted }, standings)
    }
}
