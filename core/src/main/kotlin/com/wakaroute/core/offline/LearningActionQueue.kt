package com.wakaroute.core.offline

import com.wakaroute.core.content.ContentClient
import com.wakaroute.core.content.QuizAnswer
import com.wakaroute.core.content.TestAnswer
import com.wakaroute.core.net.ApiError
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Holds what could not be sent, and sends it when it can.
 *
 * 共通判断規則 §5. Two rules shape everything here, and both are about not
 * losing a student's work:
 *
 * **Permanent and temporary failures are different.** A 4xx other than 408 and
 * 429 will fail identically forever — a completion for a lesson that has since
 * been deleted is a 404 for all time. Retried in order, it blocks every record
 * behind it permanently. Those are set aside and the queue moves on. 5xx and
 * network errors stop the run with the order intact.
 *
 * **The idempotency key travels with the submission**, created when the student
 * answered and reused on every resend. A key generated at send time makes each
 * retry a fresh attempt — a second row in the history, a changed 理解度, and a
 * different 修了証 condition.
 */
class LearningActionQueue(
    private val store: PendingActionStore,
    private val content: ContentClient,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /**
     * Serialises everything.
     *
     * The queue is read-modify-write on a single file, and a flush racing an
     * enqueue would either lose the new entry or re-send a sent one.
     */
    private val mutex = Mutex()

    suspend fun pending(): List<PendingAction> = mutex.withLock { store.read() }

    /**
     * Throws the queue away.
     *
     * Only for signing out of one account into another, where the entries
     * belong to the identity being left behind. Sending them afterwards would
     * file one student's work under another's name.
     */
    suspend fun clear() = mutex.withLock { store.write(emptyList()) }

    suspend fun markViewed(lessonId: String) =
        enqueue(PendingAction.MarkViewed(newId(), lessonId, now()))

    suspend fun markComplete(lessonId: String) =
        enqueue(PendingAction.MarkComplete(newId(), lessonId, now()))

    /**
     * Queues a sitting, with the key it must keep.
     *
     * The key is minted here — at the moment the student pressed submit — and
     * never again.
     */
    suspend fun submitQuiz(lessonId: String, answers: List<QuizAnswer>): PendingAction.SubmitQuiz {
        val action = PendingAction.SubmitQuiz(
            id = newId(),
            lessonId = lessonId,
            createdAtEpochSeconds = now(),
            answers = answers,
            idempotencyKey = newId(),
        )
        enqueue(action)
        return action
    }

    /**
     * Submits a sitting, keeping it safe either way.
     *
     * Queued **first**, then sent. A student who answers underground and closes
     * the app has, from their point of view, answered — and the order here is
     * what makes that true.
     *
     * Returns [QuizOutcome.Graded] only with the **server's** grade. There is
     * no offline branch that scores anything: §5 requires the app to say
     * 「回答をあずかりました。いま採点できないので、通信できたときに送ります」 rather than
     * invent a number, and [QuizOutcome.Held] is that sentence in a type.
     */
    suspend fun submitQuizNow(lessonId: String, answers: List<QuizAnswer>): QuizOutcome {
        val action = submitQuiz(lessonId, answers)

        return try {
            val result = content.submitQuiz(action.lessonId, action.answers, action.idempotencyKey)
            remove(action.id)
            QuizOutcome.Graded(result)
        } catch (e: ApiError) {
            if (e.isTransient) {
                // Left queued, with its key, to go later.
                QuizOutcome.Held
            } else {
                // It will fail identically for ever; keeping it would block the
                // queue. Dropped, and the student is told rather than left
                // believing it was recorded.
                remove(action.id)
                QuizOutcome.Rejected
            }
        }
    }

    /** Queues a test sitting, with the key it must keep. */
    suspend fun submitTest(
        testId: String,
        answers: List<TestAnswer>,
        elapsedSeconds: Int,
    ): PendingAction.SubmitTest {
        val action = PendingAction.SubmitTest(
            id = newId(),
            createdAtEpochSeconds = now(),
            testId = testId,
            answers = answers,
            elapsedSeconds = elapsedSeconds,
            idempotencyKey = newId(),
        )
        enqueue(action)
        return action
    }

    /** As [submitQuizNow]: queued first, then sent, and graded only by the server. */
    suspend fun submitTestNow(
        testId: String,
        answers: List<TestAnswer>,
        elapsedSeconds: Int,
    ): TestOutcome {
        val action = submitTest(testId, answers, elapsedSeconds)

        return try {
            val result = sendTest(action)
            remove(action.id)
            TestOutcome.Graded(result)
        } catch (e: ApiError) {
            if (e.isTransient) {
                TestOutcome.Held
            } else {
                remove(action.id)
                TestOutcome.Rejected
            }
        }
    }

    private suspend fun sendTest(action: PendingAction.SubmitTest) = content.submitTest(
        testId = action.testId,
        answers = action.answers,
        elapsedSeconds = action.elapsedSeconds,
        idempotencyKey = action.idempotencyKey,
    )

    private suspend fun remove(actionId: String) = mutex.withLock {
        store.write(store.read().filterNot { it.id == actionId })
    }

    /**
     * Records a stretch of study that has finished.
     *
     * Queued rather than sent directly, always. This is the record the 共通判断
     * 規則 opens with — 「電車で勉強した生徒の記録を、通信状況で失わせない」 — and it is
     * exactly the one most likely to be made with no signal.
     */
    suspend fun recordStudy(
        clientSessionId: String,
        startedAt: String,
        endedAt: String,
        durationSeconds: Int,
        subject: String? = null,
        courseId: String? = null,
        lessonId: String = "",
    ) = enqueue(
        PendingAction.RecordStudy(
            id = newId(),
            lessonId = lessonId,
            createdAtEpochSeconds = now(),
            clientSessionId = clientSessionId,
            startedAt = startedAt,
            endedAt = endedAt,
            durationSeconds = durationSeconds,
            subject = subject,
            courseId = courseId,
        ),
    )

    suspend fun rateLesson(
        lessonId: String,
        understood: Boolean,
        reasons: List<String> = emptyList(),
        comment: String? = null,
    ) = enqueue(
        PendingAction.RateLesson(
            id = newId(),
            lessonId = lessonId,
            createdAtEpochSeconds = now(),
            understood = understood,
            // §6: the API rejects anything longer with a 400 — which this queue
            // would classify as permanent and discard. The input field stops the
            // student first; this is the second line, because losing the tail of
            // a comment beats losing the whole verdict.
            reasons = if (understood) emptyList() else reasons,
            comment = comment?.take(MAX_COMMENT_LENGTH),
        ),
    )

    private suspend fun enqueue(action: PendingAction) = mutex.withLock {
        store.write(store.read().merging(action))
    }

    /**
     * Applies §5's three merge rules.
     *
     * Note that they are keyed on the **kind and the lesson**, not on the
     * lesson alone: a completion and a quiz submission for the same lesson are
     * different facts and must both survive.
     */
    private fun List<PendingAction>.merging(action: PendingAction): List<PendingAction> =
        when (action.mergeRule) {
            // Two presses, one fact. The earliest entry is kept so the queue
            // still replays in the order the student acted.
            MergeRule.Coalesce ->
                if (any { it.sameKindAndLesson(action) }) this else this + action

            // A later verdict is what the student means now.
            MergeRule.Replace -> filterNot { it.sameKindAndLesson(action) } + action

            // Two sittings are two sittings.
            MergeRule.Keep -> this + action
        }

    private fun PendingAction.sameKindAndLesson(other: PendingAction): Boolean =
        this::class == other::class && lessonId == other.lessonId

    /**
     * Sends everything it can, oldest first.
     *
     * Stops at the first temporary failure with the rest untouched, so order is
     * preserved across sessions. Permanent failures are dropped and the run
     * continues — one dead record must not hold up the ones behind it.
     */
    suspend fun flush(): FlushResult = mutex.withLock {
        val queued = store.read().sortedBy { it.createdAtEpochSeconds }

        var sent = 0
        var discarded = 0

        for ((index, action) in queued.withIndex()) {
            try {
                send(action)
                sent++
            } catch (e: ApiError) {
                if (e.isTransient) {
                    // Everything from here on, including this one, stays queued
                    // in order.
                    store.write(queued.drop(index))
                    return@withLock FlushResult(sent = sent, discarded = discarded, stoppedEarly = true)
                }
                discarded++
            }
        }

        store.write(emptyList())
        FlushResult(sent = sent, discarded = discarded, stoppedEarly = false)
    }

    private suspend fun send(action: PendingAction) = when (action) {
        is PendingAction.MarkViewed -> content.markViewed(action.lessonId)
        is PendingAction.MarkComplete -> {
            content.markComplete(action.lessonId)
            Unit
        }

        is PendingAction.SubmitQuiz -> {
            content.submitQuiz(action.lessonId, action.answers, action.idempotencyKey)
            Unit
        }

        is PendingAction.SubmitTest -> {
            sendTest(action)
            Unit
        }

        is PendingAction.RecordStudy -> {
            content.recordStudySession(
                com.wakaroute.core.content.RecordStudySession(
                    clientSessionId = action.clientSessionId,
                    startedAt = action.startedAt,
                    endedAt = action.endedAt,
                    durationSeconds = action.durationSeconds,
                    subject = action.subject,
                    courseId = action.courseId,
                    lessonId = action.lessonId.takeIf { it.isNotBlank() },
                ),
            )
            Unit
        }

        is PendingAction.RateLesson -> content.rateLesson(
            lessonId = action.lessonId,
            understood = action.understood,
            reasons = action.reasons,
            comment = action.comment,
        )
    }

    companion object {
        /** §6. Longer than this is a 400, which this queue would then discard. */
        const val MAX_COMMENT_LENGTH = 1000
    }
}

