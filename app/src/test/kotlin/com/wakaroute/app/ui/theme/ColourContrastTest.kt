package com.wakaroute.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether the palette is actually readable, in both themes.
 *
 * This exists because the alternative is looking at it. A screenshot tells you
 * a screen "looks fine" on the display you happen to own, at the brightness you
 * happen to use — and 中学生 read this on cheap hand-me-down phones, often in a
 * bright room, sometimes at night with the screen dimmed. The ratio is the part
 * that does not depend on any of that.
 *
 * WCAG AA: **4.5:1** for body text, **3:1** for outlines and large text. The
 * pairs below are the ones the app actually draws; a colour changed in
 * [LightScheme] or [DarkScheme] without checking it fails here rather than in
 * front of a student.
 *
 * Dark mode is the reason this is worth automating. It is the theme nobody
 * develops in, and the one where a colour lifted from the light scheme lands on
 * a near-black background and disappears.
 */
class ColourContrastTest {

    @Test
    fun `dark theme text is readable`() = assertReadable(DarkScheme, "dark")

    @Test
    fun `light theme text is readable`() = assertReadable(LightScheme, "light")

    @Test
    fun `the preparing card stays readable behind its tint`() {
        // 準備中 on the home screen draws surfaceVariant at 40% over surface, so
        // neither colour on its own is what the text actually sits on.
        for ((scheme, name) in listOf(DarkScheme to "dark", LightScheme to "light")) {
            val card = scheme.surfaceVariant.over(scheme.surface, alpha = 0.4f)

            assertMeets(scheme.onSurface, card, 4.5, "$name: body on the 準備中 card")
            assertMeets(scheme.onSurfaceVariant, card, 4.5, "$name: secondary text on the 準備中 card")
        }
    }

    private fun assertReadable(scheme: ColorScheme, name: String) {
        assertMeets(scheme.onSurface, scheme.surface, 4.5, "$name: body text")
        assertMeets(scheme.onSurfaceVariant, scheme.surface, 4.5, "$name: secondary text")
        assertMeets(scheme.onSurfaceVariant, scheme.surfaceVariant, 4.5, "$name: text on a tinted card")
        assertMeets(scheme.primary, scheme.surface, 4.5, "$name: primary text and links")
        assertMeets(scheme.onPrimary, scheme.primary, 4.5, "$name: label on a filled button")
        assertMeets(
            scheme.onSecondaryContainer,
            scheme.secondaryContainer,
            4.5,
            "$name: the selected navigation pill",
        )
        // 「学習記録を削除する」 and every failure message.
        assertMeets(scheme.error, scheme.surface, 4.5, "$name: error text")
        assertMeets(scheme.tertiary, scheme.surface, 4.5, "$name: the 準備中 accent")

        // Outlines are not text; 3:1 is the bar for a UI boundary.
        assertMeets(scheme.outline, scheme.surface, 3.0, "$name: outlines and dividers")
    }

    private fun assertMeets(foreground: Color, background: Color, required: Double, what: String) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "$what is %.2f:1, below the %.1f:1 it needs".format(ratio, required),
            ratio >= required,
        )
    }

    /** WCAG 2.1 relative luminance. */
    private fun Color.relativeLuminance(): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val la = a.relativeLuminance()
        val lb = b.relativeLuminance()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** This colour composited over [background] at [alpha], as the screen shows it. */
    private fun Color.over(background: Color, alpha: Float) = Color(
        red = red * alpha + background.red * (1 - alpha),
        green = green * alpha + background.green * (1 - alpha),
        blue = blue * alpha + background.blue * (1 - alpha),
    )
}
