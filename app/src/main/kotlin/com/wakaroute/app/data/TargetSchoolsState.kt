package com.wakaroute.app.data

import com.wakaroute.core.auth.AuthSession
import com.wakaroute.core.goals.TargetSchoolList
import com.wakaroute.core.goals.TargetSchoolsRepository
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
    private val auth: AuthSession,
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
    suspend fun refreshIfRegistered() {
        if (!auth.isRegistered()) {
            _state.value = TargetSchoolsUi.NotRegistered
            return
        }
        load()
    }

    suspend fun add(schoolId: String, name: String, examDate: String?) =
        mutate { repository.add(schoolId, name, examDate) }

    suspend fun remove(schoolId: String) = mutate { repository.remove(schoolId) }

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
