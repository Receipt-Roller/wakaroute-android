package com.wakaroute.app.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wakaroute.app.ui.design.AdaptiveRow
import com.wakaroute.app.ui.design.ComingSoonChip
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.core.map.ElementId
import com.wakaroute.core.map.LearningElement
import com.wakaroute.core.map.MasteryLevel
import com.wakaroute.core.map.SubjectMapState
import com.wakaroute.app.data.UnderstandingMapState
import com.wakaroute.core.map.isMeasurable
import java.net.URLDecoder

/**
 * One 要素: what it needs, and what needs it.
 *
 * The levels are shown as a scale, and the ones above 基本を解ける are marked
 * **準備中** rather than 未達成. That distinction is the whole reason this screen
 * is careful: nobody has written the 確認テスト those levels would need, so a
 * student has not failed them — there is nothing there to fail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElementScreen(
    mapState: UnderstandingMapState,
    subjectName: String,
    domainCode: String,
    elementId: String,
    onBack: () -> Unit,
) {
    val subject = remember(subjectName) { subjectNamed(subjectName) }
    val state = remember(subject) { subject?.let(mapState::state) }
    val available = state as? SubjectMapState.Available

    val decodedId = remember(elementId) { ElementId(URLDecoder.decode(elementId, "UTF-8")) }
    val element = available?.subject?.element(decodedId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(element?.name ?: "項目") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        if (available == null || element == null) {
            MissingContent(Modifier.padding(padding))
            return@Scaffold
        }

        val prerequisites = element.prerequisiteIds.mapNotNull(available.subject::element)
        val dependants = available.subject.elements.filter { decodedId in it.prerequisiteIds }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ReadableColumn(spacing = 20.dp) {
                element.grade?.let {
                    Text(
                        text = "中$it で学ぶ項目です。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                MasteryScale(current = available.progress.recordOrEmpty[element.id])

                ElementList(
                    heading = "この項目の前提",
                    empty = "前提はありません。ここから始められます。",
                    // 「B が固まっていないと A は身につかない」 — not merely the
                    // order the two are taught in.
                    caption = "つぎの項目が身についていないと、この項目はむずかしくなります。",
                    elements = prerequisites,
                )

                ElementList(
                    heading = "この項目が前提になっているもの",
                    empty = "この項目を前提にしている項目は、いまのところありません。",
                    caption = "この項目が固まると、つぎに進めるようになります。",
                    elements = dependants,
                )

                Text(
                    text = "レッスンとクイズは準備中です。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun MasteryScale(current: MasteryLevel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "理解のレベル", style = MaterialTheme.typography.titleMedium)

        for (level in MasteryLevel.entries) {
            val isCurrent = level == current

            Surface(
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AdaptiveRow(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) { flexible ->
                    Text(
                        text = "${level.level}　${level.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = flexible,
                    )

                    // 準備中, never 未達成. There is no 確認テスト behind these
                    // levels, so nothing has been failed.
                    if (!level.isMeasurable) ComingSoonChip()
                    if (isCurrent) {
                        Text(text = "いまここ", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        Text(
            text = "レベル3以上は確認テストが必要です。テストはまだ作られていないため、準備中としています。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ElementList(
    heading: String,
    empty: String,
    caption: String,
    elements: List<LearningElement>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = heading, style = MaterialTheme.typography.titleMedium)

        if (elements.isEmpty()) {
            Text(
                text = empty,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        for (element in elements) {
            Text(text = "・${element.name}", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
