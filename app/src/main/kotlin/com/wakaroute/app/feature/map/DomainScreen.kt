package com.wakaroute.app.feature.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wakaroute.app.ui.design.AdaptiveRow
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.core.map.LearningElement
import com.wakaroute.core.map.MasteryLevel
import com.wakaroute.core.map.SchoolSubject
import com.wakaroute.core.map.SubjectMapState
import com.wakaroute.app.data.UnderstandingMapState

/** The 要素 inside one 領域, in the order they are normally taught. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainScreen(
    mapState: UnderstandingMapState,
    subjectName: String,
    domainCode: String,
    onOpenElement: (SchoolSubject, String, String) -> Unit,
    onBack: () -> Unit,
) {
    val subject = remember(subjectName) { subjectNamed(subjectName) }
    val state = remember(subject) { subject?.let(mapState::state) }
    val available = state as? SubjectMapState.Available
    val domain = available?.subject?.domain(domainCode)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(domain?.name ?: "理解マップ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        if (available == null || domain == null || subject == null) {
            // Reachable by a stale deep link or a graph that failed validation
            // between screens. Said as a fact rather than as an error the
            // student could have caused.
            MissingContent(Modifier.padding(padding))
            return@Scaffold
        }

        val elements = available.subject.elementsIn(domain.code)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        ) {
            item {
                ReadableColumn(spacing = 8.dp) {
                    Text(
                        text = "${elements.size}項目",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }

            items(elements) { element ->
                ReadableColumn(spacing = 0.dp) {
                    ElementRow(
                        element = element,
                        level = available.progress.recordOrEmpty[element.id],
                        onClick = { onOpenElement(subject, domain.code, element.id.value) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ElementRow(element: LearningElement, level: MasteryLevel, onClick: () -> Unit) {
    // One sentence, for the same reason as DomainRow: a clickable row with no
    // semantics of its own announces nothing, and the pieces below would be
    // read as three unrelated fragments.
    val announcement = buildString {
        append(element.name)
        element.grade?.let { append("、中$it") }
        append("、${level.label}")
        if (element.prerequisiteIds.isNotEmpty()) append("、前提${element.prerequisiteIds.size}項目")
    }

    AdaptiveRow(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = announcement }
            .padding(vertical = 14.dp),
    ) { flexible ->
        Column(
            modifier = flexible,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = element.name, style = MaterialTheme.typography.titleMedium)

            Text(
                text = buildString {
                    element.grade?.let { append("中$it　") }
                    // The level, in the same words as the web map. On Phase 1
                    // this is まだ for everything, which is a fact about this
                    // build and is explained on the previous screen.
                    append(level.label)
                    if (element.prerequisiteIds.isNotEmpty()) {
                        append("　前提 ${element.prerequisiteIds.size}")
                    }
                },
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

@Composable
internal fun MissingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "表示できませんでした", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "この項目は、いまのアプリでは表示できません。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun subjectNamed(name: String): SchoolSubject? =
    SchoolSubject.entries.firstOrNull { it.name == name }
