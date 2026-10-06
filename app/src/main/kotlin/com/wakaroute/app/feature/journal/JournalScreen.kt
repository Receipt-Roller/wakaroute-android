package com.wakaroute.app.feature.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wakaroute.app.ui.design.AdaptiveRow
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.core.journal.DiaryDraft
import com.wakaroute.core.journal.DiaryEntry
import com.wakaroute.core.journal.JournalCategories
import com.wakaroute.core.journal.JournalClient
import com.wakaroute.core.journal.JournalDates
import com.wakaroute.core.journal.JournalDay
import com.wakaroute.core.journal.JournalDayRecord
import com.wakaroute.core.journal.JournalDaySummary
import com.wakaroute.core.journal.JournalOutbox
import com.wakaroute.core.journal.JournalRefusal
import com.wakaroute.core.journal.NewDayLogEntry
import com.wakaroute.core.journal.PendingJournalWrite
import com.wakaroute.core.journal.StudySource
import com.wakaroute.core.net.ApiError
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 受験日記 — one day: what was done, how it went, and what tomorrow holds.
 *
 * The same screen as iOS, built from one `GET /{date}` plus whatever this
 * device wrote and has not sent. Writing is offered whether or not the day
 * could be read: the student on a train is the one who most needs to write the
 * day down, and the outbox holds it until the signal comes back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(client: JournalClient, outbox: JournalOutbox, onBack: () -> Unit) {
    var date by remember { mutableStateOf(JournalDates.today()) }
    var day by remember { mutableStateOf<JournalDay?>(null) }
    var week by remember { mutableStateOf<List<JournalDaySummary>>(emptyList()) }
    var unsent by remember { mutableStateOf<List<PendingJournalWrite>>(emptyList()) }
    var loadFailed by remember { mutableStateOf(false) }
    var refusal by remember { mutableStateOf<JournalRefusal?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var editingDiary by remember { mutableStateOf(false) }
    var addingEntry by remember { mutableStateOf(false) }
    val coroutines = rememberCoroutineScope()

    LaunchedEffect(date, reload) {
        // Anything queued goes first, so what is read back already includes it.
        runCatching { outbox.flush() }
        day = try {
            client.day(date).also { loadFailed = false }
        } catch (e: ApiError) {
            loadFailed = true
            // The same day, read a moment ago, is still the best account of it:
            // a student who opened this on Wi-Fi and lost it on the stairs
            // should not watch their diary vanish. Another date's record is
            // never kept — that would show one day under another's heading.
            day?.takeIf { it.date == date.toString() }
        }
        week = runCatching { client.summary(date.minusDays(6), date) }.getOrDefault(emptyList())
        unsent = outbox.pending()
    }

    val record = JournalDayRecord.of(date.toString(), day, unsent)

    // A save replaces the whole diary. Opened from a diary we could not read,
    // the form starts empty, and saving it — even later, from the outbox —
    // would erase what the student wrote earlier. Time blocks are only ever
    // added, so they stay writable offline.
    val diaryIsKnown = day != null || record.isDiaryUnsent
    val isToday = date == JournalDates.today()
    val isWritable = JournalDates.isWritable(date)

    /** Tries now; keeps it in the outbox if the connection is the problem. */
    fun write(send: suspend () -> Unit, queue: suspend () -> Unit) {
        coroutines.launch {
            try {
                send()
            } catch (e: ApiError) {
                val refused = JournalRefusal.of(e)
                // The server has answered. Asking again gets the same answer,
                // so it is explained rather than queued.
                if (refused != null) refusal = refused else queue()
            }
            reload++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("受験日記") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            ReadableColumn(spacing = 24.dp) {
                DayPicker(
                    date = date,
                    isToday = isToday,
                    isWritable = isWritable,
                    onPrevious = { date = date.minusDays(1) },
                    onNext = { if (date < JournalDates.today()) date = date.plusDays(1) },
                )
                WeekStrip(week, selected = date, onSelect = { date = it })

                if (loadFailed) LoadFailedNotice(onRetry = { reload++ })

                DiarySection(record, isToday, isWritable, diaryIsKnown, onEdit = { editingDiary = true })
                TimeLogSection(
                    record = record,
                    isWritable = isWritable,
                    onAdd = { addingEntry = true },
                    onDelete = { entryId ->
                        coroutines.launch {
                            // Not queued. A block still on the server is visibly
                            // still there, which beats a screen claiming it is gone.
                            try {
                                client.deleteEntry(entryId)
                            } catch (e: ApiError) {
                                refusal = JournalRefusal.of(e)
                            }
                            reload++
                        }
                    },
                )

                // With no reply from the server, 0分 would be a guess, not a fact.
                if (record.hasContent) StudyTotalsSection(record)

                if (record.unsentCount > 0) UnsentNotice(record.unsentCount)
            }
        }
    }

    if (editingDiary) {
        DiaryEditorSheet(
            date = date,
            initial = DiaryDraft.from(record.diary),
            onDismiss = { editingDiary = false },
            onSave = { draft ->
                editingDiary = false
                // Nothing written and nothing there: closing an untouched form
                // must not send a delete for a diary that never existed.
                if (!(draft.isEmpty && record.diary == null)) {
                    val day = date
                    if (draft.isEmpty) {
                        write({ client.deleteDiary(day) }, { outbox.queueDiaryDeletion(day) })
                    } else {
                        write({ client.saveDiary(draft, day) }, { outbox.queueDiary(draft, day) })
                    }
                }
            },
        )
    }

    if (addingEntry) {
        AddEntrySheet(
            remainingMinutes = record.remainingMinutes,
            onDismiss = { addingEntry = false },
            onAdd = { entry: NewDayLogEntry ->
                addingEntry = false
                val day = date
                write({ client.addEntry(entry, day) }, { outbox.queueEntry(entry, day) })
            },
        )
    }

    refusal?.let { refused ->
        AlertDialog(
            onDismissRequest = { refusal = null },
            confirmButton = { TextButton(onClick = { refusal = null }) { Text("OK") } },
            title = { Text(refused.title()) },
            text = { Text(refused.message()) },
        )
    }
}

/** Plain, and about the rule rather than the student. Nothing judges how they spent the day. */
private fun JournalRefusal.title() = when (this) {
    JournalRefusal.DayFull -> "1日は24時間までです"
    JournalRefusal.TooManyEntries -> "この日の記録がいっぱいです"
    JournalRefusal.DateNotWritable -> "この日はもう書けません"
    JournalRefusal.InvalidRequest -> "保存できませんでした"
}

private fun JournalRefusal.message() = when (this) {
    JournalRefusal.DayFull -> "記録した時間の合計が24時間を超えています。どれかを短くするか、削除してください。"
    JournalRefusal.TooManyEntries -> "1日に記録できるのは50件までです。"
    JournalRefusal.DateNotWritable -> "書けるのは今日から31日前までです。"
    JournalRefusal.InvalidRequest -> "入力の形式が正しくないようです。文字数や時間を確認してください。"
}

// --- day selection ---------------------------------------------------------

private val TITLE_FORMAT = DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPANESE)

