package com.wakaroute.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnderstandingMapRepositoryTest {

    private val repository = BundledUnderstandingMapRepository()

    @Test
    fun `math is available`() {
        val state = repository.state(SchoolSubject.Math)
        assertTrue(state is SubjectMapState.Available)
        assertEquals(26, (state as SubjectMapState.Available).subject.elements.size)
    }

    @Test
    fun `the other four subjects are coming soon, never an empty graph`() {
        // The single most dangerous shortcut available here. With no edges every
        // 要素 is Ready, and the screens would render that as
        // 「どの要素も学習できます／つまずきはありません」 — the one answer this map
        // must never give, about four subjects nobody has mapped yet.
        val others = SchoolSubject.entries - SchoolSubject.Math

        for (subject in others) {
            assertEquals(
                "${subject.label} has no authored edges and must be 準備中",
                SubjectMapState.ComingSoon,
                repository.state(subject),
            )
        }
    }

    @Test
    fun `every subject appears, including the ones with no content`() {
        // 準備中 rather than silently missing, as the サービス仕様 requires of
        // anything unbuilt.
        assertEquals(5, repository.allStates().size)
        assertEquals(SchoolSubject.entries, repository.allStates().map { it.first })
    }

    @Test
    fun `phase 1 reports an empty record rather than inventing progress`() {
        val state = repository.state(SchoolSubject.Math) as SubjectMapState.Available

        // Phase 1 talks to no MANABU2 endpoint. Anything other than empty here
        // would be progress the app made up.
        assertTrue(state.mastery.isEmpty)
        assertTrue(state.subject.elements.all { state.mastery[it.id] == MasteryLevel.NotStarted })
    }

    @Test
    fun `the graph carries the date it was authored`() {
        // Shown on the screen so the map is never mistaken for live data.
        val state = repository.state(SchoolSubject.Math) as SubjectMapState.Available
        assertTrue(state.asOf.isNotBlank())
    }
}
