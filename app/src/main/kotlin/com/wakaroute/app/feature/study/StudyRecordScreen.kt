package com.wakaroute.app.feature.study

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakaroute.app.ui.design.AdaptiveRow
import com.wakaroute.app.ui.design.ReadableColumn
import androidx.compose.ui.semantics.semantics
import com.wakaroute.core.content.ContentClient
import com.wakaroute.core.content.StudyDay
import com.wakaroute.core.content.StudyStreak
import com.wakaroute.core.net.ApiError
import com.wakaroute.core.offline.LearningActionQueue
import com.wakaroute.core.study.StudyTimer
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 記録 — the timer, the streak, and what has been studied.
 *
 * The clock runs on the device and the finished session goes through the
 * offline queue, so a session studied underground is still a session. The
 * **streak and the totals are the server's**, because they have to survive a
 * new phone: a 中1 student who starts here sits their exams in 中3.
 */
@Composable
fun StudyRecordScreen(
    onOpenJournal: () -> Unit,
    timer: StudyTimer,
    queue: LearningActionQueue,
    content: ContentClient,
) {
    val running by timer.running.collectAsStateWithLifecycle()
    var elapsed by remember { mutableStateOf(0) }
    var streak by remember { mutableStateOf<StudyStreak?>(null) }
    var days by remember { mutableStateOf<List<StudyDay>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    // Ticks only while something is running, so a screen left open costs
    // nothing.
    LaunchedEffect(running) {
        while (running != null) {
            elapsed = timer.elapsedSeconds()
            delay(1000)
        }
        elapsed = 0
    }

    LaunchedEffect(reload) {
        try {
            val today = LocalDate.now()
            streak = content.studyStreak()
            days = content.studySummary(from = today.minusDays(29).toString(), to = today.toString())
            loadFailed = false
        } catch (e: ApiError) {
            loadFailed = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ReadableColumn(spacing = 20.dp) {
            Text("記録", style = MaterialTheme.typography.headlineSmall)

            TimerCard(
                isRunning = running != null,
                elapsedSeconds = elapsed,
                onStart = { timer.start() },
                onStop = {
                    scope.launch {
                        val recorded = timer.stop()
                        runCatching { queue.flush() }
                        if (recorded != null) reload++
                    }
                },
            )

            JournalCard(onOpenJournal)

            streak?.let { StreakRow(it) }

            days?.let { History(it) }

            if (loadFailed) {
                Text(
                    text = "記録をいま読み込めませんでした。計測したぶんは端末に保存してあります。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (days?.isEmpty() != false && streak == null && !loadFailed) {
                Text(
                    // Not 「まだ勉強していません」 — that would be a claim about the
                    // student. Nothing has been recorded because nothing has
                    // been recorded anywhere yet.
                    text = "まだ記録はありません。タイマーを止めると、ここに残ります。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The way into 受験日記. A card here rather than a tab: the diary is about the
 * same day the timer is, and splitting them would answer 「きょう何をした？」 in
 * two places. The same choice as iOS.
 */
@Composable
private fun JournalCard(onClick: () -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {},
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("受験日記", style = MaterialTheme.typography.titleMedium)
            Text(
                "できたこと、困ったこと、明日やること",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TimerCard(
    isRunning: Boolean,
    elapsedSeconds: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (isRunning) formatDuration(elapsedSeconds) else "00:00",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            if (isRunning) {
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Text("やめる")
                }
            } else {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text("勉強をはじめる")
                }
            }

            Text(
                text = "1分より短いものは記録しません。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun StreakRow(streak: StudyStreak) {
    AdaptiveRow(modifier = Modifier.fillMaxWidth()) { flexible ->
        Column(modifier = flexible, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("連続で勉強した日数", style = MaterialTheme.typography.labelLarge)
            Text(
                // 0 is a real answer, not an omission — and it is not phrased as
                // a failure.
                text = if (streak.currentDays > 0) "${streak.currentDays}日" else "まだありません",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        if (streak.longestDays > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("いちばん長かったとき", style = MaterialTheme.typography.labelLarge)
                Text("${streak.longestDays}日", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

/**
 * The last 30 days.
 *
 * The series arrives dense — zero days included — so nothing here has to work
 * out how many days a month has, which is the thing the API's own notes say a
 * client filling gaps reliably gets wrong.
 */
@Composable
private fun History(days: List<StudyDay>) {
    val studied = days.filter { it.totalSeconds > 0 }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("これまで（30日）", style = MaterialTheme.typography.titleMedium)

        // if/else, never an early `return`. Returning out of a composable
        // after it has already emitted corrupts Compose's slot table, and the
        // crash lands in the runtime with nothing pointing back here. This
        // exact shape took the 記録 screen down.
        if (studied.isEmpty()) {
            Text(
                text = "この30日の記録はまだありません。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "合計 ${formatDuration(days.sumOf { it.totalSeconds })}　" +
                    "勉強した日 ${studied.size}日",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            for (day in studied.sortedByDescending { it.date }) {
                HorizontalDivider()
                AdaptiveRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { flexible ->
                    Text(day.date, style = MaterialTheme.typography.bodyLarge, modifier = flexible)
                    Text(formatDuration(day.totalSeconds), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/**
 * `h:mm:ss` past an hour, `mm:ss` below it.
 *
 * Written out rather than using a locale formatter: a duration is not a
 * time-of-day, and formatters that know about noon and midnight produce
 * surprising things when handed 90 minutes.
 */
private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, remaining)
    } else {
        "%02d:%02d".format(minutes, remaining)
    }
}
