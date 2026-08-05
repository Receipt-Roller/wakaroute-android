package com.wakaroute.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wakaroute.core.schools.SchoolOwnership
import com.wakaroute.core.schools.SearchPreferences
import com.wakaroute.core.schools.SchoolSearchQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Small, non-secret settings.
 *
 * Plain `DataStore` is correct **here and only here**. Nothing in this file is a
 * credential: a search keyword and a "has read the intro" flag are not worth
 * protecting, and treating them as though they were would blur the line that
 * matters when Phase 2 adds a `deviceSecret`. That one goes to the Keystore, and
 * `AGENTS.md` says so where someone adding a field will read it.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wakaroute_settings")

class AppPreferences(private val context: Context) : SearchPreferences {

    /** Whether the intro has been shown. Shown once, and skippable. */
    val hasSeenIntroduction: Flow<Boolean> =
        context.dataStore.data.map { it[HAS_SEEN_INTRODUCTION] ?: false }

    suspend fun markIntroductionSeen() {
        context.dataStore.edit { it[HAS_SEEN_INTRODUCTION] = true }
    }

    /**
     * The last search the student ran.
     *
     * Only the **conditions** are kept, never the results. Caching a page would
     * mean tracking `asOf` against the time it was fetched, which §7 requires
     * and Phase 1 has no screen to honour — and a stale list of schools is worse
     * than a short wait.
     */
    override val lastSearch: Flow<SchoolSearchQuery> = context.dataStore.data.map { preferences ->
        SchoolSearchQuery(
            keyword = preferences[SEARCH_KEYWORD]?.takeIf { it.isNotBlank() },
            prefectureCode = preferences[SEARCH_PREFECTURE]?.takeIf { it.isNotBlank() },
            ownership = SchoolOwnership.fromWire(preferences[SEARCH_OWNERSHIP]),
        )
    }

    override suspend fun saveSearch(query: SchoolSearchQuery) {
        context.dataStore.edit { preferences ->
            preferences.putOrRemove(SEARCH_KEYWORD, query.keyword?.trim())
            preferences.putOrRemove(SEARCH_PREFECTURE, query.prefectureCode)
            preferences.putOrRemove(SEARCH_OWNERSHIP, query.ownership?.wire)
        }
    }

    override suspend fun clearSearch() {
        context.dataStore.edit { preferences ->
            preferences.remove(SEARCH_KEYWORD)
            preferences.remove(SEARCH_PREFECTURE)
            preferences.remove(SEARCH_OWNERSHIP)
        }
    }

    /**
     * Removes rather than stores blank.
     *
     * An empty string round-trips as a filter of `q=`, which is a different
     * request from sending no `q` at all — and only one of them means "the
     * student cleared the box".
     */
    private fun androidx.datastore.preferences.core.MutablePreferences.putOrRemove(
        key: Preferences.Key<String>,
        value: String?,
    ) {
        if (value.isNullOrBlank()) remove(key) else set(key, value)
    }

    private companion object {
        val HAS_SEEN_INTRODUCTION = booleanPreferencesKey("has_seen_introduction")
        val SEARCH_KEYWORD = stringPreferencesKey("search_keyword")
        val SEARCH_PREFECTURE = stringPreferencesKey("search_prefecture")
        val SEARCH_OWNERSHIP = stringPreferencesKey("search_ownership")
    }
}
