package com.wakaroute.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Japanese-aware defaults.
 *
 * [LineBreak.Paragraph] applies the 禁則処理 rules — 「。」 and 「）」 do not begin a
 * line, 「（」 does not end one. Compose's default strategy is tuned for
 * whitespace-separated text and produces breaks a Japanese reader reads as
 * mistakes.
 *
 * Line heights are set in sp rather than left to the default so they scale with
 * the student's own text size. §8: a layout must not break at large sizes, and
 * a fixed leading is the usual reason it does.
 */
private val JapaneseText = TextStyle(
    lineBreak = LineBreak.Paragraph,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

private val base = Typography()

val WakaRouteTypography = Typography(
    displaySmall = base.displaySmall.merge(JapaneseText),
    headlineLarge = base.headlineLarge.merge(JapaneseText),
    headlineMedium = base.headlineMedium.merge(JapaneseText),
    headlineSmall = base.headlineSmall.merge(JapaneseText),
    titleLarge = base.titleLarge.merge(JapaneseText),
    titleMedium = base.titleMedium.merge(JapaneseText),
    titleSmall = base.titleSmall.merge(JapaneseText),
    bodyLarge = base.bodyLarge.merge(JapaneseText).copy(lineHeight = 26.sp),
    bodyMedium = base.bodyMedium.merge(JapaneseText).copy(lineHeight = 22.sp),
    bodySmall = base.bodySmall.merge(JapaneseText).copy(lineHeight = 18.sp),
    labelLarge = base.labelLarge.merge(JapaneseText),
    labelMedium = base.labelMedium.merge(JapaneseText),
    labelSmall = base.labelSmall.merge(JapaneseText),
)
