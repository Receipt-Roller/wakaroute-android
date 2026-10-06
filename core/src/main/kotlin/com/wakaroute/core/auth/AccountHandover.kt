package com.wakaroute.core.auth

import com.wakaroute.core.journal.JournalOutbox
import com.wakaroute.core.offline.LearningActionQueue

/**
 * Moving a learning record onto a new phone.
 *
 * Two operations that look symmetrical and are not:
 *
 * - **Linking adds a way into the account this device already has.** The user
 *   id does not change, so nothing moves and nothing can be lost. Safe at any
 *   time.
 * - **Signing in replaces the account this install was using.** The anonymous
 *   learner it registered as becomes unreachable — and so does anything still
 *   sitting in the offline queue, which would then belong to an account the
 *   student can no longer reach.
 *
 * The second is guarded here rather than left to a screen, because the failure
 * is silent: the student signs in, sees their old progress, and never learns
 * that yesterday's quiz went nowhere.
 */
class AccountHandover(
    private val auth: AuthSession,
    private val queue: LearningActionQueue,
    /** Unsent diary and time blocks belong to the account being left, like the queue. */
    private val journal: JournalOutbox? = null,
) {
    /**
     * Why a sign-in was refused.
     *
     * [pendingRecords] is reported rather than hidden so the student can be
     * told **what** is at stake before being asked to accept losing it.
     */
    data class UnsentWork(val pendingRecords: Int)

    sealed interface SignInResult {
        data class SignedIn(val userId: String?) : SignInResult

        /** Nothing was changed. The student still holds their local account. */
        data class Refused(val unsent: UnsentWork) : SignInResult
    }

    suspend fun link(email: String, password: String, displayName: String = "") =
        auth.linkAccount(email = email, password = password, displayName = displayName)

    /**
     * Signs in, after making sure this device is not carrying work that would
     * be stranded.
     *
     * An upload is attempted first. If anything still will not send, the
     * sign-in is **refused** and the caller is told how much is at stake —
     * rather than discarding a student's study time on their behalf.
     *
     * [discardingUnsentWork] proceeds anyway, for when they have seen the
     * number and chosen.
     */
    suspend fun signIn(
        email: String,
        password: String,
        discardingUnsentWork: Boolean = false,
    ): SignInResult {
        if (!discardingUnsentWork) {
            runCatching { queue.flush() }
            runCatching { journal?.flush() }

            val stillPending = queue.pending().size + (journal?.pending()?.size ?: 0)
            if (stillPending > 0) return SignInResult.Refused(UnsentWork(stillPending))
        }

        val session = auth.signIn(email = email, password = password)

        // Whatever is left belongs to the account this install just left.
        // Sending it now would file one student's work under another's name.
        queue.clear()
        journal?.clear()

        return SignInResult.SignedIn(session.userId)
    }
}
