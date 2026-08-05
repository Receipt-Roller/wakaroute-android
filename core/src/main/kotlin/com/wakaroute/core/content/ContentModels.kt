package com.wakaroute.core.content

import com.wakaroute.core.net.LenientInt
import kotlinx.serialization.Serializable

/**
 * One course's progress, as `GET /api/v1/me/progress` reports it.
 *
 * A 要素 on the 理解マップ **is** a course, so this is the per-要素 record.
 * Every integer is read leniently: the API's own schema declares them
 * `integer | string`.
 */
@Serializable
data class CourseProgress(
    val courseId: String,
    val courseTitle: String = "",
    @Serializable(with = LenientInt::class) val totalLessons: Int = 0,
    @Serializable(with = LenientInt::class) val completedLessons: Int = 0,
    /**
     * The server's own judgement that every lesson is done.
     *
     * Used rather than `completedLessons == totalLessons`, which disagrees the
     * moment a lesson is unpublished: the student finished everything that
     * existed, and a client-side comparison would quietly take that away.
     */
    val isCompleted: Boolean = false,
    val lastActivityAt: String? = null,
)

/**
 * One sitting of one lesson's quiz.
 *
 * `isPassed` is the only hard evidence the app has that something did **not**
 * stick, which makes this type the whole basis of 「つまずき」. Everything else a
 * student's record shows is equally consistent with them simply being partway
 * through.
 */
@Serializable
data class QuizAttempt(
    val attemptId: String = "",
    val lessonId: String = "",
    val courseId: String = "",
    @Serializable(with = LenientInt::class) val scorePercent: Int = 0,
    @Serializable(with = LenientInt::class) val passingScorePercent: Int = 0,
    val isPassed: Boolean = false,
    val startedAt: String? = null,
    val completedAt: String? = null,
)

/** `GET /api/v1/paths/{id}` — a 領域, with the courses that make it up. */
@Serializable
data class PathDetail(
    val id: String,
    val name: String = "",
    /**
     * Empty on this endpoint (LMS-DEV t-d1bea77).
     *
     * Not a problem for us: the bundled prerequisite graph already says which
     * 教科 and 領域 each path is, so nothing here needs to infer it. Modelled
     * only so a future fix is visible rather than silently ignored.
     */
    val labels: List<String> = emptyList(),
    @Serializable(with = LenientInt::class) val courseCount: Int = 0,
    val courses: List<CourseSummary> = emptyList(),
)

@Serializable
data class CourseSummary(
    val id: String,
    val title: String = "",
    @Serializable(with = LenientInt::class) val lessonCount: Int = 0,
)
