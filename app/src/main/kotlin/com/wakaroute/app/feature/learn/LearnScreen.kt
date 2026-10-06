package com.wakaroute.app.feature.learn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wakaroute.app.feature.tests.TestsEntryCard
import com.wakaroute.app.ui.design.AdaptiveRow
import com.wakaroute.app.ui.design.Centered
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.app.ui.design.studentFacingMessage
import com.wakaroute.core.content.ContentClient
import com.wakaroute.core.content.CourseProgress
import com.wakaroute.core.content.PathDetail
import com.wakaroute.core.content.SubjectCatalog
import com.wakaroute.core.content.SubjectPaths
import com.wakaroute.core.map.SchoolSubject
import com.wakaroute.core.net.ApiError
import java.net.URLDecoder

/**
 * 学ぶ: 教科 → 領域 → 項目 → レッスン, for every 教科 that has content.
 *
 * The same route as iOS's 学ぶ tab, built from MANABU2 paths grouped by their
 * 教科 label. It makes no claim about what the student should do next — that
 * needs prerequisite edges, and only 数学 has them, so only 数学 links to its
 * 理解マップ.
 */
@Composable
fun LearnScreen(
    content: ContentClient,
    onOpenTests: () -> Unit,
    onOpenCards: () -> Unit,
    onOpenSubject: (SchoolSubject) -> Unit,
    onOpenMap: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        ReadableColumn(spacing = 16.dp) {
            Text(text = "学ぶ", style = MaterialTheme.typography.headlineSmall)

            TestsEntryCard(onOpenTests)

            // iOS's wording, word for word: 共通判断規則 「言葉は揃える」.
            NavigationRow(
                title = "5教科のカード",
                detail = "漢字・単語・数学・理科・社会。電波がなくても使えます",
                onClick = onOpenCards,
            )

            Loading(key = Unit, load = { SubjectCatalog.group(content.paths()) }) { subjects ->
                Column {
                    for (subjectPaths in subjects) {
                        SubjectRow(subjectPaths, onClick = { onOpenSubject(subjectPaths.subject) })
                        if (subjectPaths.subject == SchoolSubject.Math) {
                            NavigationRow(
                                title = "理解マップを見る",
                                detail = "何が何の前提になっているか",
                                onClick = onOpenMap,
                                modifier = Modifier.padding(start = 24.dp),
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectRow(subjectPaths: SubjectPaths, onClick: () -> Unit) {
    NavigationRow(
        title = subjectPaths.subject.label,
        detail = "${subjectPaths.paths.size} 領域・${subjectPaths.courseCount} 項目",
        onClick = onClick,
        titleStyle = MaterialTheme.typography.titleLarge,
    )
}

/** One 教科's 領域. */
@Composable
fun SubjectScreen(
    content: ContentClient,
    subjectName: String,
    onOpenPath: (pathId: String, title: String) -> Unit,
    onBack: () -> Unit,
) {
    val subject = remember(subjectName) { SchoolSubject.entries.firstOrNull { it.name == subjectName } }

    PushedScreen(title = subject?.label ?: "学ぶ", onBack = onBack) { padding ->
        Loading(key = subject, load = { SubjectCatalog.group(content.paths()) }, padding = padding) { subjects ->
            val paths = subjects.firstOrNull { it.subject == subject }?.paths.orEmpty()

            ListOf(padding, heading = "領域") {
                items(paths) { path ->
                    ReadableColumn(spacing = 0.dp) {
                        val title = SubjectCatalog.domainName(path)
                        NavigationRow(
                            title = title,
                            detail = "${path.courseCount} 項目",
                            onClick = { onOpenPath(path.id, title) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private data class PathWithProgress(val path: PathDetail, val progress: Map<String, CourseProgress>)

/** One 領域's 項目, in the order they are meant to be taken. */
@Composable
fun PathScreen(
    content: ContentClient,
    pathId: String,
    title: String,
    onOpenCourse: (courseId: String, title: String) -> Unit,
    onBack: () -> Unit,
) {
    val id = remember(pathId) { URLDecoder.decode(pathId, "UTF-8") }

    PushedScreen(title = URLDecoder.decode(title, "UTF-8"), onBack = onBack) { padding ->
        Loading(
            key = id,
            load = {
                // Progress is the lesser request: without it the list still
                // works, it just cannot say which 項目 are finished.
                val progress = runCatching { content.progress() }.getOrDefault(emptyList())
                PathWithProgress(content.pathDetail(id), progress.associateBy { it.courseId })
            },
            padding = padding,
        ) { (path, progress) ->
            ListOf(padding, heading = "上から順に学ぶように並んでいます。") {
                items(path.courses) { course ->
                    ReadableColumn(spacing = 0.dp) {
                        val done = progress[course.id]?.isCompleted == true
                        NavigationRow(
                            title = course.title,
                            detail = buildString {
                                append("${course.lessonCount} レッスン")
                                if (done) append("　読み終えました")
                            },
                            onClick = { onOpenCourse(course.id, course.title) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** One 項目's lessons — the same list the 理解マップ shows for a 要素. */
@Composable
fun CourseScreen(
    content: ContentClient,
    courseId: String,
    title: String,
    onOpenLesson: (String) -> Unit,
    onBack: () -> Unit,
) {
    val id = remember(courseId) { URLDecoder.decode(courseId, "UTF-8") }

    PushedScreen(title = URLDecoder.decode(title, "UTF-8"), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            ReadableColumn(spacing = 20.dp) {
                LessonList(content = content, courseId = id, onOpenLesson = onOpenLesson)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PushedScreen(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
        content = content,
    )
}

@Composable
private fun ListOf(padding: PaddingValues, heading: String, rows: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    ) {
        item {
            ReadableColumn(spacing = 0.dp) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
        rows()
    }
}

@Composable
private fun NavigationRow(
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
) {
    AdaptiveRow(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$title、$detail" }
            .padding(vertical = 14.dp),
    ) { flexible ->
        Column(modifier = flexible, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = titleStyle)
            Text(
                text = detail,
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

private sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Loaded<T>(val value: T) : LoadState<T>
    data class Failed(val message: String) : LoadState<Nothing>
}

/**
 * Loads once per [key], with a retry button on failure.
 *
 * Inline in a scrolling column when [padding] is null; filling the Scaffold
 * otherwise.
 */
@Composable
private fun <T> Loading(
    key: Any?,
    load: suspend () -> T,
    padding: PaddingValues? = null,
    content: @Composable (T) -> Unit,
) {
    var state by remember(key) { mutableStateOf<LoadState<T>>(LoadState.Loading) }
    var attempt by remember(key) { mutableIntStateOf(0) }

    LaunchedEffect(key, attempt) {
        state = LoadState.Loading
        state = try {
            LoadState.Loaded(load())
        } catch (e: ApiError) {
            LoadState.Failed(e.studentFacingMessage())
        }
    }

    when (val current = state) {
        is LoadState.Loaded -> content(current.value)

        LoadState.Loading -> if (padding != null) {
            Centered(padding) { CircularProgressIndicator() }
        } else {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }

        is LoadState.Failed -> {
            val failure = @Composable {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(current.message, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { attempt++ }) { Text("もう一度読み込む") }
                }
            }
            if (padding != null) Centered(padding) { failure() } else failure()
        }
    }
}
