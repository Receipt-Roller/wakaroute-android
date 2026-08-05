package com.wakaroute.core.map

import com.wakaroute.core.content.CourseProgress
import com.wakaroute.core.content.QuizAttempt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 共通判断規則 §2, which the wiki calls the most dangerous part of the product.
 *
 * These are not defensive tests around a tricky function — each one is a rule
 * that, broken, makes the app tell a student something false about themselves
 * while every screen still looks correct.
 */
class MasteryDerivationTest {

    private val course = "course-1"
    private val other = "course-2"

    @Test
    fun `passing one quiz does not finish a twenty-three lesson course`() {
        // The mistake iOS shipped. 正の数・負の数 has 23 lessons; treating a
        // pass on lesson 1 as mastery of the 要素 unlocked the seven things
        // built on top of it, and sent the student onward over a foundation
        // they had barely touched.
        val level = MasteryDerivation.level(
            progress = CourseProgress(courseId = course, totalLessons = 23, completedLessons = 1),
            attempts = listOf(attempt(lesson = "lesson-1", passed = true)),
        )

        assertEquals(MasteryLevel.UnderstandsMeaning, level)
    }

    @Test
    fun `基本を解ける needs the whole course finished and nothing missed`() {
        val level = MasteryDerivation.level(
            progress = CourseProgress(courseId = course, totalLessons = 23, completedLessons = 23, isCompleted = true),
            attempts = listOf(attempt(lesson = "lesson-1", passed = true)),
        )

        assertEquals(MasteryLevel.SolvesBasics, level)
    }

    @Test
    fun `a missed quiz caps the level however much of the course is done`() {
        val level = MasteryDerivation.level(
            progress = CourseProgress(courseId = course, totalLessons = 5, completedLessons = 5, isCompleted = true),
            attempts = listOf(
                attempt(lesson = "lesson-1", passed = true),
                attempt(lesson = "lesson-2", passed = false),
            ),
        )

        assertEquals(MasteryLevel.UnderstandsMeaning, level)
    }

    @Test
    fun `a course nobody wrote a quiz for still reaches 基本を解ける`() {
        // Otherwise a 要素 with no quiz blocks everything downstream forever,
        // and the student is sent back over work they have already finished.
        val level = MasteryDerivation.level(
            progress = CourseProgress(courseId = course, totalLessons = 4, completedLessons = 4, isCompleted = true),
            attempts = emptyList(),
        )

        assertEquals(MasteryLevel.SolvesBasics, level)
    }

    @Test
    fun `untouched is まだ, not something worse`() {
        assertEquals(MasteryLevel.NotStarted, MasteryDerivation.level(progress = null, attempts = emptyList()))
        assertEquals(
            MasteryLevel.NotStarted,
            MasteryDerivation.level(CourseProgress(courseId = course, totalLessons = 5), emptyList()),
        )
    }

    @Test
    fun `nothing is ever derived above 基本を解ける`() {
        // Level 4 is the mastery threshold, and no evidence available today can
        // support it. A counter built on 習得 would read 0 for a student who
        // had finished everything there is.
        val record = MasteryDerivation.record(
            progress = listOf(CourseProgress(courseId = course, totalLessons = 3, completedLessons = 3, isCompleted = true)),
            quizAttempts = List(20) { attempt(lesson = "lesson-$it", passed = true) },
        )

        assertEquals(MasteryLevel.SolvesBasics, record[ElementId(course)])
        assertTrue(record[ElementId(course)].level <= MasteryLevel.HighestMeasurable.level)
    }

    @Test
    fun `only the latest attempt per lesson counts`() {
        // Failed at 60%, later passed at 90%. Holding the failure against them
        // sends a student backwards over work they have already redone.
        val record = MasteryDerivation.record(
            progress = listOf(CourseProgress(courseId = course, totalLessons = 1, completedLessons = 1, isCompleted = true)),
            quizAttempts = listOf(
                attempt(lesson = "lesson-1", passed = false, at = "2026-08-01T10:00:00Z"),
                attempt(lesson = "lesson-1", passed = true, at = "2026-08-02T10:00:00Z"),
            ),
        )

        assertEquals(MasteryLevel.SolvesBasics, record[ElementId(course)])
        assertFalse("a fixed failure is not a stumble", ElementId(course) in record.struggling)
    }

    @Test
    fun `and it cuts the other way too`() {
        // Passed, then sat it again and missed. The app says so again.
        val record = MasteryDerivation.record(
            progress = listOf(CourseProgress(courseId = course, totalLessons = 1, completedLessons = 1, isCompleted = true)),
            quizAttempts = listOf(
                attempt(lesson = "lesson-1", passed = true, at = "2026-08-01T10:00:00Z"),
                attempt(lesson = "lesson-1", passed = false, at = "2026-08-02T10:00:00Z"),
            ),
        )

        assertEquals(MasteryLevel.UnderstandsMeaning, record[ElementId(course)])
        assertTrue(ElementId(course) in record.struggling)
    }

