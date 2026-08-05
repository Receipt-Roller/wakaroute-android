package com.wakaroute.core.schools

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SchoolTest {

    /**
     * The worked example from the iOS notes: 312 applicants, 300 examinees,
     * 240 places, 245 admitted gives 志願倍率 1.30, 受験倍率 1.25, 実質倍率 1.22.
     *
     * These are not interchangeable. Showing 1.22 as plain 「倍率」 beside a
     * school's published 1.30 looks like an error — or is taken as one.
     */
    private val fullResult = SchoolAdmissionResult(
        academicYear = 2026,
        capacity = 240,
        applicants = 312,
        examinees = 300,
        admitted = 245,
    )

    @Test
    fun `the most informative ratio is 実質倍率 when the numbers allow it`() {
        val ratio = fullResult.competitionRatio!!
        assertEquals(SchoolAdmissionResult.CompetitionRatio.Kind.Actual, ratio.kind)
        assertEquals(1.22, ratio.value, 0.01)
    }

    @Test
    fun `it falls back to 受験倍率 then 志願倍率`() {
        assertEquals(
            SchoolAdmissionResult.CompetitionRatio.Kind.Examinee,
            fullResult.copy(admitted = null).competitionRatio!!.kind,
        )
        assertEquals(
            SchoolAdmissionResult.CompetitionRatio.Kind.Applicant,
            fullResult.copy(admitted = null, examinees = null).competitionRatio!!.kind,
        )
    }

    @Test
    fun `no ratio is invented when the numbers are not there`() {
        assertNull(SchoolAdmissionResult(academicYear = 2026).competitionRatio)
        // Dividing by a published capacity of zero would produce Infinity, and
        // 「倍率 ∞」 is not a thing to show a 中3 student.
        assertNull(fullResult.copy(admitted = 0, capacity = 0).competitionRatio)
    }

    @Test
    fun `every ratio carries its own label and explanation`() {
        for (kind in SchoolAdmissionResult.CompetitionRatio.Kind.entries) {
            assertEquals(false, kind.label.isBlank())
            assertEquals(false, kind.explanation.isBlank())
        }
    }

    @Test
    fun `a 偏差値 band reads as a range`() {
        assertEquals("58", SchoolDeviationScore(value = 58.0).displayText)
        assertEquals("55〜60", SchoolDeviationScore(valueLow = 55.0, valueHigh = 60.0).displayText)
        assertNull(SchoolDeviationScore().displayText)
    }

    @Test
    fun `the next exam date skips sittings that have already passed`() {
        // The catalogue can carry several years at once.
        val detail = SchoolDetail(
            school = School(id = "TEST-0000001", name = "テスト高等学校"),
            examSchedules = listOf(
                SchoolExamSchedule(academicYear = 2025, testDates = listOf("2025-02-21")),
                SchoolExamSchedule(academicYear = 2026, testDates = listOf("2026-02-21", "2026-02-22")),
            ),
        )

        assertEquals(LocalDate.of(2026, 2, 21), detail.nextExamDate(LocalDate.of(2025, 8, 5)))
        assertNull(detail.nextExamDate(LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun `a schedule published without dates is not an error`() {
        // 未発表 is a real and common state: the schedule exists before the
        // dates are fixed.
        val detail = SchoolDetail(
            school = School(id = "TEST-0000001", name = "テスト高等学校"),
            examSchedules = listOf(SchoolExamSchedule(academicYear = 2027, statusLabel = "未発表")),
        )

        assertNull(detail.nextExamDate(LocalDate.of(2026, 8, 5)))
    }

    @Test
    fun `prefecture codes are the key, not names`() {
        assertEquals(47, Prefecture.all.size)
        assertEquals("東京都", Prefecture.named("13"))
        assertEquals(47, Prefecture.all.map { it.code }.toSet().size)
        assertNull(Prefecture.named("99"))
    }

    @Test
    fun `an unfiltered query knows it is unfiltered`() {
        assertEquals(true, SchoolSearchQuery().isEmpty)
        assertEquals(true, SchoolSearchQuery(keyword = "  ").isEmpty)
        assertEquals(false, SchoolSearchQuery(prefectureCode = "13").isEmpty)
    }
}
