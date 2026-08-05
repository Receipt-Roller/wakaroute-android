package com.wakaroute.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.wakaroute.app.ui.theme.isLargeFontScale
import com.wakaroute.core.documents.DocumentBlock
import com.wakaroute.core.documents.InlineSpan
import com.wakaroute.core.documents.plainText

/**
 * Draws one parsed block.
 *
 * Shared by the bundled documents and the MANABU2 lesson bodies, so the two
 * cannot drift — a lesson and the 利用規約 should not render a table two
 * different ways.
 */
@Composable
fun DocumentBlockView(block: DocumentBlock, modifier: Modifier = Modifier) {
    when (block) {
        is DocumentBlock.Title -> Text(
            text = block.spans.annotated(),
            style = MaterialTheme.typography.headlineMedium,
            modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
        )

        is DocumentBlock.Heading -> Text(
            text = block.spans.annotated(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier.padding(top = 12.dp),
        )

        is DocumentBlock.Paragraph -> Text(
            text = block.spans.annotated(),
            style = MaterialTheme.typography.bodyLarge,
            modifier = modifier,
        )

        is DocumentBlock.BulletList -> MarkedList(block.items, modifier) { "・" }

        // The numbering is the content, not decoration: lesson bodies use these
        // for worked steps, and 「①のあと②」 means nothing as a row of dots.
        is DocumentBlock.NumberedList -> MarkedList(block.items, modifier) { "${it + 1}." }

        is DocumentBlock.Table -> TableView(block, modifier)
        is DocumentBlock.Collapsible -> CollapsibleView(block, modifier)
        is DocumentBlock.Figure -> FigureView(block, modifier)
    }
}

@Composable
private fun MarkedList(
    items: List<List<InlineSpan>>,
    modifier: Modifier,
    marker: (Int) -> String,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // A literal marker, not a drawn shape. TalkBack skips a
                // decorative dot and the student loses the list structure.
                Text(marker(index), style = MaterialTheme.typography.bodyLarge)
                Text(text = item.annotated(), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * A table, as a grid — or as labelled rows when the text is large.
 *
 * 「気温／基準の0／負の数が表す側」 only means something as a grid, so it stays one
 * while it fits. At the largest text sizes three columns of Japanese wrap to a
 * character or two each, which is unreadable; there each row becomes its own
 * block with the header repeated as a label. Same information, and it is the
 * header that makes the cell make sense.
 */
@Composable
private fun TableView(table: DocumentBlock.Table, modifier: Modifier) {
    if (isLargeFontScale() && table.header.isNotEmpty()) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            for (row in table.rows) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEachIndexed { index, cell ->
                        table.header.getOrNull(index)?.let {
                            Text(
                                text = it.plainText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(text = cell.annotated(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                HorizontalDivider()
            }
        }
        return
    }

    Column(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
    ) {
        if (table.header.isNotEmpty()) {
            Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                for (cell in table.header) {
                    TableCell(cell, MaterialTheme.typography.labelLarge)
                }
            }
        }
        for (row in table.rows) {
            HorizontalDivider()
            Row {
                for (cell in row) {
                    TableCell(cell, MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun TableCell(cell: List<InlineSpan>, style: androidx.compose.ui.text.TextStyle) {
    Text(
        text = cell.annotated(),
        style = style,
        // A floor and a ceiling: narrow cells stay tappable-wide enough to
        // scan, wide ones do not push the whole table off screen.
        modifier = Modifier.widthIn(min = 96.dp, max = 220.dp).padding(10.dp),
    )
}

/**
 * A question with its answer hidden until asked for.
 *
 * **Collapsed by default, deliberately.** The author wrote 「確認1：…」 expecting
 * the student to try before reading, and expanding it for them removes the only
 * part of the lesson where the student does the work.
 */
@Composable
private fun CollapsibleView(block: DocumentBlock.Collapsible, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val summary = block.summary.plainText

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AdaptiveRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (expanded) "$summary、答えを隠す" else "$summary、答えを見る"
                    },
            ) { flexible ->
                Text(
                    text = block.summary.annotated(),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = flexible,
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            }

            if (expanded) {
                for (inner in block.body) {
                    DocumentBlockView(inner)
                }
            }
        }
    }
}

/**
 * A diagram.
 *
 * **The drawing itself is not rendered yet.** What is shown is the author's own
 * `<desc>` — written in Japanese, describing what the figure shows — which is
 * what a screen reader would be given in any case. That is a real reduction for
 * a sighted student and it is marked as one rather than hidden.
 *
 * Rendering the SVG needs a decision nobody has made: §3 rules out WebView, and
 * hand-writing an SVG subset renderer risks drawing a **subtly wrong number
 * line** in a maths lesson, which is worse than describing it. Raised in AB.
 */
@Composable
private fun FigureView(figure: DocumentBlock.Figure, modifier: Modifier) {
    val description = figure.description ?: figure.title ?: return

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = figure.title?.let { "図: $it" } ?: "図",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Keeps the authored emphasis, which in these documents carries meaning. */
fun List<InlineSpan>.annotated() = buildAnnotatedString {
    for (span in this@annotated) {
        if (span.strong) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
        } else {
            append(span.text)
        }
    }
}
