package com.wakaroute.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wakaroute.app.ui.design.ReadableColumn
import com.wakaroute.app.ui.theme.WakaRouteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The readable-measure cap, on a screen wide enough for it to matter.
 *
 * Long-form Japanese set across the full width of a tablet is genuinely hard to
 * read — the eye loses the line on the way back. [ReadableColumn] exists to
 * stop that, and for months it did not: `fillMaxWidth()` was applied *before*
 * `widthIn(max = …)`, which pins the minimum width to the parent's and leaves
 * the maximum with nothing left to constrain.
 *
 * **On a phone both orders look identical**, because the screen is narrower
 * than the cap and it never binds. So these force a 900dp parent rather than
 * trusting whatever device they run on — they have to fail on a phone too, not
 * only on the tablet nobody has.
 *
 * Both assertions are on **position and width in dp**, not on how it looks: a
 * screenshot of this on a phone shows nothing either way.
 */
@RunWith(AndroidJUnit4::class)
class ReadableColumnTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setContent() {
        rule.setContent {
            WakaRouteTheme {
                // `requiredWidth`, not `width`: the latter is clamped by the
                // incoming constraints, so on a 384dp phone it quietly produces
                // a 384dp box and the cap never binds — the test would then pass
                // for the same reason the bug was invisible.
                Box(Modifier.requiredWidth(900.dp)) {
                    ReadableColumn {
                        Text(LONG_PARAGRAPH)
                        Text(SHORT_LINE)
                    }
                }
            }
        }
    }

    @Test
    fun longTextIsCappedRatherThanRunningTheFullWidth() {
        setContent()

        // The paragraph is wider than the cap, so it fills whatever column it is
        // given and its measured width *is* the column's. Uncapped: 900dp.
        rule.onNodeWithText(LONG_PARAGRAPH).assertWidthIsEqualTo(640.dp)
    }

    @Test
    fun shortLinesStartAtTheSameEdgeAsLongOnes() {
        // The other half of the requirement, and why fillMaxWidth is there at
        // all: without it the column shrinks to its widest child, so 「8項目」
        // ends up centred on the screen while the paragraph under it is
        // left-aligned.
        //
        // Compared against each other rather than against an absolute position:
        // the 900dp box is itself centred in a narrower screen, so the root
        // coordinates are negative and say nothing on their own.
        setContent()

        val paragraphLeft = rule.onNodeWithText(LONG_PARAGRAPH).getUnclippedBoundsInRoot().left
        rule.onNodeWithText(SHORT_LINE).assertLeftPositionInRootIsEqualTo(paragraphLeft)
    }

    private companion object {
        /** Wider than 640dp at any sane text size, so it fills the column. */
        const val LONG_PARAGRAPH =
            "ワカルートは、入試の合否を予想するものではありません。" +
                "志望校を決めるときは、学校の先生や家の人と相談してください。"

        const val SHORT_LINE = "8項目"
    }
}
