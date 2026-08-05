package com.wakaroute.app.data

import com.wakaroute.core.map.LiveUnderstandingMap
import com.wakaroute.core.map.SchoolSubject
import com.wakaroute.core.map.SubjectMapState
import com.wakaroute.core.map.UnderstandingMapRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The 理解マップ, structure first and the student's record after.
 *
 * The bundled structure is available with no network and no account, so the
 * screen draws immediately and stays useful on a train. The learner's record is
 * layered on afterwards **only if this device already has an account** — the
 * same guard as 志望校, and for the same reason: the first authenticated call is
 * what creates a MANABU2 learner, and opening a map should not do that.
 */
class UnderstandingMapState(
    private val bundled: UnderstandingMapRepository,
    private val live: LiveUnderstandingMap,
    private val isRegistered: () -> Boolean,
) {
    private val _states = MutableStateFlow(bundled.allStates())

    /** Every 教科 in display order, so 準備中 ones still appear. */
    val states: StateFlow<List<Pair<SchoolSubject, SubjectMapState>>> = _states.asStateFlow()

    fun state(subject: SchoolSubject): SubjectMapState =
        _states.value.firstOrNull { it.first == subject }?.second ?: bundled.state(subject)

    /**
     * Replaces the bundled structure with the live one where it can.
     *
     * Per subject rather than all-or-nothing: 数学 succeeding should not wait on
     * anything, and the four 準備中 教科 cost no request at all.
     */
    suspend fun refreshIfRegistered() {
        if (!isRegistered()) return

        _states.value = _states.value.map { (subject, current) ->
            subject to if (current is SubjectMapState.Available) live.load(subject) else current
        }
    }
}
