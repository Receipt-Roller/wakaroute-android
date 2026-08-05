package com.wakaroute.app.feature.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.core.content.ContentClient
import com.wakaroute.core.content.Quiz
import com.wakaroute.core.content.QuizAnswer
import com.wakaroute.core.content.QuizQuestion
import com.wakaroute.core.net.ApiError
import com.wakaroute.core.offline.LearningActionQueue
import com.wakaroute.core.offline.QuizOutcome
import java.net.URLDecoder
import kotlinx.coroutines.launch

private sealed interface QuizScreenState {
    data object Loading : QuizScreenState
    data class Answering(val quiz: Quiz, val lessonTitle: String) : QuizScreenState
    data object Missing : QuizScreenState
    data class Failed(val message: String) : QuizScreenState
}

/**
 * 確認クイズ.
 *
 * The grade never comes from here. §5: the app must not compute a score, and it
 * cannot — the quiz arrives without its answer key. Submitting hands the answers
 * to the offline queue, which stores them **before** trying to send, so a
 * student who answers underground has genuinely answered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    content: ContentClient,
    queue: LearningActionQueue,
    lessonId: String,
    onBack: () -> Unit,
) {
    val id = remember(lessonId) { URLDecoder.decode(lessonId, "UTF-8") }
    var state by remember(id) { mutableStateOf<QuizScreenState>(QuizScreenState.Loading) }
    val selections = remember(id) { mutableStateMapOf<String, String>() }
    var outcome by remember(id) { mutableStateOf<QuizOutcome?>(null) }
    var submitting by remember(id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(id) {
        state = try {
            val lesson = content.lessonDetail(id)
            lesson.quiz
                ?.let { QuizScreenState.Answering(it, lesson.title) }
                ?: QuizScreenState.Missing
        } catch (e: ApiError) {
            QuizScreenState.Failed(e.studentFacingMessage())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("確認クイズ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            QuizScreenState.Loading -> Centered(padding) { CircularProgressIndicator() }

            QuizScreenState.Missing -> Centered(padding) {
                Text("このレッスンに確認クイズはありません。")
            }

            is QuizScreenState.Failed -> Centered(padding) {
                Text(current.message, style = MaterialTheme.typography.bodyMedium)
            }

            is QuizScreenState.Answering -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    ReadableColumn(spacing = 6.dp) {
                        Text(current.lessonTitle, style = MaterialTheme.typography.titleMedium)
                        current.quiz.instructions?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                items(current.quiz.questions.sortedBy { it.orderIndex }) { question ->
                    ReadableColumn(spacing = 8.dp) {
                        QuestionView(
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
                            answered = selections.size,
                            total = current.quiz.questions.size,
                            submitting = submitting,
                            onSubmit = {
                                scope.launch {
                                    submitting = true
                                    outcome = queue.submitQuizNow(
                                        lessonId = id,
                                        answers = current.quiz.questions.map { question ->
                                            QuizAnswer(
                                                questionId = question.id,
                                                optionId = selections[question.id],
                                            )
                                        },
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

@Composable
private fun QuestionView(
    question: QuizQuestion,
    selectedOptionId: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Column(
        // One group per question, so a screen reader announces 「4つのうち3つ目」
        // rather than reading twenty loose radio buttons.
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(question.questionText, style = MaterialTheme.typography.bodyLarge)

        for (option in question.options.sortedBy { it.orderIndex }) {
            val selected = option.id == selectedOptionId

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        // Role makes it a radio button to TalkBack rather than
                        // an unexplained tappable row.
                        role = Role.RadioButton,
                        onClick = { onSelect(option.id) },
                    )
                    .padding(vertical = 6.dp),
            ) {
                RadioButton(selected = selected, onClick = null, enabled = enabled)
                Text(
                    text = option.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Row(modifier: Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        content = { content() },
    )
}

@Composable
private fun SubmitButton(answered: Int, total: Int, submitting: Boolean, onSubmit: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
            onClick = onSubmit,
            // Every question, not just some. A partial submission is recorded
            // as a real attempt and counts against the student.
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

/**
 * What happened, in the student's terms.
 *
 * [QuizOutcome.Held] is the case this screen exists to get right. §5:
 * 「回答をあずかりました。いま採点できないので、通信できたときに送ります」 — no score,
 * no guess, and no suggestion that they did anything wrong.
 */
@Composable
private fun OutcomeView(outcome: QuizOutcome) {
    val (title, body) = when (outcome) {
        is QuizOutcome.Graded -> {
            val result = outcome.result
            val verdict = if (result.isPassed) "合格です" else "もう一度ためしましょう"
            verdict to "${result.scorePercent}点（合格は${result.passingScorePercent}点以上）"
        }

        QuizOutcome.Held ->
            "回答をあずかりました" to
                "いま採点できないので、通信できたときに送ります。答えは端末に保存してあります。"

        QuizOutcome.Rejected ->
            "この回答は記録できませんでした" to
                "レッスンが変更されたのかもしれません。もう一度レッスンを開いてためしてください。"
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

@Composable
private fun Centered(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().padding(padding).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun ApiError.studentFacingMessage(): String = when (this) {
    is ApiError.Offline -> "インターネットにつながっていないようです。"
    is ApiError.TimedOut -> "時間内に返事がありませんでした。"
    else -> "いま読み込めませんでした。しばらくしてから、もう一度ためしてください。"
}
