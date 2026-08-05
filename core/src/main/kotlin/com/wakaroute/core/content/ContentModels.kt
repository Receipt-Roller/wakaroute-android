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

/** `GET /api/v1/courses/{id}` — a 要素, broken into its sections and lessons. */
@Serializable
data class CourseDetail(
    val id: String,
    val title: String = "",
    val description: String? = null,
    @Serializable(with = LenientInt::class) val lessonCount: Int = 0,
    val sections: List<CourseSection> = emptyList(),
) {
    /** Every lesson in teaching order, section boundaries flattened away. */
    val lessons: List<LessonSummary>
        get() = sections.sortedBy { it.orderIndex }.flatMap { it.lessons.sortedBy { l -> l.orderIndex } }
}

@Serializable
data class CourseSection(
    val id: String,
    val title: String = "",
    val summary: String? = null,
    @Serializable(with = LenientInt::class) val orderIndex: Int = 0,
    val lessons: List<LessonSummary> = emptyList(),
)

@Serializable
data class LessonSummary(
    val id: String,
    val sectionId: String = "",
    val title: String = "",
    val summary: String? = null,
    @Serializable(with = LenientInt::class) val orderIndex: Int = 0,
    val hasVideo: Boolean = false,
    val hasSlides: Boolean = false,
    val hasQuiz: Boolean = false,
)

/**
 * `GET /api/v1/lessons/{id}`.
 *
 * The quiz arrives **without its answer key** — grading happens on the server.
 * That is not an inconvenience to work around: 共通判断規則 §5 forbids showing a
 * score the app worked out itself, and having no key makes that impossible
 * rather than merely discouraged.
 */
@Serializable
data class LessonDetail(
    val id: String,
    val courseId: String = "",
    val sectionId: String = "",
    val title: String = "",
    val summary: String? = null,
    val bodyHtml: String? = null,
    val videoUrl: String? = null,
    val slidesEmbedUrl: String? = null,
    @Serializable(with = LenientInt::class) val orderIndex: Int = 0,
    val quiz: Quiz? = null,
)

@Serializable
data class Quiz(
    val id: String,
    val title: String? = null,
    val instructions: String? = null,
    @Serializable(with = LenientInt::class) val passingScorePercent: Int = 0,
    val isRequired: Boolean = false,
    val questions: List<QuizQuestion> = emptyList(),
)

@Serializable
data class QuizQuestion(
    val id: String,
    val questionText: String = "",
    val imageUrl: String? = null,
    /** `MultipleChoiceSingle` is the only kind authored so far. */
    val questionType: String = "",
    @Serializable(with = LenientInt::class) val orderIndex: Int = 0,
    val options: List<QuizOption> = emptyList(),
)

@Serializable
data class QuizOption(
    val id: String,
    val text: String = "",
    @Serializable(with = LenientInt::class) val orderIndex: Int = 0,
)

/** `GET /api/v1/me/progress/{courseId}` — the same course, lesson by lesson. */
@Serializable
data class CourseProgressDetail(
    val courseId: String,
    val courseTitle: String = "",
    @Serializable(with = LenientInt::class) val totalLessons: Int = 0,
    @Serializable(with = LenientInt::class) val completedLessons: Int = 0,
    val isCompleted: Boolean = false,
    val lessons: List<LessonProgress> = emptyList(),
) {
    fun forLesson(lessonId: String): LessonProgress? = lessons.firstOrNull { it.lessonId == lessonId }
}

@Serializable
data class LessonProgress(
    val lessonId: String,
    val title: String = "",
    val isViewed: Boolean = false,
    val isCompleted: Boolean = false,
    /**
     * The latest quiz result for this lesson, as the server sees it.
     *
     * Note the shape: `null` when the quiz has never been sat, which is a third
     * thing beside passed and failed. 共通判断規則 §3 turns on exactly that
     * distinction — a quiz not yet taken is not a stumble.
     */
    val latestQuizPassed: Boolean? = null,
    @Serializable(with = LenientInt::class) val latestQuizScorePercent: Int? = null,
    val completedAt: String? = null,
)

/** The body of `POST /api/v1/lessons/{id}/quiz/submit`. */
@Serializable
data class QuizAnswer(val questionId: String, val optionId: String? = null, val textAnswer: String? = null)

