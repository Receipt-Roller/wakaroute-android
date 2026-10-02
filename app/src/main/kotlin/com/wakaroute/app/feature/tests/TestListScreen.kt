package com.wakaroute.app.feature.tests

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wakaroute.app.ui.design.AdaptiveRow
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.core.content.ContentClient
import com.wakaroute.core.content.TestSummary
import com.wakaroute.core.net.ApiError

private sealed interface TestListState {
    data object Loading : TestListState
    data class Loaded(val tests: List<TestSummary>) : TestListState
    data class Failed(val message: String) : TestListState
}

/**
 * Every test the student can sit.
 *
 * Tests attached to a 領域 (the スタート診断) come first, in the order the
 * server lists them, which already keeps each 教科's four together.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestListScreen(
    content: ContentClient,
    onOpenTest: (String) -> Unit,
    onBack: () -> Unit,
) {
    var state by remember { mutableStateOf<TestListState>(TestListState.Loading) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        state = TestListState.Loading
        state = try {
            TestListState.Loaded(content.tests().sortedBy { it.pathId == null })
        } catch (e: ApiError) {
            TestListState.Failed(e.studentFacingMessage())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("スタート診断") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            TestListState.Loading -> Centered(padding) { CircularProgressIndicator() }

            is TestListState.Failed -> Centered(padding) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(current.message, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { attempt++ }) { Text("もう一度読み込む") }
                }
            }

            is TestListState.Loaded -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            ) {
                item {
                    ReadableColumn(spacing = 8.dp) {
                        Text(
                            text = "まちがいが増え始めたところが、いま戻るべきところです。" +
                                "結果は理解マップのレベルには反映しません。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }

                if (current.tests.isEmpty()) {
                    item {
                        ReadableColumn(spacing = 0.dp) {
                            Text("いま受けられるテストはありません。")
                        }
                    }
                }

                items(current.tests) { test ->
                    ReadableColumn(spacing = 0.dp) {
                        TestRow(test, onClick = { onOpenTest(test.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun TestRow(test: TestSummary, onClick: () -> Unit) {
    AdaptiveRow(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 14.dp),
    ) { flexible ->
        Column(modifier = flexible, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = test.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = buildString {
                    append("${test.questionCount}問・${timeLimitLabel(test.timeLimitSeconds)}")
                    test.latestResult?.let { append("　前回 ${it.scorePercent}%") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun timeLimitLabel(timeLimitSeconds: Int?): String =
    if (timeLimitSeconds == null) "時間制限なし" else "${timeLimitSeconds / 60}分"

@Composable
internal fun Centered(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().padding(padding).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

internal fun ApiError.studentFacingMessage(): String = when (this) {
    is ApiError.Offline -> "インターネットにつながっていないようです。"
    is ApiError.TimedOut -> "時間内に返事がありませんでした。"
    else -> "いま読み込めませんでした。しばらくしてから、もう一度ためしてください。"
}
