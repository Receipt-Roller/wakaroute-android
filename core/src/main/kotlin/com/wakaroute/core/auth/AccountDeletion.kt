package com.wakaroute.core.auth

import com.wakaroute.core.cards.CardProgressStore
import com.wakaroute.core.cards.InMemoryCardProgressStore
import com.wakaroute.core.journal.JournalOutbox
import com.wakaroute.core.net.ApiError
import com.wakaroute.core.offline.LearningActionQueue

/**
 * 学習記録の削除.
 *
 * Required by **App Store Review 5.1.1(v)** and Google Play's equivalent
 * data-deletion policy: an app that creates accounts must let the student
 * delete one from inside the app. ワカルート creates a learner account silently
 * on first launch, so this applies **even though there is no sign-up screen
 * anywhere** — which is exactly why it is easy to leave out until review
 * rejects the build.
 *
 * The order is the whole design: the server is asked first, and this device
 * forgets only after it confirms. Forgetting first would strand an account on
 * the server that the student can neither reach nor delete.
 */
class AccountDeletion(
    private val auth: AuthSession,
    private val queue: LearningActionQueue,
    /** Card progress lives only on the device, so only the device can forget it. */
    private val cardProgress: CardProgressStore = InMemoryCardProgressStore(),
    /** Unsent diary and time blocks — the same reason as the queue. */
    private val journal: JournalOutbox? = null,
) {
    sealed interface Result {
        /** Gone. The next launch starts a new, empty learner. */
        data object Deleted : Result

        /** Nothing was deleted, on the server or here. */
        data class Failed(val cause: ApiError) : Result
    }

    /**
     * Deletes the account, then everything this device is still holding for it.
     *
     * The queue goes too. Entries waiting to be sent are lesson completions,
     * quiz answers and study times belonging to someone who has just asked to
     * be forgotten; sending them afterwards would recreate the very record the
     * student deleted, under a new account.
     *
     * Card progress goes as well. It never reached the server, but the
     * confirmation tells the student everything is erased, and it is theirs.
     */
    suspend fun delete(): Result = try {
        auth.deleteAccount()

        // Only now is it safe to forget. Deliberately not in a finally: a
        // failed delete must leave the records intact, because the account
        // they belong to still exists.
        queue.clear()
        journal?.clear()
        cardProgress.clear()

        Result.Deleted
    } catch (e: ApiError) {
        Result.Failed(e)
    }
}
