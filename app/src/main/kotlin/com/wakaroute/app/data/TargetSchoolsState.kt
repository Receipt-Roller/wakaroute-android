package com.wakaroute.app.data

import com.wakaroute.core.goals.TargetSchoolList
import com.wakaroute.core.goals.TargetSchoolsRepository
import com.wakaroute.core.goals.moved
import com.wakaroute.core.net.ApiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The 志望校 list, shared by the screens that read and write it.
 *
 * One holder rather than a view model per screen, because adding a school on
 * the detail screen has to be visible on the home screen without a reload. Two
 * independent copies would disagree, and the one showing the stale count is the
 * one a student is looking at.
 */
class TargetSchoolsState(
    private val repository: TargetSchoolsRepository,
    /**
     * Whether this device already has an account.
     *
     * A function rather than the [AuthSession] itself, so this holder — and the
     * screens that use it — can be tested without a Keystore. It is also the
     * whole of what this class needs from auth, and saying so keeps the two
     * from growing into each other.
     */
    private val hasAccount: () -> Boolean,
) {
    private val _state = MutableStateFlow<TargetSchoolsUi>(TargetSchoolsUi.NotRegistered)
    val state: StateFlow<TargetSchoolsUi> = _state.asStateFlow()

    /**
     * Loads the list, but **only if this device already has an account**.
     *
     * This guard is the reason the home screen can show a 志望校 section without
     * creating a MANABU2 learner for everyone who opens the app. Registration
     * happens on the first authenticated call, so an unconditional load here
     * would register every install — including the ones that only ever look at
     * the school catalogue.
     *
     * The 実装ガイド describes registering on first launch. This defers it to
     * the first action that actually needs an account, which keeps that
     * behaviour for anyone who uses the feature and skips it for everyone else.
     * Raised in AB rather than done quietly.
     */
    suspend fun refreshIfAccountExists() {
        if (!hasAccount()) {
            _state.value = TargetSchoolsUi.NotRegistered
            return
        }
        load()
    }

    suspend fun add(schoolId: String, name: String, examDate: String?) =
        mutate { repository.add(schoolId, name, examDate) }

    suspend fun remove(schoolId: String) = mutate { repository.remove(schoolId) }

    /**
     * Moves one 志望校, showing the new order **before** the server confirms it.
     *
     * Optimistic here and nowhere else. Reordering is a direct manipulation —
     * the student is pointing at a row and saying "up" — and a list that sits
     * still for a round trip reads as a dead button, so they tap again and now
     * two moves are in flight. Adding and removing are not like that: those are
     * one deliberate action with a clear before and after, and they can afford
     * to wait for the truth.
     *
     * On failure the previous order is restored and [Failed] is reported, so a
     * move that did not save never masquerades as one that did.
     */
    suspend fun move(from: Int, to: Int): Boolean {
        val current = (_state.value as? TargetSchoolsUi.Loaded) ?: return false
        val reordered = current.list.goals.moved(from, to)
        if (reordered == current.list.goals) return true

        // Ranks are the server's to assign, so the local copy renumbers them
        // too. Leaving them stale would make 第一志望 the second row until the
        // response landed.
        _state.value = TargetSchoolsUi.Loaded(
            current.list.copy(goals = reordered.mapIndexed { index, goal -> goal.copy(rank = index) }),
        )

        return try {
            _state.value = TargetSchoolsUi.Loaded(repository.reorder(reordered.map { it.externalId }))
            true
        } catch (e: ApiError) {
            _state.value = current
            false
        }
    }

    private suspend fun load() {
        _state.value = TargetSchoolsUi.Loading
        _state.value = try {
            TargetSchoolsUi.Loaded(repository.load())
        } catch (e: ApiError) {
            TargetSchoolsUi.Failed(e.studentFacingMessage(), e.isTransient)
        }
    }

    /**
     * Applies a change and keeps the result.
     *
     * On failure the previous list is restored rather than left half-applied —
     * a 志望校 that appears and then silently is not saved is worse than one that
     * never appeared.
     */
    private suspend fun mutate(block: suspend () -> TargetSchoolList): Boolean {
        val previous = _state.value

        return try {
            _state.value = TargetSchoolsUi.Loaded(block())
            true
        } catch (e: ApiError) {
            _state.value = previous
            false
        }
    }
}

sealed interface TargetSchoolsUi {
    /** No account yet, so there is nothing to fetch and nothing to create. */
    data object NotRegistered : TargetSchoolsUi

    data object Loading : TargetSchoolsUi

    data class Loaded(val list: TargetSchoolList) : TargetSchoolsUi

    data class Failed(val message: String, val canRetry: Boolean) : TargetSchoolsUi
}

private fun ApiError.studentFacingMessage(): String = when (this) {
    is ApiError.Offline -> "インターネットにつながっていないようです。"
    is ApiError.TimedOut -> "時間内に返事がありませんでした。"
    is ApiError.Decoding -> "結果を読み取れませんでした。アプリの更新があるか確認してください。"
    else -> "いま読み込めませんでした。しばらくしてから、もう一度ためしてください。"
}
