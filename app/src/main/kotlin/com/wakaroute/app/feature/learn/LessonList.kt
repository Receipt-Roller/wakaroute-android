package com.wakaroute.app.feature.learn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wakaroute.app.ui.design.AdaptiveRow
import com.wakaroute.core.content.ContentClient
import com.wakaroute.core.content.CourseProgressDetail
import com.wakaroute.core.content.LessonSummary
import com.wakaroute.core.net.ApiError

private sealed interface LessonListState {
    /** No account yet, so there is nothing to fetch and nothing to create. */
    data object NotRegistered : LessonListState
    data object Loading : LessonListState
    data class Loaded(val lessons: List<LessonSummary>, val progress: CourseProgressDetail?) : LessonListState
    data object Failed : LessonListState
}

/**
 * The lessons of one 要素.
 *
 * Lives on the 項目 screen rather than behind a separate 学ぶ tab. §5 describes
 * 学ぶ as 教科 → 領域 → 項目 → レッスン, which is the route the 理解マップ already
 * takes for its first three steps — building a second one would give the same
 * place two doors and let them drift apart.
 *
 * Only loaded when the device already has an account, for the same reason as
 * everywhere else: the first authenticated call creates a MANABU2 learner.
 */
@Composable
fun LessonList(
    content: ContentClient,
    courseId: String,
    isRegistered: () -> Boolean,
    onOpenLesson: (String) -> Unit,
) {
    var state by remember(courseId) { mutableStateOf<LessonListState>(LessonListState.Loading) }

    LaunchedEffect(courseId) {
        if (!isRegistered()) {
            state = LessonListState.NotRegistered
            return@LaunchedEffect
        }

        state = try {
            val course = content.courseDetail(courseId)
            // Progress is a second request and a lesser one: without it the
            // list still works, it just cannot say which lessons are done.
            val progress = runCatching { content.courseProgress(courseId) }.getOrNull()
            LessonListState.Loaded(course.lessons, progress)
        } catch (e: ApiError) {
            LessonListState.Failed
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "レッスン", style = MaterialTheme.typography.titleMedium)

        when (val current = state) {
            LessonListState.NotRegistered -> Note("レッスンはまだ読み込んでいません。")
            LessonListState.Loading -> Note("読み込んでいます…")
            LessonListState.Failed -> Note("レッスンを読み込めませんでした。")

            is LessonListState.Loaded -> if (current.lessons.isEmpty()) {
                Note("この項目にはまだレッスンがありません。")
            } else {
                current.lessons.forEachIndexed { index, lesson ->
                    LessonRow(
                        number = index + 1,
                        lesson = lesson,
                        // Three states, not two: done, opened but not finished,
                        // and never opened. Only the first counts towards
                        // 基本を解ける.
                        isCompleted = current.progress?.forLesson(lesson.id)?.isCompleted == true,
                        onClick = { onOpenLesson(lesson.id) },
                    )
                    HorizontalDivider()
                }

                current.progress?.let {
                    Text(
                        text = "${it.completedLessons} / ${it.totalLessons} 読み終えました",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LessonRow(
    number: Int,
    lesson: LessonSummary,
    isCompleted: Boolean,
    onClick: () -> Unit,
) {
    val announcement = buildString {
        append("$number、${lesson.title}")
        if (isCompleted) append("、読み終えました")
        if (lesson.hasQuiz) append("、確認クイズあり")
    }

    AdaptiveRow(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = announcement }
            .padding(vertical = 12.dp),
    ) { flexible ->
        Column(modifier = flexible, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = "$number. ${lesson.title}", style = MaterialTheme.typography.bodyLarge)

            val notes = buildList {
                // 「読み終えました」 in words, not a tick alone — §8 forbids
                // carrying state on a symbol with nothing beside it.
                if (isCompleted) add("読み終えました")
                if (lesson.hasQuiz) add("確認クイズあり")
            }
            if (notes.isNotEmpty()) {
                Text(
                    text = notes.joinToString("　"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
