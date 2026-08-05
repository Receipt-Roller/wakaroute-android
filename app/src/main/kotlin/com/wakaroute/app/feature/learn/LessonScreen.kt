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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.wakaroute.app.ui.design.DocumentBlockView
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.core.content.ContentClient
import com.wakaroute.core.content.LessonDetail
import com.wakaroute.core.documents.DocumentBlock
import com.wakaroute.core.documents.DocumentParser
import com.wakaroute.core.net.ApiError
import com.wakaroute.core.offline.LearningActionQueue
import java.net.URLDecoder
import kotlinx.coroutines.launch

private sealed interface LessonState {
    data object Loading : LessonState
    data class Loaded(val lesson: LessonDetail, val blocks: List<DocumentBlock>) : LessonState
    data class Failed(val message: String, val canRetry: Boolean) : LessonState
}

/**
 * One lesson.
 *
 * The body is parsed into blocks and drawn with the app's own typography — no
 * WebView (§3), so it follows the student's text size and TalkBack reads it as
 * ordinary content. Tables, numbered steps, collapsible 確認 questions and the
 * SVG diagrams all come through the shared renderer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    content: ContentClient,
    queue: LearningActionQueue,
    lessonId: String,
    onOpenQuiz: (String) -> Unit,
    onBack: () -> Unit,
) {
    val id = remember(lessonId) { URLDecoder.decode(lessonId, "UTF-8") }
    var state by remember(id) { mutableStateOf<LessonState>(LessonState.Loading) }
    var completed by remember(id) { mutableStateOf(false) }
    var completing by remember(id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(id) {
        state = try {
            val lesson = content.lessonDetail(id)
            LessonState.Loaded(lesson, DocumentParser.parse(lesson.bodyHtml.orEmpty()))
        } catch (e: ApiError) {
            LessonState.Failed(e.studentFacingMessage(), e.isTransient)
        }

        // Through the queue, so a lesson read underground is still recorded as
        // read. Coalesced there — opening twice is one fact.
        runCatching {
            queue.markViewed(id)
            queue.flush()
        }
    }

    val loaded = state as? LessonState.Loaded

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(loaded?.lesson?.title ?: "レッスン") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            LessonState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is LessonState.Failed -> Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                ReadableColumn(spacing = 12.dp) {
                    Text("レッスンを読み込めませんでした", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (current.canRetry) {
                        Button(onClick = { state = LessonState.Loading }) { Text("もう一度ためす") }
                    }
                }
            }

            is LessonState.Loaded -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                current.lesson.summary?.let { summary ->
                    item {
                        ReadableColumn {
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                items(current.blocks) { block ->
                    ReadableColumn { DocumentBlockView(block) }
                }

                if (current.lesson.quiz != null) {
                    item {
                        ReadableColumn {
                            // Offered, not forced. The quiz is how a 要素 gets
                            // past 意味がわかる, but a student who wants to read
                            // the next lesson first is not doing it wrong.
                            Button(
                                onClick = { onOpenQuiz(id) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("確認クイズにすすむ")
                            }
                        }
                    }
                }

                item {
                    // After the body and beside the completion button, never in
                    // front of either. §6: it must not interrupt.
                    ReadableColumn { LessonRatingCard(id, queue) }
                }

                item {
                    ReadableColumn {
                        CompleteButton(
                            completed = completed,
                            working = completing,
                            onComplete = {
                                scope.launch {
                                    completing = true
                                    // Queued first: 「読み終えた」 must survive a
                                    // tunnel. The button turns regardless,
                                    // because the record is safe either way.
                                    completed = runCatching {
                                        queue.markComplete(id)
                                        queue.flush()
                                        true
                                    }.getOrDefault(false)
                                    completing = false
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 「読み終えた」 — the student's own statement, not something inferred.
 *
 * Deliberately not triggered by scrolling to the bottom. Completion is what
 * 基本を解ける is built on (共通判断規則 §2), so it should mean the student decided
 * they were done, not that a scroll position crossed a threshold.
 */
@Composable
private fun CompleteButton(completed: Boolean, working: Boolean, onComplete: () -> Unit) {
    if (completed) {
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Text("　読み終えました")
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onComplete, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                Text(if (working) "記録しています…" else "読み終えた")
            }
            Text(
                text = "この項目のレッスンをすべて読み終えると、理解マップに反映されます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ApiError.studentFacingMessage(): String = when (this) {
    is ApiError.Offline -> "インターネットにつながっていないようです。"
    is ApiError.TimedOut -> "時間内に返事がありませんでした。"
    is ApiError.Decoding -> "内容を読み取れませんでした。アプリの更新があるか確認してください。"
    else -> "いま読み込めませんでした。しばらくしてから、もう一度ためしてください。"
}
