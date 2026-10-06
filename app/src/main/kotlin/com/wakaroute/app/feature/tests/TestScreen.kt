package com.wakaroute.app.feature.tests

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wakaroute.app.ui.design.Centered
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.app.ui.design.studentFacingMessage
import com.wakaroute.core.content.ContentClient
import com.wakaroute.core.content.TestAnswer
import com.wakaroute.core.content.TestDetail
import com.wakaroute.core.content.TestQuestion
import com.wakaroute.core.content.TestResult
import com.wakaroute.core.documents.MathNotation
import com.wakaroute.core.net.ApiError
import com.wakaroute.core.offline.LearningActionQueue
import com.wakaroute.core.offline.TestOutcome
import java.net.URLDecoder
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface TestScreenState {
    data object Loading : TestScreenState
    data class Ready(val test: TestDetail) : TestScreenState
    data class Failed(val message: String) : TestScreenState
}

/**
 * One test, from 「はじめる」 to the server's grade.
 *
 * Grading follows the 確認クイズ exactly: the answers go to the offline queue
 * first, with a key minted at submit, and a score only ever comes from the
 * server.
 *
 * **The time limit does not stop the student.** The server records whether a
 * sitting was within it, and 「時間内に安定する」 is a separate level for that
 * reason. Cutting a student off mid-answer would turn 「遅かった」 into
 * 「できなかった」, which is not what happened.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestScreen(
    content: ContentClient,
    queue: LearningActionQueue,
    testId: String,
    onBack: () -> Unit,
) {
    val id = remember(testId) { URLDecoder.decode(testId, "UTF-8") }
    var state by remember(id) { mutableStateOf<TestScreenState>(TestScreenState.Loading) }

    // Saved across process death: a 50-minute test lost to the system
    // reclaiming memory while the student checked a message is not recoverable
    // any other way. elapsedRealtime keeps counting through it.
    var startedAt by rememberSaveable(id) { mutableStateOf<Long?>(null) }
    val selections = rememberSaveable(id, saver = SelectionsSaver) { mutableStateMapOf() }

    var outcome by remember(id) { mutableStateOf<TestOutcome?>(null) }
    var submitting by remember(id) { mutableStateOf(false) }
    var confirmingLeave by remember(id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val inProgress = startedAt != null && outcome == null
    val leave = { if (inProgress) confirmingLeave = true else onBack() }
    BackHandler(enabled = inProgress) { confirmingLeave = true }

    LaunchedEffect(id) {
        state = try {
            TestScreenState.Ready(content.testDetail(id))
        } catch (e: ApiError) {
            TestScreenState.Failed(e.studentFacingMessage())
        }
    }

    if (confirmingLeave) {
        AlertDialog(
            onDismissRequest = { confirmingLeave = false },
            title = { Text("テストをやめますか？") },
            text = { Text("ここまでの答えは送られず、記録にも残りません。") },
            confirmButton = {
                TextButton(onClick = { confirmingLeave = false; onBack() }) { Text("やめる") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingLeave = false }) { Text("続ける") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("スタート診断") },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            TestScreenState.Loading -> Centered(padding) { CircularProgressIndicator() }

            is TestScreenState.Failed -> Centered(padding) {
                Text(current.message, style = MaterialTheme.typography.bodyMedium)
            }

            is TestScreenState.Ready -> {
                val test = current.test
                val questions = remember(test) { test.questions.sortedBy { it.orderIndex } }
                val started = startedAt

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item {
                        ReadableColumn(spacing = 8.dp) {
                            Introduction(test, questions.size)
                            if (started == null) {
                                Button(
                                    onClick = { startedAt = SystemClock.elapsedRealtime() },
                                    enabled = questions.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("はじめる") }
                            } else if (outcome == null) {
                                Clock(startedAt = started, timeLimitSeconds = test.timeLimitSeconds)
                            }
                        }
                    }

                    if (started != null) {
                        items(questions) { question ->
                            ReadableColumn(spacing = 8.dp) {
                                QuestionView(
                                    number = questions.indexOf(question) + 1,
                                    question = question,
                                    selectedOptionId = selections[question.id],
                                    enabled = outcome == null && !submitting,
                                    onSelect = { selections[question.id] = it },
                                )
                            }
                        }

                        item {
                            ReadableColumn(spacing = 12.dp) {
                                outcome?.let { OutcomeView(it) } ?: SubmitButton(
                                    answered = questions.count { it.id in selections },
                                    total = questions.size,
                                    submitting = submitting,
                                    onSubmit = {
                                        scope.launch {
                                            submitting = true
                                            outcome = queue.submitTestNow(
                                                testId = id,
                                                answers = questions.map { TestAnswer(it.id, selections.getValue(it.id)) },
                                                elapsedSeconds = elapsedSeconds(started),
                                            )
                                            submitting = false
                                        }
                                    },
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
private fun Introduction(test: TestDetail, questionCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(test.title, style = MaterialTheme.typography.titleLarge)
        test.description?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = "${questionCount}問・${timeLimitLabel(test.timeLimitSeconds)}・" +
                "合格ライン ${test.passingScorePercent}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Remaining time when there is a limit, elapsed time when there is not.
 *
 * Ticks once a second. Announced to TalkBack only when it crosses the limit —
 * a live region that changed every second would talk over every question.
 */
