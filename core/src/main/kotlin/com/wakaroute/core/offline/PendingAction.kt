package com.wakaroute.core.offline

import com.wakaroute.core.content.QuizAnswer
import com.wakaroute.core.content.TestAnswer
import kotlinx.serialization.Serializable

/**
 * Something the student did that has not reached the server yet.
 *
 * 共通判断規則 §5: 学習時間・レッスン完了・クイズ提出・評価 are held on the device
 * and sent later — **so that a student who studied on a train does not lose
 * their record to the signal.**
 *
 * The three ways these merge are not an implementation detail; they are three
 * different facts about what a student did, and [MergeRule] names them.
 */
@Serializable
sealed interface PendingAction {
    /** This queue entry. Not the lesson, and not the idempotency key. */
    val id: String

    /** When the student did it, so the queue can be replayed in order. */
    val createdAtEpochSeconds: Long

    val lessonId: String

    val mergeRule: MergeRule

    /** Opening a lesson. */
    @Serializable
    data class MarkViewed(
        override val id: String,
        override val lessonId: String,
        override val createdAtEpochSeconds: Long,
    ) : PendingAction {
        override val mergeRule get() = MergeRule.Coalesce
    }

    /** 「読み終えた」. */
    @Serializable
    data class MarkComplete(
        override val id: String,
        override val lessonId: String,
        override val createdAtEpochSeconds: Long,
    ) : PendingAction {
        override val mergeRule get() = MergeRule.Coalesce
    }

    /**
     * A quiz sitting.
     *
     * [idempotencyKey] is created **when the student submits** and stored here,
     * so a resend days later presents the same one. Generating it at send time
     * would make every retry a fresh attempt: a second row in the history, a
     * different 理解度, and a changed 修了証 condition.
     */
    @Serializable
    data class SubmitQuiz(
        override val id: String,
        override val lessonId: String,
        override val createdAtEpochSeconds: Long,
        val answers: List<QuizAnswer>,
        val idempotencyKey: String,
    ) : PendingAction {
        override val mergeRule get() = MergeRule.Keep
    }

    /**
     * A test sitting. Same key rule as [SubmitQuiz].
     *
     * [lessonId] is empty: a test belongs to a 領域, not a lesson.
     * [elapsedSeconds] is fixed when the student submitted — a resend days
     * later must not report the days as time spent on the test.
     */
    @Serializable
    data class SubmitTest(
        override val id: String,
        override val createdAtEpochSeconds: Long,
        val testId: String,
        val answers: List<TestAnswer>,
        val elapsedSeconds: Int,
        val idempotencyKey: String,
        override val lessonId: String = "",
    ) : PendingAction {
        override val mergeRule get() = MergeRule.Keep
    }

    /**
     * A finished stretch of study.
     *
     * [clientSessionId] is the server's deduplication key, minted when the
     * timer stopped and never regenerated — the same rule as the quiz key. A
     * fresh one on a resend counts the same twenty minutes twice, and a student
     * looking at an inflated total has no way to tell it is wrong.
     */
    @Serializable
    data class RecordStudy(
        override val id: String,
        override val lessonId: String,
        override val createdAtEpochSeconds: Long,
        val clientSessionId: String,
        val startedAt: String,
        val endedAt: String,
        val durationSeconds: Int,
        val subject: String? = null,
        val courseId: String? = null,
    ) : PendingAction {
        // Two stretches of study are two stretches, exactly as two quiz
        // sittings are two sittings.
        override val mergeRule get() = MergeRule.Keep
    }

    /** 「わかった」/「むずかしかった」 on a lesson. */
    @Serializable
    data class RateLesson(
        override val id: String,
        override val lessonId: String,
        override val createdAtEpochSeconds: Long,
        val understood: Boolean,
        val reasons: List<String> = emptyList(),
        val comment: String? = null,
    ) : PendingAction {
        override val mergeRule get() = MergeRule.Replace
    }
}

/**
 * How a new action of the same kind, on the same lesson, combines with one
 * already waiting.
 *
 * Copied from 共通判断規則 §5 because the differences are the point:
 */
enum class MergeRule {
    /**
     * **まとめる.** Pressing twice does not make it twice true — a lesson opened
     * or finished is one fact however many times it was recorded.
     */
    Coalesce,

    /**
     * **まとめない.** A second sitting is different answers and a different
     * score. Collapsing two would erase one of the student's attempts.
     */
    Keep,

    /**
     * **置き換える.** One verdict per lesson, and if the student changed their
     * mind the later answer is what they mean. The server replaces it too.
     */
    Replace,
}
