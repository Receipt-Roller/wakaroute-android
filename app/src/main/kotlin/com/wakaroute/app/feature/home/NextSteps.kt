package com.wakaroute.app.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SubdirectoryArrowLeft
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wakaroute.app.ui.design.AdaptiveRow
import com.wakaroute.app.ui.design.StatusChip
import com.wakaroute.app.ui.design.StatusTone
import com.wakaroute.core.map.NextStep

/**
 * 「つぎにやること」 — the product's core idea on one screen.
 *
 * The ordering is [com.wakaroute.core.map.HomeDigest]'s; this is only how it
 * reads. §3's table, and the reason it is a table rather than a sentence with a
 * variable in it: 未着手 and 不合格あり look identical in the model and mean
 * opposite things to a student.
 *
 * **Only 不合格あり is drawn as a problem.** A student who has not begun sees an
 * invitation, never a correction.
 */
@Composable
fun NextStepsSection(steps: List<NextStep>, onOpenElement: (NextStep) -> Unit) {
    if (steps.isEmpty()) return

    Text(text = "つぎにやること", style = MaterialTheme.typography.titleMedium)

    for (step in steps) {
        NextStepRow(step) { onOpenElement(step) }
    }
}

@Composable
private fun NextStepRow(step: NextStep, onClick: () -> Unit) {
    val (icon, tone, label) = when (step.tone) {
        NextStep.Tone.NotStarted -> Triple(Icons.Filled.Flag, StatusTone.Neutral, "これから")
        NextStep.Tone.InProgress -> Triple(Icons.Filled.PlayArrow, StatusTone.Progress, "学習中")
        // The only tone that signals a problem, and it rests on evidence: a
        // quiz here was sat and missed.
        NextStep.Tone.Stumbling ->
            Triple(Icons.Filled.SubdirectoryArrowLeft, StatusTone.Attention, "手前でつまずき")
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${step.element.name}、$label。${step.message}"
            },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AdaptiveRow(modifier = Modifier.fillMaxWidth()) { flexible ->
                Text(
                    text = step.element.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = flexible,
                )
                // Symbol and word together — §8. The colour is the third signal,
                // never the only one.
                StatusChip(label = label, icon = icon, tone = tone)
            }

            Text(
                text = step.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