    @Test
    fun `the server's ordering is not trusted`() {
        // Documented as newest-first. Re-derived anyway: a server-side sort
        // change would otherwise invert every student's 理解度 in silence.
        val newestLast = listOf(
            attempt(lesson = "lesson-1", passed = false, at = "2026-08-01T10:00:00Z"),
            attempt(lesson = "lesson-1", passed = true, at = "2026-08-05T10:00:00Z"),
        )

        assertEquals(true, MasteryDerivation.latestAttemptPerLesson(newestLast)["lesson-1"]!!.isPassed)
        assertEquals(true, MasteryDerivation.latestAttemptPerLesson(newestLast.reversed())["lesson-1"]!!.isPassed)
    }

    @Test
    fun `a quiz opened and abandoned is not a failure`() {
        // An attempt row carries isPassed = false from the moment it exists.
        // Counting one the student never submitted would turn "opened a quiz
        // and closed it" into 「つまずき」 — and §3 is explicit that a stumble
        // needs evidence they sat it and missed.
        val attempts = listOf(
            attempt(lesson = "lesson-1", passed = true, at = "2026-08-05T10:00:00Z"),
            QuizAttempt(lessonId = "lesson-1", courseId = course, isPassed = false, startedAt = "2026-08-05T11:00:00Z"),
        )

        assertEquals(true, MasteryDerivation.latestAttemptPerLesson(attempts)["lesson-1"]!!.isPassed)
    }

    @Test
    fun `an abandoned attempt alone leaves the 要素 untouched by it`() {
        val record = MasteryDerivation.record(
            progress = listOf(CourseProgress(courseId = course, totalLessons = 2, completedLessons = 2, isCompleted = true)),
            quizAttempts = listOf(
                QuizAttempt(lessonId = "lesson-1", courseId = course, isPassed = false, startedAt = "2026-08-05T11:00:00Z"),
            ),
        )

        assertEquals(MasteryLevel.SolvesBasics, record[ElementId(course)])
        assertFalse("opening a quiz is not stumbling", ElementId(course) in record.struggling)
    }

    @Test
    fun `seven digit fractional seconds are handled here too`() {
        // The shape production actually sends.
        val attempts = listOf(
            attempt(lesson = "lesson-1", passed = false, at = "2026-08-02T14:48:17.0204684+00:00"),
            attempt(lesson = "lesson-1", passed = true, at = "2026-08-02T15:48:17.9999999+00:00"),
        )

        assertEquals(true, MasteryDerivation.latestAttemptPerLesson(attempts)["lesson-1"]!!.isPassed)
    }

    @Test
    fun `a stumble in one 要素 does not mark another`() {
        val record = MasteryDerivation.record(
            progress = listOf(
                CourseProgress(courseId = course, totalLessons = 2, completedLessons = 2, isCompleted = true),
                CourseProgress(courseId = other, totalLessons = 2, completedLessons = 2, isCompleted = true),
            ),
            quizAttempts = listOf(attempt(lesson = "lesson-1", passed = false, courseId = other)),
        )

        assertEquals(MasteryLevel.SolvesBasics, record[ElementId(course)])
        assertFalse(ElementId(course) in record.struggling)
        assertEquals(MasteryLevel.UnderstandsMeaning, record[ElementId(other)])
        assertTrue(ElementId(other) in record.struggling)
    }

    @Test
    fun `an attempt with no course is dropped rather than guessed at`() {
        // Attributing it to the wrong 要素 would mark a stumble in something
        // the student never touched.
        val record = MasteryDerivation.record(
            progress = emptyList(),
            quizAttempts = listOf(QuizAttempt(lessonId = "lesson-1", courseId = "", isPassed = false, completedAt = "2026-08-01T10:00:00Z")),
        )

        assertTrue(record.isEmpty)
    }

    @Test
    fun `an unreadable timestamp does not silently win`() {
        // Dropped, so a garbage date cannot outrank a real attempt.
        val attempts = listOf(
            attempt(lesson = "lesson-1", passed = true, at = "2026-08-01T10:00:00Z"),
            QuizAttempt(lessonId = "lesson-1", courseId = course, isPassed = false, completedAt = "きのう"),
        )

        assertEquals(true, MasteryDerivation.latestAttemptPerLesson(attempts)["lesson-1"]!!.isPassed)
    }

    @Test
    fun `a first launch derives nothing at all`() {
        val record = MasteryDerivation.record(progress = emptyList(), quizAttempts = emptyList())

        assertTrue(record.isEmpty)
        assertEquals(MasteryLevel.NotStarted, record[ElementId(course)])
    }

    private fun attempt(
        lesson: String,
        passed: Boolean,
        at: String = "2026-08-01T10:00:00Z",
        courseId: String = course,
    ) = QuizAttempt(
        attemptId = "attempt-$lesson-$at",
        lessonId = lesson,
        courseId = courseId,
        isPassed = passed,
        startedAt = at,
        completedAt = at,
    )
}