@Serializable
internal data class QuizSubmission(val answers: List<QuizAnswer>)

/**
 * The graded result. **Produced by the server, never by the app.**
 *
 * §5: 「オフラインで点数を出さない」. When a submission cannot be sent, the honest
 * answer is 「いま採点できないので、通信できたときに送ります」 — an invented score
 * would be worse than admitting that.
 */
@Serializable
data class QuizResult(
    val attemptId: String = "",
    val quizId: String = "",
    @Serializable(with = LenientInt::class) val scorePercent: Int = 0,
    @Serializable(with = LenientInt::class) val passingScorePercent: Int = 0,
    val isPassed: Boolean = false,
    val completedAt: String? = null,
)

@Serializable
data class LessonCompletion(
    val lessonId: String = "",
    val courseId: String = "",
    val isCompleted: Boolean = false,
    val course: CourseProgress? = null,
)

/**
 * `POST /api/v1/lessons/{id}/feedback` — §6 of the 共通判断規則.
 *
 * Two-valued on purpose. A five-point scale has 中学生 cluster on the middle,
 * the average never moves, and nobody learns which lesson to fix.
 */
@Serializable
data class LessonFeedbackRequest(
    val understood: Boolean,
    /** Dropped by the server when [understood] is true, so it is not sent then. */
    val reasons: List<String> = emptyList(),
    /**
     * Free text, **read by a human**. The input field must tell the student not
     * to write their name or their school.
     *
     * Over 1000 characters is a 400 — a permanent failure, which the offline
     * queue discards. Truncated before it ever gets that far.
     */
    val comment: String? = null,
)

/** The server's acknowledgement. Nothing in it is shown to the student. */
@Serializable
data class LessonFeedbackAcknowledgement(val lessonId: String = "")

/**
 * One stretch of study, as MANABU2 stores it.
 *
 * The app records **finished** sessions rather than driving the server's clock
 * with start/stop. A timer that needs the network to start is a timer that does
 * not work on a train, which is the situation this whole feature exists for.
 */
@Serializable
data class StudySession(
    val sessionId: String = "",
    /** Our own id for the session. The server deduplicates replays on it. */
    val clientSessionId: String? = null,
    val courseId: String? = null,
    val lessonId: String? = null,
    val subject: String? = null,
    val kind: String = "",
    val startedAt: String? = null,
    val endedAt: String? = null,
    /** `yyyy-MM-dd` in the learner's own day, as the server reckons it. */
    val studyDate: String = "",
    @Serializable(with = LenientInt::class) val durationSeconds: Int? = null,
    val status: String = "",
)

/**
 * `POST /api/v1/me/study-sessions` — a session that already finished.
 *
 * [clientSessionId] is what makes the offline queue safe here: a replay of the
 * same session is recognised and not counted twice. It is generated when the
 * timer stops and stored with the queued record, never regenerated on resend —
 * the same rule as the quiz idempotency key, for the same reason.
 */
@Serializable
data class RecordStudySession(
    val clientSessionId: String,
    val startedAt: String,
    val endedAt: String,
    @Serializable(with = LenientInt::class) val durationSeconds: Int,
    val subject: String? = null,
    val courseId: String? = null,
    val lessonId: String? = null,
    val kind: String? = null,
)

/** `GET /api/v1/me/study-streak`. */
@Serializable
data class StudyStreak(
    @Serializable(with = LenientInt::class) val currentDays: Int = 0,
    @Serializable(with = LenientInt::class) val longestDays: Int = 0,
    val lastStudyDate: String? = null,
) {
    companion object {
        val None = StudyStreak()
    }
}

/**
 * One day in `GET /api/v1/me/study-summary`.
 *
 * The series is dense — zero days included — so the client never has to work
 * out how many days a month has or where a range ends. Filling those gaps
 * locally is, in the API's own words, the thing a client reliably gets wrong.
 */
@Serializable
data class StudyDay(
    val date: String,
    @Serializable(with = LenientInt::class) val totalSeconds: Int = 0,
    @Serializable(with = LenientInt::class) val sessionCount: Int = 0,
)
