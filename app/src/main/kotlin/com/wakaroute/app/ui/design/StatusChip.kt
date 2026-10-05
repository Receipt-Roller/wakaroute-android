package com.wakaroute.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SubdirectoryArrowLeft
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.wakaroute.core.map.DomainProgress

/**
 * A state, shown as a symbol **and** a word.
 *
 * §8: colour alone must never carry 正誤・状態・選択. Every chip therefore has an
 * icon and a label; the colour is the third signal, not the only one. A student
 * with a colour vision difference, or one reading in sunlight, gets the same
 * information.
 */
enum class StatusTone { Neutral, Progress, Attention, Pending }

@Composable
fun StatusChip(
    label: String,
    icon: ImageVector,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val colors = tone.colors()
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.container)
            // Outlined as well as filled. A neutral chip sits on cards that are
            // themselves surfaceVariant, and without the border it dissolves
            // into the card — leaving colour as the only signal, which is the
            // thing §8 forbids.
            .border(1.dp, colors.content.copy(alpha = 0.35f), shape)
            .padding(PaddingValues(horizontal = 10.dp, vertical = 6.dp)),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            // The label right beside it already says this. Announcing both
            // makes TalkBack read every state twice.
            contentDescription = null,
            tint = colors.content,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.content,
            // A status label is short and must not be broken across lines.
            // 「これから」 split as 「これ／から」 is unreadable at a glance, which is
            // the entire job of a chip.
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The chip for a 領域's standing, so the mapping lives in exactly one place. */
@Composable
fun StandingChip(standing: DomainProgress.Standing, modifier: Modifier = Modifier) {
    val (icon, tone) = when (standing) {
        DomainProgress.Standing.NotStarted -> Icons.Filled.Flag to StatusTone.Neutral
        DomainProgress.Standing.InProgress -> Icons.Filled.PlayArrow to StatusTone.Progress
        // The only standing drawn as a problem, because it is the only one that
        // rests on evidence that something went wrong.
        DomainProgress.Standing.Stumbling -> Icons.Filled.SubdirectoryArrowLeft to StatusTone.Attention
        DomainProgress.Standing.Strong -> Icons.Filled.TaskAlt to StatusTone.Progress
    }

    StatusChip(label = standing.label, icon = icon, tone = tone, modifier = modifier)
}

private data class ChipColors(val container: Color, val content: Color)

@Composable
private fun StatusTone.colors(): ChipColors = when (this) {
    StatusTone.Neutral -> ChipColors(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.onSurfaceVariant,
    )

    StatusTone.Progress -> ChipColors(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.onPrimaryContainer,
    )

    StatusTone.Attention -> ChipColors(
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer,
    )

    StatusTone.Pending -> ChipColors(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