/**
 * What one flush achieved.
 *
 * [discarded] is counted rather than hidden: entries dropped for a permanent
 * failure are records a student made that will never arrive, and a number that
 * is quietly always zero is one nobody will notice going up.
 */
data class FlushResult(
    val sent: Int,
    val discarded: Int,
    /** True when a temporary failure stopped the run with entries still queued. */
    val stoppedEarly: Boolean,
)

/**
 * What happened to a sitting.
 *
 * Three outcomes, and the middle one is the reason this type exists: 「送れて
 * いない」 is not a failure the student caused and not a score the app may
 * guess at. §5: 「架空の点数を見せるより、採点できないと認めるほうがましです」.
 */
sealed interface QuizOutcome {
    /** The server graded it. The only way a score ever reaches the screen. */
    data class Graded(val result: com.wakaroute.core.content.QuizResult) : QuizOutcome

    /** Safely queued, not yet sent. No score exists to show. */
    data object Held : QuizOutcome

    /** The server refused it in a way that repeating cannot fix. */
    data object Rejected : QuizOutcome
}

/** What happened to a test sitting. The same three cases as [QuizOutcome]. */
sealed interface TestOutcome {
    data class Graded(val result: com.wakaroute.core.content.TestResult) : TestOutcome

    data object Held : TestOutcome

    data object Rejected : TestOutcome
}
