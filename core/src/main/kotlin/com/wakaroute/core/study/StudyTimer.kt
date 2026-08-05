package com.wakaroute.core.study

import com.wakaroute.core.offline.LearningActionQueue
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The study clock.
 *
 * Runs entirely on the device and posts the **finished** session, rather than
 * driving the server's start/stop endpoints. A timer that needs a connection to
 * begin is a timer that does not work on a train, which is the situation the
 * 共通判断規則 opens by describing.
 *
 * Nothing here is derived from wall-clock arithmetic the server would then
 * disagree with: the session is sent as two instants and a duration, and the
 * server decides which day it belongs to. A phone whose clock is wrong, or one
 * in another timezone, would otherwise put a study session on the wrong date
 * and quietly break a streak.
 */
class StudyTimer(
    private val queue: LearningActionQueue,
    private val now: () -> Instant = Instant::now,
    private val newSessionId: () -> String = { UUID.randomUUID().toString() },
) {
    private val _running = MutableStateFlow<RunningSession?>(null)

    /** The session in progress, or null. */
    val running: StateFlow<RunningSession?> = _running.asStateFlow()

    data class RunningSession(
        val clientSessionId: String,
        val startedAt: Instant,
        val subject: String?,
        val courseId: String?,
        val lessonId: String?,
    )

    /**
     * Starts the clock, replacing anything already running.
     *
     * One clock per student, matching the server's own rule: someone who
     * starts 数学 having left 英語 running an hour ago should not have both
     * counting. The abandoned session is **discarded rather than recorded** —
     * an hour they did not study is worse than a lost minute they did.
     */
    fun start(subject: String? = null, courseId: String? = null, lessonId: String? = null) {
        _running.value = RunningSession(
            clientSessionId = newSessionId(),
            startedAt = now(),
            subject = subject,
            courseId = courseId,
            lessonId = lessonId,
        )
    }

    /**
     * Stops the clock and queues the session.
     *
     * Returns the seconds recorded, or null when nothing was running or the
     * stretch was too short to mean anything.
     */
    suspend fun stop(): Int? {
        val session = _running.value ?: return null
        _running.value = null

        val endedAt = now()
        val seconds = (endedAt.epochSecond - session.startedAt.epochSecond).toInt()

        // A few seconds is a mis-tap, not study. Recording it would put a
        // meaningless entry on the calendar and — worse — count towards a
        // streak the student did not earn.
        if (seconds < MINIMUM_SECONDS) return null

        queue.recordStudy(
            clientSessionId = session.clientSessionId,
            startedAt = ISO.format(session.startedAt),
            endedAt = ISO.format(endedAt),
            durationSeconds = seconds,
            subject = session.subject,
            courseId = session.courseId,
            lessonId = session.lessonId.orEmpty(),
        )

        return seconds
    }

    /** Seconds on the clock right now, for the display. */
    fun elapsedSeconds(): Int {
        val session = _running.value ?: return 0
        return (now().epochSecond - session.startedAt.epochSecond).toInt().coerceAtLeast(0)
    }

    companion object {
        /** Below this a session is a mis-tap rather than study. */
        const val MINIMUM_SECONDS = 60

        private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT
    }
}
