package com.wakaroute.app.feature.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wakaroute.app.ui.design.AdaptiveRow
import com.wakaroute.core.journal.DiaryDraft
import com.wakaroute.core.journal.JournalCategories
import com.wakaroute.core.journal.JournalLimits
import com.wakaroute.core.journal.NewDayLogEntry
import java.time.LocalDate
import java.util.UUID

/**
 * The diary for one day. Opened from the current diary, never from nothing —
 * the save replaces the whole thing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiaryEditorSheet(
    date: LocalDate,
    initial: DiaryDraft,
    onDismiss: () -> Unit,
    onSave: (DiaryDraft) -> Unit,
) {
    // Saveable: a paragraph typed in bed must survive the screen rotating.
    var achievements by rememberSaveable { mutableStateOf(initial.achievements) }
    var struggles by rememberSaveable { mutableStateOf(initial.struggles) }
    var tomorrowPlan by rememberSaveable { mutableStateOf(initial.tomorrowPlan) }
    var focus by rememberSaveable { mutableStateOf(initial.focus) }
    var fatigue by rememberSaveable { mutableStateOf(initial.fatigue) }
    val draft = DiaryDraft(achievements, struggles, tomorrowPlan, focus, fatigue)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AdaptiveRow(modifier = Modifier.fillMaxWidth()) { flexible ->
                Text("${date.monthValue}月${date.dayOfMonth}日の日記", style = MaterialTheme.typography.titleLarge, modifier = flexible)
                TextButton(onClick = onDismiss) { Text("キャンセル") }
                // Checked while typing, rather than meeting a 400 after 保存.
                Button(onClick = { onSave(draft) }, enabled = !draft.isOverLimit) { Text("保存") }
            }

            DiaryField("できたこと", achievements, "わかったこと、進んだこと") { achievements = it }
            DiaryField("困ったこと", struggles, "つまずいたところ、わからなかったこと") { struggles = it }
            Caption("書いておくと、あとで見返すときに役立ちます。")
            DiaryField("明日やること", tomorrowPlan, "英語の長文 2 題、など") { tomorrowPlan = it }

            Text("この日の調子", style = MaterialTheme.typography.titleMedium)
            Caption("任意です。えらばなくてもかまいません。")
            MoodPicker("集中できた", focus, low = "あまり", high = "とても") { focus = it }
            MoodPicker("つかれ", fatigue, low = "元気", high = "くたくた") { fatigue = it }

            if (draft.isOverLimit) {
                Text(
                    "1つの欄は ${JournalLimits.DIARY_TEXT_LENGTH} 文字までです。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Grows with what is typed: a paragraph the student cannot see all of is one they stop writing. */
@Composable
private fun DiaryField(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
    )
}

/** 1–5, with the number written out, and both ends labelled so it is clear which way is which. */
@Composable
private fun MoodPicker(title: String, value: Int?, low: String, high: String, onChange: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AdaptiveRow(modifier = Modifier.fillMaxWidth()) { flexible ->
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = flexible)
            if (value != null) TextButton(onClick = { onChange(null) }) { Text("えらばない") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Caption(low)
            for (step in 1..5) {
                FilterChip(
                    selected = value == step,
                    onClick = { onChange(step) },
                    label = { Text("$step") },
                    modifier = Modifier.semantics { contentDescription = "$title 5 段階のうち $step" },
                )
            }
            Caption(high)
        }
    }
}

/** 時間を記録 — a block of time on the day being shown. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun AddEntrySheet(remainingMinutes: Int, onDismiss: () -> Unit, onAdd: (NewDayLogEntry) -> Unit) {
    var category by rememberSaveable { mutableStateOf(JournalCategories.SELF_STUDY) }
    var minutes by rememberSaveable { mutableIntStateOf(30.coerceAtMost(remainingMinutes)) }
    var subject by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    // Made once per sheet, so a retry after a lost reply is the same block.
    val clientEntryId = rememberSaveable { "android-${UUID.randomUUID()}" }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AdaptiveRow(modifier = Modifier.fillMaxWidth()) { flexible ->
                Text("時間を記録", style = MaterialTheme.typography.titleLarge, modifier = flexible)
                TextButton(onClick = onDismiss) { Text("キャンセル") }
                Button(
                    onClick = {
                        val study = JournalCategories.countsAsStudy(category)
                        onAdd(
                            NewDayLogEntry(
                                clientEntryId = clientEntryId,
                                category = category,
                                durationMinutes = minutes,
                                subject = subject.trim().takeIf { study && it.isNotEmpty() },
                                content = content.trim().takeIf { study && it.isNotEmpty() },
                            ),
                        )
                    },
                    enabled = minutes in 1..remainingMinutes,
                ) { Text("追加") }
            }

            Text("なにを", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in JournalCategories.known) {
                    FilterChip(
                        selected = category == option,
                        onClick = { category = option },
                        label = { Text(JournalCategories.displayName(option)) },
                    )
                }
            }

            Text("どれくらい", style = MaterialTheme.typography.titleMedium)
            Text(duration(minutes), style = MaterialTheme.typography.headlineSmall)
            // The usual lengths are one tap; the buttons below cover the rest.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (length in listOf(15, 30, 45, 60, 90, 120, 180)) {
                    FilterChip(
                        selected = minutes == length,
                        onClick = { minutes = length },
                        enabled = length <= remainingMinutes,
                        label = { Text(duration(length)) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { minutes = (minutes - 5).coerceAtLeast(5) }) {
                    Icon(Icons.Filled.Remove, contentDescription = "5分へらす")
                }
                Caption("5分きざみで調整")
                IconButton(onClick = { minutes = (minutes + 5).coerceAtMost(remainingMinutes) }) {
                    Icon(Icons.Filled.Add, contentDescription = "5分ふやす")
                }
            }
            Caption("この日はあと ${duration(remainingMinutes)} 記録できます。")

            if (JournalCategories.countsAsStudy(category)) {
                Text("くわしく", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it.take(JournalLimits.SUBJECT_LENGTH) },
                    placeholder = { Text("教科（数学、英語…）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it.take(JournalLimits.CONTENT_LENGTH) },
                    placeholder = { Text("内容（p.42 二次関数 …）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Caption("任意です。")
            }
        }
    }
}
