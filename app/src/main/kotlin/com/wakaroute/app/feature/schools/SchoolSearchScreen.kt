package com.wakaroute.app.feature.schools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wakaroute.app.AppServices
import com.wakaroute.app.ui.design.AdaptiveRow
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.core.schools.Prefecture
import com.wakaroute.core.schools.School
import com.wakaroute.core.schools.SchoolOwnership

@Composable
fun SchoolSearchScreen(
    services: AppServices,
    onOpenSchool: (String) -> Unit,
) {
    val viewModel: SchoolSearchViewModel = viewModel(
        factory = SchoolSearchViewModel.Factory(services.schools, services.preferences),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    val listState = rememberLazyListState()

    // Loads the next page a little before the bottom, so scrolling does not
    // stop dead while a request is in flight.
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }

    androidx.compose.runtime.LaunchedEffect(shouldLoadMore, state) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchControls(
            keyword = query.keyword.orEmpty(),
            prefectureCode = query.prefectureCode,
            ownership = query.ownership,
            hasFilters = !query.isEmpty,
            onKeywordChange = viewModel::setKeyword,
            onPrefectureChange = viewModel::setPrefecture,
            onOwnershipChange = viewModel::setOwnership,
            onSearch = {
                keyboard?.hide()
                viewModel.search()
            },
            onClear = viewModel::clearFilters,
        )

        HorizontalDivider()

        when (val current = state) {
            SearchUiState.Idle, SearchUiState.Loading -> CenteredProgress()

            SearchUiState.Empty -> Message(
                title = "見つかりませんでした",
                body = "条件に合う高校はありませんでした。キーワードを短くするか、条件をへらしてためしてください。",
                actionLabel = if (!query.isEmpty) "条件をクリア" else null,
                onAction = viewModel::clearFilters,
            )

            is SearchUiState.Failed -> Message(
                title = "検索できませんでした",
                body = current.failure.message,
                actionLabel = if (current.failure.canRetry) "もう一度ためす" else null,
                onAction = viewModel::search,
            )

            is SearchUiState.Success -> Results(
                state = current,
                listState = listState,
                onOpenSchool = onOpenSchool,
            )
        }
    }
}

@Composable
private fun SearchControls(
    keyword: String,
    prefectureCode: String?,
    ownership: SchoolOwnership?,
    hasFilters: Boolean,
    onKeywordChange: (String) -> Unit,
    onPrefectureChange: (String?) -> Unit,
    onOwnershipChange: (SchoolOwnership?) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = keyword,
            onValueChange = onKeywordChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("学校名・地名") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (keyword.isNotEmpty()) {
                    IconButton(onClick = { onKeywordChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "キーワードを消す")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )

        // 設置区分. Horizontally scrollable rather than wrapped, so the row keeps
        // one predictable height as text size changes.
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (option in SchoolOwnership.entries) {
                FilterChip(
                    selected = ownership == option,
                    onClick = {
                        onOwnershipChange(if (ownership == option) null else option)
                        onSearch()
                    },
                    label = { Text(option.label) },
                )
            }
        }

        // 都道府県. Codes are the key — 「東京」 and 「東京都」 are the same place,
        // and matching on the name would make that a bug.
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (prefecture in Prefecture.all) {
                FilterChip(
                    selected = prefectureCode == prefecture.code,
                    onClick = {
                        onPrefectureChange(
                            if (prefectureCode == prefecture.code) null else prefecture.code,
                        )
                        onSearch()
                    },
                    label = { Text(prefecture.name) },
                )
            }
        }

        AdaptiveRow(modifier = Modifier.fillMaxWidth()) { _ ->
            Button(onClick = onSearch) { Text("検索") }

            // Always offered while anything is set. A restored condition the
            // student cannot see how to remove reads as a broken app.
            if (hasFilters) {
                TextButton(onClick = onClear) { Text("条件をクリア") }
            }
        }
    }
}

@Composable
private fun Results(
    state: SearchUiState.Success,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpenSchool: (String) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    text = "${state.totalCount}件",
                    style = MaterialTheme.typography.titleSmall,
                )
                // The catalogue's own currency, not when we fetched it. §7 keeps
                // the two apart so a list is never mistaken for live data.
                state.asOf?.let {
                    Text(
                        text = "学校データ基準日: $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(state.schools, key = { it.id }) { school ->
            SchoolRow(school) { onOpenSchool(school.id) }
            HorizontalDivider()
        }

        if (state.isLoadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun SchoolRow(school: School, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = school.name, style = MaterialTheme.typography.titleMedium)

        Text(
            text = listOfNotNull(
                school.prefecture,
                school.ownershipDisplay,
                school.campusTypeLabel,
            ).joinToString("　"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun Message(
    title: String,
    body: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        ReadableColumn(spacing = 12.dp) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