@Composable
private fun DayPicker(date: LocalDate, isToday: Boolean, isWritable: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前の日")
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.format(TITLE_FORMAT), style = MaterialTheme.typography.titleMedium)
            when {
                isToday -> Caption("きょう")
                !isWritable -> Caption("記録できる期間をすぎています")
            }
        }
        IconButton(onClick = onNext, enabled = !isToday) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "次の日")
        }
    }
}

/**
 * The last seven days, totals only — from `summary`, which carries no diary
 * text, so the strip is safe on screen with somebody looking over a shoulder.
 * A bar with the number beside it: height never carries the information alone.
 */
@Composable
private fun WeekStrip(days: List<JournalDaySummary>, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    if (days.isEmpty()) return
    val busiest = days.maxOf { it.loggedMinutes }.coerceAtLeast(1)

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (day in days) {
            val date = LocalDate.parse(day.date)
            val isSelected = date == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(date) }
                    .semantics(mergeDescendants = true) {
                        this.selected = isSelected
                        contentDescription = "${date.monthValue}月${date.dayOfMonth}日、" +
                            (if (day.loggedMinutes > 0) "記録 ${day.loggedMinutes}分" else "記録なし") +
                            (if (day.hasDiary) "、日記あり" else "、日記なし")
                    }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Caption(date.format(DateTimeFormatter.ofPattern("E", Locale.JAPANESE)))
                Box(modifier = Modifier.height(34.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .width(12.dp)
                            .height((34f * day.loggedMinutes / busiest).coerceAtLeast(4f).dp)
                            .background(
                                if (day.loggedMinutes > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(3.dp),
                            ),
                    )
                }
                Text(
                    "${date.dayOfMonth}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
                Caption(if (day.hasDiary) "日記" else " ")
            }
        }
    }
}