@Composable
private fun Clock(startedAt: Long, timeLimitSeconds: Int?) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }

    val elapsed = ((now - startedAt) / 1000).toInt()

    if (timeLimitSeconds == null) {
        Text(
            text = "経過 ${minutesAndSeconds(elapsed)}",
            style = MaterialTheme.typography.titleMedium,
        )
        return
    }

    val remaining = timeLimitSeconds - elapsed
    if (remaining > 0) {
        Text(
            text = "残り ${minutesAndSeconds(remaining)}",
            style = MaterialTheme.typography.titleMedium,
        )
    } else {
        Text(
            text = "時間をすぎました。このまま最後まで答えられますが、時間内の記録にはなりません。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun QuestionView(
    number: Int,
    question: TestQuestion,
    selectedOptionId: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "問$number　" + MathNotation.toReadableText(question.questionText, dollarDelimited = true),
            style = MaterialTheme.typography.bodyLarge,
        )

        for (option in question.options.sortedBy { it.orderIndex }) {
            val selected = option.id == selectedOptionId

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(option.id) },
                    )
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = null, enabled = enabled)
                Text(
                    text = MathNotation.toReadableText(option.optionText, dollarDelimited = true),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SubmitButton(answered: Int, total: Int, submitting: Boolean, onSubmit: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
            onClick = onSubmit,
            // Every question. The server records a partial — even an empty —
            // submission as a real attempt, scored against the whole test.
            enabled = answered == total && !submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (submitting) "送っています…" else "答え合わせをする")
        }

        if (answered < total) {
            Text(
                text = "$total 問のうち $answered 問に答えました。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OutcomeView(outcome: TestOutcome) {
    val (title, body) = when (outcome) {
        is TestOutcome.Graded -> outcome.result.let { it.verdict() to it.details() }

        TestOutcome.Held ->
            "回答をあずかりました" to
                "いま採点できないので、通信できたときに送ります。答えは端末に保存してあります。"

        TestOutcome.Rejected ->
            "この回答は記録できませんでした" to
                "テストが変更されたのかもしれません。もう一度テストを開いてためしてください。"
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun TestResult.verdict(): String = when {
    passedButOverTime -> "合格ラインです（時間はこえました）"
    isPassed -> "合格ラインです"
    else -> "戻るところが見つかりました"
}

private fun TestResult.details(): String = buildString {
    if (correctCount != null && totalQuestions != null) append("${totalQuestions}問中${correctCount}問正解。")
    append("${scorePercent}%（合格ラインは${passingScorePercent}%）")
}

private fun elapsedSeconds(startedAt: Long): Int =
    ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt().coerceAtLeast(1)

private fun minutesAndSeconds(totalSeconds: Int): String =
    String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)

/** HashMap is Bundle-safe; SnapshotStateMap is not. */
private val SelectionsSaver = Saver<SnapshotStateMap<String, String>, HashMap<String, String>>(
    save = { HashMap(it) },
    restore = { saved -> mutableStateMapOf<String, String>().apply { putAll(saved) } },
)
