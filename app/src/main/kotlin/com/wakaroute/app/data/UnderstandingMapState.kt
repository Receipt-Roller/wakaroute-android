package com.wakaroute.app.data

import com.wakaroute.core.map.LiveUnderstandingMap
import com.wakaroute.core.map.PrerequisiteGraphSync
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
 * layered on afterwards, and a failure to fetch it costs the record — never the
 * structure.
 */
class UnderstandingMapState(
    private val bundled: UnderstandingMapRepository,
    private val live: LiveUnderstandingMap,
    private val graphs: PrerequisiteGraphSync? = null,
) {
    private val _states = MutableStateFlow(bundled.allStates())

    /** Every 教科 in display order. Screens show only the ones with a map. */
    val states: StateFlow<List<Pair<SchoolSubject, SubjectMapState>>> = _states.asStateFlow()

    fun state(subject: SchoolSubject): SubjectMapState =
        _states.value.firstOrNull { it.first == subject }?.second ?: bundled.state(subject)

    /**
     * Fetches the published prerequisite graphs.
     *
     * **Not gated on having an account, unlike the record below.** The graphs
     * are content structure, not learner data, and they are fetched
     * unauthenticated — so this cannot create a MANABU2 learner the way an
     * authenticated call would. Gating it would mean a student who has never
     * registered never sees a 教科 go live.
     *
     * Failure is silent on purpose: no network, or an endpoint that does not
     * exist yet, both mean 「いま持っているものを使う」.
     */
    suspend fun refreshPublishedGraphs() {
        val sync = graphs ?: return
        sync.refresh()

        // Re-read: a 教科 whose edges have just arrived now has a map to show.
        _states.value = bundled.allStates()
    }

    /**
     * Replaces the bundled structure with the live one where it can.
     *
     * Per subject rather than all-or-nothing: 数学 succeeding should not wait on
     * anything, and a 教科 without a map costs no request at all.
     */
    suspend fun refreshProgress() {
        _states.value = _states.value.map { (subject, current) ->
            subject to if (current is SubjectMapState.Available) live.load(subject) else current
        }
    }
}