// --- diary ---------------------------------------------------------------

@Composable
private fun DiarySection(
    record: JournalDayRecord,
    isToday: Boolean,
    isWritable: Boolean,
    diaryIsKnown: Boolean,
    onEdit: () -> Unit,
) {
    val canEdit = isWritable && diaryIsKnown

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AdaptiveRow(modifier = Modifier.fillMaxWidth()) { flexible ->
            // 「きょう」 only when it is. Yesterday's page saying きょう is a small
            // lie on a screen whose whole job is which day was which.
            SectionHeader(
                title = if (isToday) "きょうのふりかえり" else "この日のふりかえり",
                subtitle = "できたこと・困ったこと・明日やること",
                modifier = flexible,
            )
            if (canEdit) TextButton(onClick = onEdit) { Text(if (record.diary == null) "書く" else "編集") }
        }

        if (record.isDiaryUnsent) Caption("まだ送信していません")

        if (!diaryIsKnown) {
            Card {
                Text(
                    "この日の日記を読み込めませんでした。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Said, so the missing button is not a mystery. 一日の時間 still works.
                Caption("前に書いたものを消さないように、つながってから書けるようにしています。")
            }
            return@Column
        }

        Card {
            val diary = record.diary
            if (diary != null) {
                DiaryReading(diary)
            } else {
                Text(
                    if (isWritable) "まだ何も書いていません。" else "この日は書かれていません。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isWritable) {
                    // About the day, not about effort. Nothing asks whether they did enough.
                    Caption("うまくいったこと、つまずいたこと、明日やること。ひとことでかまいません。")
                    OutlinedButton(onClick = onEdit) { Text("書いてみる") }
                }
            }
        }
    }
}

