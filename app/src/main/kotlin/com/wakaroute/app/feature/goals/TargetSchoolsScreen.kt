package com.wakaroute.app.feature.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakaroute.app.data.TargetSchoolsState
import com.wakaroute.app.data.TargetSchoolsUi
import com.wakaroute.app.ui.design.AdaptiveRow
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.core.goals.TargetSchool
import kotlinx.coroutines.launch

/**
 * 志望校 の並べ替えと取り消し。
 *
 * **Buttons, not drag-and-drop.** A drag handle is the obvious choice and the
 * wrong one here: §8 requires that TalkBack complete the main operations, and a
 * drag gesture cannot be performed by a screen reader user at all. Two arrows
 * per row work with a screen reader, with a switch, and with a student holding
 * the phone one-handed on a train — which is most of them.
 *
 * The order **is** the ranking: 第一志望 is simply the top row. Nothing here asks
 * a student to type a number.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetSchoolsScreen(
    targetSchools: TargetSchoolsState,
    onFindSchools: () -> Unit,
    onBack: () -> Unit,
) {
    val state by targetSchools.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { targetSchools.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("志望校") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                TargetSchoolsUi.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                is TargetSchoolsUi.Failed -> Message(
                    body = "志望校を読み込めませんでした。${current.message}",
                    actionLabel = if (current.canRetry) "もう一度ためす" else null,
                    onAction = { scope.launch { targetSchools.refresh() } },
                )

                is TargetSchoolsUi.Loaded -> if (current.list.isEmpty) {
                    Message(
                        body = "まだ志望校を登録していません。",
                        actionLabel = "高校を探す",
                        onAction = onFindSchools,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        ReadableColumn(spacing = 0.dp) {
                            Text(
                                text = "上にあるものが第一志望です。矢印で順番を入れ替えられます。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 16.dp),
                            )

                            current.list.goals.forEachIndexed { index, goal ->
                                GoalRow(
                                    goal = goal,
                                    index = index,
                                    total = current.list.goals.size,
                                    enabled = !working,
                                    onMove = { to ->
                                        scope.launch {
                                            working = true
                                            failed = !targetSchools.move(index, to)
                                            working = false
                                        }
                                    },
                                    onRemove = {
                                        scope.launch {
                                            working = true
                                            failed = !targetSchools.remove(goal.externalId)
                                            working = false
                                        }
                                    },
                                )
                                HorizontalDivider()
                            }

                            current.list.daysRemaining?.let { days ->
                                Text(
                                    text = if (days >= 0) {
                                        "いちばん早い入試まであと $days 日（${current.list.bindingDeadline}）"
                                    } else {
                                        "いちばん早い入試日（${current.list.bindingDeadline}）は過ぎています"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 16.dp),
                                )
                            }

                            if (failed) {
                                Text(
                                    text = "保存できませんでした。もとの順番に戻しています。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalRow(
    goal: TargetSchool,
    index: Int,
    total: Int,
    enabled: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    AdaptiveRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) { flexible ->
        Column(
            modifier = flexible.semantics(mergeDescendants = true) {
                // Position spoken as words. 「1/3」 read aloud is not obviously a
                // position, and 第一志望 is the thing that actually matters.
                contentDescription = if (index == 0) {
                    "第一志望、${goal.name}"
                } else {
                    "${index + 1}番目、${goal.name}"
                }
            },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (index == 0) {
                Text(
                    text = "第一志望",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(text = goal.name, style = MaterialTheme.typography.bodyLarge)
            goal.targetDate?.let {
                Text(
                    text = "入試日 $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row {
            IconButton(
                onClick = { onMove(index - 1) },
                // Disabled at the ends rather than hidden. A control that
                // disappears shifts the two beside it, and the next tap lands
                // on the wrong one.
                enabled = enabled && index > 0,
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "${goal.name}を上へ")
            }

            IconButton(
                onClick = { onMove(index + 1) },
                enabled = enabled && index < total - 1,
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "${goal.name}を下へ")
            }

            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(Icons.Filled.Close, contentDescription = "${goal.name}を志望校から外す")
            }
        }
    }
}

@Composable
private fun Message(body: String, actionLabel: String?, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        ReadableColumn(spacing = 12.dp) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
