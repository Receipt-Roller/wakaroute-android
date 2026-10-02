package com.wakaroute.core.map

import com.wakaroute.core.content.CourseProgress
import com.wakaroute.core.content.QuizAttempt
import com.wakaroute.core.net.ApiTimestamp
import java.time.Instant

/**
 * Works out how well each 要素 is understood, from what MANABU2 records.
 *
 * Only the first three levels are measurable today:
 *
 * | Level | 意味 | Evidence available |
 * |---|---|---|
 * | 0 まだ | — | nothing touched |
 * | 1 意味がわかる | read it | lessons completed |
 * | 2 基本を解ける | got it right | course finished, and no quiz sat was missed |
 * | 3 根拠をつなげる | — | **needs a per-要素 確認テスト, none authored** |
 * | 4 初見で使える | — | needs 確認テスト |
 * | 5 時間内に安定する | — | needs test time limits (LMS-DEV t-d1bea71) |
 *
 * The スタート診断 that do exist test a whole 領域 at once, and are
 * deliberately not used here: passing one says nothing about which 要素
 * inside it are solid.
 *
 * Nothing is ever reported above 2. That is not caution for its own sake:
 * level 4 is [MasteryLevel.MasteredThreshold], so **no 要素 can ever be called
 * 習得** on evidence that would not support it. Screens must draw the upper
 * levels as 準備中 rather than 未達成 — a student has not failed a level nobody
 * can measure.
 *
 * **Do not infer readiness for 受験 from an untimed quiz pass.**
 */
object MasteryDerivation {

    /**
     * Derives the record for one subject from the two responses the app
     * already loads. The 理解マップ therefore costs no extra requests.
     */
    fun record(progress: List<CourseProgress>, quizAttempts: List<QuizAttempt>): MasteryRecord {
        val progressByCourse = progress.associateBy { it.courseId }
        val attemptsByCourse = latestAttemptPerLesson(quizAttempts).values
            .filter { it.courseId.isNotBlank() }
            .groupBy { it.courseId }

        val levels = mutableMapOf<ElementId, MasteryLevel>()
        val struggling = mutableSetOf<ElementId>()

        for (courseId in progressByCourse.keys + attemptsByCourse.keys) {
            val attempts = attemptsByCourse[courseId].orEmpty()
            val element = ElementId(courseId)

            levels[element] = level(progressByCourse[courseId], attempts)

            // The only hard evidence that something did not stick. Everything
            // else about a 要素 at 意味がわかる is equally consistent with a
            // student simply being partway through it.
            if (attempts.any { !it.isPassed }) struggling += element
        }

        return MasteryRecord(levels = levels, struggling = struggling)
    }

    /**
     * One course's level.
     *
     * 基本を解ける is a claim about the **whole** 要素, so it needs the whole
     * 要素: every lesson finished, and nothing sat and missed.
     *
     * This is the mistake iOS shipped. 正の数・負の数 has 23 lessons; passing
     * the quiz on lesson 1 was treated as mastering the 要素, which unlocked the
     * seven things built on top of it. **A pass twenty-two lessons from the end
     * says a great deal about that lesson and nothing about the 要素.**
     *
     * A course with no quiz still reaches 2 once its lessons are done.
     * Otherwise a 要素 nobody wrote a quiz for would block everything
     * downstream forever, and the student would be sent back over work they
     * had already finished.
     */
    fun level(progress: CourseProgress?, attempts: List<QuizAttempt>): MasteryLevel {
        val started = (progress?.completedLessons ?: 0) > 0 || attempts.isNotEmpty()
        if (!started) return MasteryLevel.NotStarted

        // A missed quiz caps the level however much of the course is done.
        if (attempts.any { !it.isPassed }) return MasteryLevel.UnderstandsMeaning

        return if (progress?.isCompleted == true) {
            MasteryLevel.SolvesBasics
        } else {
            MasteryLevel.UnderstandsMeaning
        }
    }

    /**
     * The most recent attempt for each lesson.
     *
     * Only the latest counts. A student who failed at 60% and later passed at
     * 90% has fixed it, and holding the failure against them would send them
     * backwards over work they have already redone. It cuts the other way too:
     * pass first, fail later, and the app says so again.
     *
     * The API documents these as newest-first, but the order is re-derived here
     * rather than trusted — a server-side sort change would otherwise invert
     * every student's 理解度 silently.
     */
    fun latestAttemptPerLesson(attempts: List<QuizAttempt>): Map<String, QuizAttempt> {
        val latest = mutableMapOf<String, QuizAttempt>()

        for (attempt in attempts) {
            if (attempt.lessonId.isBlank()) continue
            val moment = attempt.sittingMoment() ?: continue

            val held = latest[attempt.lessonId]?.sittingMoment()

            if (held == null || moment.isAfter(held)) {
                latest[attempt.lessonId] = attempt
            }
        }

        return latest
    }

    /**
     * When the quiz was actually sat, or null if it was not.
     *
     * **Keyed on `completedAt` alone, deliberately.** An attempt row carries
     * `isPassed = false` from the moment it exists, so falling back to
     * `startedAt` would let a quiz the student opened and closed — without
     * answering anything — register as a failure. That is not a 「つまずき」:
     * 共通判断規則 §3 requires evidence that they sat it and missed, and
     * abandoning a quiz is evidence of nothing.
     *
     * In practice the backend sets `completedAt` on submit and the field is
     * declared non-nullable, so this drops nothing today. It is here so that an
     * unsubmitted attempt can never become a false stumble if that changes.
     *
     * Parsed with [ApiTimestamp] — the backend sends seven-digit fractional
     * seconds.
     */
    private fun QuizAttempt.sittingMoment(): Instant? = ApiTimestamp.parse(completedAt)
}
