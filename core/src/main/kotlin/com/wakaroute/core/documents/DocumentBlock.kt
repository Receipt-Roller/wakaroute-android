package com.wakaroute.core.documents

/**
 * A document rendered as structured blocks rather than as a web page.
 *
 * §3 of the 開発ガイド rules out WebView, and the reason is not purity: a 中学生
 * handed to a browser is left to find their own way back, and with no signal
 * cannot read the 利用規約 at all. Blocks also inherit the app's font scaling,
 * so a student who has turned text size up gets it here too — which an embedded
 * page would ignore.
 *
 * The same model serves lesson bodies, which is why it covers more than the
 * legal documents need. Lesson HTML from MANABU2 uses tables heavily (58 in a
 * single 数学 course), numbered steps, collapsible answers, and inline SVG
 * diagrams. **A parser that only knew headings and paragraphs would drop all of
 * it without erroring** — 588 table cells of maths, gone, on a screen that
 * still looked fine.
 */
sealed interface DocumentBlock {
    data class Title(val spans: List<InlineSpan>) : DocumentBlock
    data class Heading(val spans: List<InlineSpan>) : DocumentBlock
    data class Paragraph(val spans: List<InlineSpan>) : DocumentBlock
    data class BulletList(val items: List<List<InlineSpan>>) : DocumentBlock

    /**
     * `<ol>`. Kept apart from [BulletList] because the numbering is the
     * content: lesson bodies use these for worked steps, and 「①のあと②」 stops
     * meaning anything if they are drawn as dots.
     */
    data class NumberedList(val items: List<List<InlineSpan>>) : DocumentBlock

    /**
     * A table, header row separated.
     *
     * Rows are lists of cells; the renderer decides how to lay them out at the
     * student's text size. Flattening a table to prose would lose the thing a
     * table is for — 「気温／基準の0／負の数が表す側」 only means something as a
     * grid.
     */
    data class Table(
        val header: List<List<InlineSpan>>,
        val rows: List<List<List<InlineSpan>>>,
    ) : DocumentBlock

    /**
     * `<details>` — a question with its answer hidden until asked for.
     *
     * Collapsed by default, deliberately: the author wrote 「確認1：…」 expecting
     * the student to try before reading. Expanding it for them removes the only
     * part of the lesson where they do the work.
     */
    data class Collapsible(
        val summary: List<InlineSpan>,
        val body: List<DocumentBlock>,
    ) : DocumentBlock

    /**
     * An inline SVG diagram.
     *
     * [description] is the author's own `<desc>`, written in Japanese and
     * describing what the diagram shows. It is what a screen reader must be
     * given, and it is also a usable fallback anywhere the drawing itself
     * cannot be rendered — the content survives either way.
     */
    /**
     * A widget the lesson drives with its own JavaScript.
     *
     * One lesson uses this today — 一次方程式 の「両辺に同じ数を足す・引く」, a
     * balance scale whose beam tilts when the student takes 3 from one side.
     * Rendered natively it loses the point: the diagram sits still and the
     * buttons become text that does nothing.
     *
     * [html] is the authored fragment, verbatim. It is executed in a WebView
     * that can reach nothing — see the renderer for what that means and why.
     */
    data class Interactive(val html: String) : DocumentBlock

    data class Figure(
        val svg: String,
        val title: String?,
        val description: String?,
    ) : DocumentBlock
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