@Composable
private fun DiaryReading(diary: DiaryEntry) {
    DiaryField("できたこと", diary.achievements)
    DiaryField("困ったこと", diary.struggles)
    DiaryField("明日やること", diary.tomorrowPlan)
    if (diary.focus != null || diary.fatigue != null) {
        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // The number is written out, never carried by a shape alone.
            diary.focus?.let { Text("集中 $it / 5", style = MaterialTheme.typography.bodyMedium) }
            diary.fatigue?.let { Text("つかれ $it / 5", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun DiaryField(label: String, text: String?) {
    if (text.isNullOrBlank()) return
    Column(modifier = Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Caption(label)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

// --- time log ------------------------------------------------------------

@Composable
private fun TimeLogSection(
    record: JournalDayRecord,
    isWritable: Boolean,
    onAdd: () -> Unit,
    onDelete: (entryId: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AdaptiveRow(modifier = Modifier.fillMaxWidth()) { flexible ->
            SectionHeader(title = "一日の時間", subtitle = "学校・部活・睡眠もふくめて", modifier = flexible)
            if (isWritable) TextButton(onClick = onAdd) { Text("追加") }
        }

        if (record.rows.isEmpty()) {
            Text(
                if (isWritable) "「追加」から、その日にやったことを分で記録できます。" else "この日の記録はありません。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Card {
                record.rows.forEachIndexed { index, row ->
                    AdaptiveRow(modifier = Modifier.fillMaxWidth()) { flexible ->
                        Column(modifier = flexible, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(JournalCategories.displayName(row.category), style = MaterialTheme.typography.bodyLarge)
                            row.detailLine?.let { Caption(it) }
                            if (!row.isSent) Caption("まだ送信していません")
                        }
                        Text(duration(row.durationMinutes), style = MaterialTheme.typography.titleSmall)
                        // A visible button, not a swipe: a gesture with no mark on
                        // screen is not a way to offer the only fix for a typo.
                        // Only once the server has the block — there is no id to
                        // delete before that.
                        row.entryId?.let { entryId ->
                            IconButton(onClick = { onDelete(entryId) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "${JournalCategories.displayName(row.category)} の記録を削除")
                            }
                        }
                    }
                    if (index < record.rows.lastIndex) HorizontalDivider()
                }
            }
            CategoryBreakdown(record.minutesByCategory)
        }

        if (record.autoStudy.isNotEmpty()) {
            Caption("アプリが記録した学習")
            Card {
                for (session in record.autoStudy) {
                    AdaptiveRow(modifier = Modifier.fillMaxWidth()) { flexible ->
                        Text(session.subject ?: "学習", style = MaterialTheme.typography.bodyLarge, modifier = flexible)
                        Text(duration(session.durationMinutes), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdown(rows: List<Pair<String, Int>>) {
    val total = rows.sumOf { it.second }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((category, minutes) in rows) {
            val name = JournalCategories.displayName(category)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$name ${duration(minutes)}" },
            ) {
                Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(110.dp))
                Box(Modifier.weight(1f).height(8.dp)) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(minutes.toFloat() / total)
                            .background(
                                if (JournalCategories.countsAsStudy(category)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(3.dp),
                            ),
                    )
                }
                Text(duration(minutes), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// --- totals --------------------------------------------------------------

/**
 * The day's two study numbers, side by side and **never added up**: a student
 * who ran the timer for 25 minutes and logged 40 for the same evening studied
 * somewhere between 40 and 65, and nothing knows which.
 */
@Composable
private fun StudyTotalsSection(record: JournalDayRecord) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "学習時間")
        AdaptiveRow(modifier = Modifier.fillMaxWidth()) { flexible ->
            TotalCard(StudySource.Learner, record.studySelfMinutes, "自主学習・宿題・塾", record.headline == StudySource.Learner, flexible)
            TotalCard(StudySource.App, record.studyAutoMinutes, "学習タイマー", record.headline == StudySource.App, flexible)
        }
        Caption("同じ時間を両方で記録していることがあるため、合計はしていません。")
        record.practice?.takeIf { it.answered > 0 }?.let {
            Caption("練習問題 ${it.answered} 問中 ${it.correct} 問正解")
        }
    }
}

@Composable
private fun TotalCard(source: StudySource, minutes: Int, caption: String, isHeadline: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .border(
                width = 1.5.dp,
                color = if (isHeadline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
            )
            .semantics(mergeDescendants = true) { contentDescription = "${source.displayName} ${duration(minutes)}、$caption" }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Caption(source.displayName)
        Text(duration(minutes), style = MaterialTheme.typography.titleLarge)
        Caption(caption)
    }
}

// --- notices -------------------------------------------------------------

/** Stated plainly, not as a warning. Writing on a train is normal, and nothing written is lost. */
@Composable
private fun UnsentNotice(count: Int) {
    Card { Caption("$count 件はまだ送信していません。あとで自動的に送ります。") }
}

/** A line, not a wall: the day could not be read, but writing still works. */
@Composable
private fun LoadFailedNotice(onRetry: () -> Unit) {
    Card {
        AdaptiveRow(modifier = Modifier.fillMaxWidth()) { flexible ->
            Column(modifier = flexible) {
                Text("記録を読み込めませんでした", style = MaterialTheme.typography.bodyMedium)
                Caption("いま書いたものは、つながったときに送ります。")
            }
            TextButton(onClick = onRetry) { Text("再読み込み") }
        }
    }
}

// --- shared --------------------------------------------------------------

/** 90 → 「1時間30分」. */
internal fun duration(minutes: Int): String = when {
    minutes < 60 -> "${minutes}分"
    minutes % 60 == 0 -> "${minutes / 60}時間"
    else -> "${minutes / 60}時間${minutes % 60}分"
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        subtitle?.let { Caption(it) }
    }
}

@Composable
internal fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}
