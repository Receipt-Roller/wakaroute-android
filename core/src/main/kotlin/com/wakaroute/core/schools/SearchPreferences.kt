package com.wakaroute.core.schools

import kotlinx.coroutines.flow.Flow

/**
 * Where the last search conditions are kept.
 *
 * An interface in `core` rather than a direct dependency on the Android
 * `DataStore`, because the state machine that uses it — loading, success,
 * 0件, failure, retry — is exactly the part worth testing, and it should not
 * need a device to run.
 *
 * **Only the conditions are stored, never the results.** Caching a page would
 * mean tracking the catalogue's `asOf` against the time it was fetched, which
 * §7 requires and which Phase 1 has no screen to honour. A stale list of
 * schools is worse than a short wait.
 */
interface SearchPreferences {
    val lastSearch: Flow<SchoolSearchQuery>
    suspend fun saveSearch(query: SchoolSearchQuery)
    suspend fun clearSearch()
}
