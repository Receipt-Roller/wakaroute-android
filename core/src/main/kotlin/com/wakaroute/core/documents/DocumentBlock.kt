package com.wakaroute.core.documents

/**
 * A document rendered as structured blocks rather than as a web page.
 *
 * §3 of the 開発ガイド rules out WebView, and the reason is not purity: a 中学生
 * handed to a browser is left to find their own way back, and with no signal
 * cannot read the 利用規約 at all. Blocks also inherit the app's font scaling,
 * so a student who has turned text size up gets it here too — which an embedded
 * page would ignore.
 */
sealed interface DocumentBlock {
    data class Title(val spans: List<InlineSpan>) : DocumentBlock
    data class Heading(val spans: List<InlineSpan>) : DocumentBlock
    data class Paragraph(val spans: List<InlineSpan>) : DocumentBlock
    data class BulletList(val items: List<List<InlineSpan>>) : DocumentBlock
}

/**
 * A run of text with or without emphasis.
 *
 * Emphasis carries meaning in these documents — 「合否を予想するものではありません」
 * is bold because it is the sentence a reader must not miss — so it is modelled
 * rather than flattened away.
 */
data class InlineSpan(val text: String, val strong: Boolean = false)

val List<InlineSpan>.plainText: String get() = joinToString("") { it.text }
