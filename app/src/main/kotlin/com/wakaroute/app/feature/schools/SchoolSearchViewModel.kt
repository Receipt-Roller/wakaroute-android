package com.wakaroute.app.feature.schools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wakaroute.core.net.ApiError
import com.wakaroute.core.schools.School
import com.wakaroute.core.schools.SchoolOwnership
import com.wakaroute.core.schools.SchoolSearchQuery
import com.wakaroute.core.schools.SchoolsRepository
import com.wakaroute.core.schools.SearchPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Why a search failed, in the terms a student can act on.
 *
 * §7 requires 読み込み中・成功・0件・失敗・再試行 to be explicit states rather than
 * implied by a null. This enum is the 失敗 half of that: 「電波がない」 and
 * 「サーバーが調子悪い」 lead to different next steps, and collapsing them into one
 * 「エラーが発生しました」 tells a student nothing they can use.
 */
enum class SearchFailure(val message: String, val canRetry: Boolean) {
    Offline("インターネットにつながっていないようです。電波のあるところで、もう一度ためしてください。", true),
    TimedOut("時間内に返事がありませんでした。もう一度ためしてください。", true),
    Server("いまサーバーが混みあっているようです。しばらくしてから、もう一度ためしてください。", true),

    /**
     * We asked for something the server would not accept. Retrying the same
     * request cannot help, so no retry button is offered.
     */
    Rejected("うまく検索できませんでした。条件を変えてためしてください。", false),

    /** A response arrived that we could not read. Never a crash — §6. */
    Unreadable("結果を読み取れませんでした。アプリの更新があるか確認してください。", false),
}

sealed interface SearchUiState {
    /** Before the first search. */
    data object Idle : SearchUiState

    data object Loading : SearchUiState

    /** Distinct from [Success] with no items, so the screen can say 0件 plainly. */
    data object Empty : SearchUiState

    data class Success(
        val schools: List<School>,
        val totalCount: Int,
        val asOf: String?,
        val canLoadMore: Boolean,
        val isLoadingMore: Boolean = false,
    ) : SearchUiState

    data class Failed(val failure: SearchFailure) : SearchUiState
}

class SchoolSearchViewModel(
    private val schools: SchoolsRepository,
    private val preferences: SearchPreferences,
) : ViewModel() {

    private val _query = MutableStateFlow(SchoolSearchQuery())
    val query: StateFlow<SchoolSearchQuery> = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    /** Cancelled before each new search, so a slow first page cannot land after a later one. */
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            // Conditions only, never a cached result list. Restoring them
            // silently would leave a student staring at 0件 with no idea a
            // filter was applied, so the screen always shows what is set.
            _query.value = preferences.lastSearch.first()
            search()
        }
    }

    fun setKeyword(keyword: String) = _query.update { it.copy(keyword = keyword) }

    fun setPrefecture(code: String?) = _query.update { it.copy(prefectureCode = code) }

    fun setOwnership(ownership: SchoolOwnership?) = _query.update { it.copy(ownership = ownership) }

    fun clearFilters() {
        _query.value = SchoolSearchQuery()
        viewModelScope.launch {
            preferences.clearSearch()
            search()
        }
    }

    fun search() {
        val query = _query.value.copy(page = 1)
        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            _state.value = SearchUiState.Loading
            preferences.saveSearch(query)

            _state.value = try {
                val page = schools.search(query)
                if (page.items.isEmpty()) {
                    SearchUiState.Empty
                } else {
                    SearchUiState.Success(
                        schools = page.items,
                        totalCount = page.totalCount,
                        asOf = page.asOf,
                        canLoadMore = page.hasMorePages,
                    )
                }
            } catch (e: ApiError) {
                SearchUiState.Failed(e.asSearchFailure())
            }
        }
    }

    /**
     * Appends the next page.
     *
     * A failure here leaves the results already on screen alone and only stops
     * the spinner. Replacing a working list with an error because page 3 timed
     * out would take away what the student already had.
     */
    fun loadMore() {
        val current = _state.value as? SearchUiState.Success ?: return
        if (!current.canLoadMore || current.isLoadingMore) return

        val nextPage = (current.schools.size / _query.value.normalizedPageSize) + 1

        viewModelScope.launch {
            _state.value = current.copy(isLoadingMore = true)

            _state.value = try {
                val page = schools.search(_query.value.copy(page = nextPage))
                current.copy(
                    schools = current.schools + page.items,
                    canLoadMore = page.hasMorePages,
                    isLoadingMore = false,
                )
            } catch (e: ApiError) {
                current.copy(isLoadingMore = false, canLoadMore = false)
            }
        }
    }

    private fun ApiError.asSearchFailure(): SearchFailure = when (this) {
        is ApiError.Offline -> SearchFailure.Offline
        is ApiError.TimedOut -> SearchFailure.TimedOut
        is ApiError.Decoding -> SearchFailure.Unreadable
        is ApiError.Unknown -> SearchFailure.Server
        is ApiError.Http -> when {
            status >= 500 || status == 429 -> SearchFailure.Server
            else -> SearchFailure.Rejected
        }
    }

    class Factory(
        private val schools: SchoolsRepository,
        private val preferences: SearchPreferences,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SchoolSearchViewModel(schools, preferences) as T
    }
}
