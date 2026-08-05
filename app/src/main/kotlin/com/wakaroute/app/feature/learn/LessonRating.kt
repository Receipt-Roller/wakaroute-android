package com.wakaroute.app.feature.learn

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wakaroute.app.ui.design.AdaptiveRow
import com.wakaroute.core.offline.LearningActionQueue
import kotlinx.coroutines.launch

/**
 * 「わかった」/「むずかしかった」 — §6 of the 共通判断規則.
 *
 * Every constraint here is one the obvious version of this feature breaks:
 *
 * - **Two values, not five.** 中学生 cluster on the middle of a scale, the
 *   average never moves, and nobody learns which lesson to fix.
 * - **It never interrupts.** Not a modal, not required, not a popup. 「毎回
 *   ポップアップが出れば、生徒は中身を見ずに閉じます。それは無いデータより悪いデータです」
 * - **Options first, free text optional.** 中学生 do not write paragraphs; with
 *   no options the response rate falls and nothing can be counted.
 * - **The comment is read by a person**, so the field says not to write a name
 *   or a school.
 */
@Composable
fun LessonRating(lessonId: String, queue: LearningActionQueue) {
    var understood by remember(lessonId) { mutableStateOf<Boolean?>(null) }
    var comment by remember(lessonId) { mutableStateOf("") }
    val reasons = remember(lessonId) { mutableStateListOf<String>() }
    var sent by remember(lessonId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (sent) {
        Text(
            text = "ありがとうございます。レッスンを直すのに使わせてもらいます。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Deliberately if/else and not an early return: returning out of a
        // composable that has already emitted corrupts Compose's slot table.
    } else {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("このレッスンはわかりましたか？", style = MaterialTheme.typography.titleSmall)

        AdaptiveRow(modifier = Modifier.fillMaxWidth()) { _ ->
            FilterChip(
                selected = understood == true,
                onClick = { understood = true; reasons.clear() },
                label = { Text("わかった") },
            )
            FilterChip(
                selected = understood == false,
                onClick = { understood = false },
                label = { Text("むずかしかった") },
            )
        }

        // Only when there is something to fix. Asking why a lesson worked
        // produces answers nobody can act on.
        if (understood == false) {
            Text("どこがむずかしかったですか？（いくつでも）", style = MaterialTheme.typography.bodySmall)

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (reason in REASONS) {
                    FilterChip(
                        selected = reason.key in reasons,
                        onClick = {
                            if (reason.key in reasons) reasons.remove(reason.key) else reasons.add(reason.key)
                        },
                        label = { Text(reason.label) },
                    )
                }
            }

            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it.take(LearningActionQueue.MAX_COMMENT_LENGTH) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("よければ、くわしく（任意）") },
                // Stopped at the field, not at the server. Over the limit is a
                // 400, which the queue treats as permanent and discards — the
                // student's whole answer, for a long comment.
                supportingText = {
                    Text("名前や学校名は書かないでください。${comment.length} / ${LearningActionQueue.MAX_COMMENT_LENGTH}")
                },
                minLines = 2,
            )
        }

        if (understood != null) {
            OutlinedButton(
                onClick = {
                    val verdict = understood == true
                    scope.launch {
                        // Through the queue, so an answer given underground is
                        // still an answer. Replaced there if they change it.
                        queue.rateLesson(
                            lessonId = lessonId,
                            understood = verdict,
                            reasons = reasons.toList(),
                            comment = comment.takeIf { it.isNotBlank() },
                        )
                        runCatching { queue.flush() }
                        sent = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("送る")
            }
        }
    }
    }
}

/** Wrapped so the section reads as an aside rather than a demand. */
@Composable
fun LessonRatingCard(lessonId: String, queue: LearningActionQueue) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            LessonRating(lessonId, queue)
        }
    }
}

/**
 * The reasons, as keys the server aggregates plus the words a 中学生 reads.
 *
 * The keys are sent, never the labels: rewording a chip must not split its
 * counts in two.
 */
private data class Reason(val key: String, val label: String)

private val REASONS = listOf(
    Reason("explanation", "説明が分かりにくい"),
    Reason("example", "例が足りない"),
    Reason("pace", "進みが速い"),
    Reason("prerequisite", "前の内容を忘れている"),
    Reason("other", "そのほか"),
)
