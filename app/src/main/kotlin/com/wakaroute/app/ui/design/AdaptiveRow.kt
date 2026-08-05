package com.wakaroute.app.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wakaroute.app.ui.theme.isLargeFontScale

/**
 * A row that becomes a column once the text is large enough to need it.
 *
 * §8, and the reason is specific to Japanese: an icon and a chevron keep their
 * width whatever the font scale, so at the largest sizes the label between them
 * is squeezed to a couple of characters and 「手前でつまずき」 wraps one character
 * per line. Stacking gives the text the whole width instead.
 *
 * The switch is on the font scale, never on the device. `if (isTablet)` breaks
 * in split screen and freeform windows — the failure §8 calls out by name.
 *
 * [content] receives a `flexible` modifier for the child that should absorb the
 * leftover width. Passing it is not optional dressing: without it the label
 * takes whatever it likes and the chip beside it wraps mid-word — 「これ／から」 —
 * which is the same one-or-two-characters-per-line failure in a different place.
 */
@Composable
fun AdaptiveRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 12.dp,
    verticalSpacing: Dp = 8.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable (flexible: Modifier) -> Unit,
) {
    if (isLargeFontScale()) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            horizontalAlignment = Alignment.Start,
        ) {
            // Stacked, everything already has the full width, so the flexible
            // child needs nothing extra.
            content(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            verticalAlignment = verticalAlignment,
        ) {
            content(Modifier.weight(1f))
        }
    }
}

/**
 * Caps body text at a comfortable measure and centres it.
 *
 * Long-form Japanese set across the full width of a tablet is genuinely hard to
 * read: the eye loses the line on the way back. Decided by available width, not
 * by device type, for the same reason as above.
 */
@Composable
fun ReadableColumn(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Column(
            // fillMaxWidth as well as the cap. Without it the column shrinks to
            // its widest child, so a short line like 「8項目」 ends up centred on
            // the screen while the paragraph under it is left-aligned.
            modifier = Modifier.fillMaxWidth().widthIn(max = READABLE_WIDTH),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = { content() },
        )
    }
}

/** About 40 Japanese characters at the default text size. */
private val READABLE_WIDTH = 640.dp
