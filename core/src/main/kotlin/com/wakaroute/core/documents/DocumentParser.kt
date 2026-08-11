package com.wakaroute.core.documents

/**
 * Turns authored HTML into [DocumentBlock]s.
 *
 * Deliberately small: it understands the tags the bundled documents and the
 * MANABU2 lesson bodies actually use, and nothing else. A general HTML parser
 * would invite authors to reach for tags the renderer cannot draw, and the
 * failure would be a paragraph silently missing from a 利用規約.
 *
 * **Unknown tags are dropped but their text is kept.** Losing a word of a legal
 * document, or a line of a maths explanation, because of an unexpected `<em>`
 * would be far worse than losing its styling.
 *
 * The one place that rule is suspended is `<svg>`: a diagram flattened to its
 * text nodes would come out as a row of loose axis labels, which reads as
 * corruption. Those are captured whole as [DocumentBlock.Figure].
 */
object DocumentParser {

    fun parse(html: String): List<DocumentBlock> = parseBlocks(html)

    private fun parseBlocks(html: String): List<DocumentBlock> {
        val blocks = mutableListOf<DocumentBlock>()

        for (match in BLOCK_PATTERN.findAll(html)) {
            val tag = match.groupValues[1].lowercase()
            // `<svg …/>` and paired tags come through different groups.
            val inner = match.groupValues.getOrElse(2) { "" }

            when (tag) {
                "h1" -> blocks += DocumentBlock.Title(inlineSpans(inner))
                "h2", "h3", "h4" -> blocks += DocumentBlock.Heading(inlineSpans(inner))
                "p" -> blocks += DocumentBlock.Paragraph(inlineSpans(inner))
                "ul" -> listItems(inner)?.let { blocks += DocumentBlock.BulletList(it) }
                "ol" -> listItems(inner)?.let { blocks += DocumentBlock.NumberedList(it) }
                "table" -> table(inner)?.let { blocks += it }
                "details" -> blocks += collapsible(inner)
                "svg" -> blocks += figure(match.value)
            }
        }

        return blocks.filterNot { it.isBlank() }
    }

    private fun listItems(inner: String): List<List<InlineSpan>>? =
        LIST_ITEM_PATTERN.findAll(inner)
            .map { inlineSpans(it.groupValues[1]) }
            .filter { it.isNotEmpty() }
            .toList()
            .takeIf { it.isNotEmpty() }

    /**
     * Header cells come from `<th>` wherever they are.
     *
     * Matching on the tag rather than on "the first row" because a table whose
     * header is inside `<thead>` and one whose header is the first `<tr>` mean
     * the same thing to a reader, and only one of them is what the authoring
     * tool happened to emit.
     */
    private fun table(inner: String): DocumentBlock.Table? {
        val header = mutableListOf<List<InlineSpan>>()
        val rows = mutableListOf<List<List<InlineSpan>>>()

        for (row in ROW_PATTERN.findAll(inner)) {
            val headerCells = CELL_PATTERN.findAll(row.groupValues[1])
                .filter { it.groupValues[1].lowercase() == "th" }
                .map { inlineSpans(it.groupValues[2]) }
                .toList()

            val bodyCells = CELL_PATTERN.findAll(row.groupValues[1])
                .filter { it.groupValues[1].lowercase() == "td" }
                .map { inlineSpans(it.groupValues[2]) }
                .toList()

            if (headerCells.isNotEmpty() && header.isEmpty()) header += headerCells
            if (bodyCells.isNotEmpty()) rows += bodyCells
        }

        if (header.isEmpty() && rows.isEmpty()) return null
        return DocumentBlock.Table(header = header, rows = rows)
    }

    private fun collapsible(inner: String): DocumentBlock.Collapsible {
        val summaryMatch = SUMMARY_PATTERN.find(inner)
        val summary = summaryMatch?.groupValues?.get(1).orEmpty()
        val rest = summaryMatch?.let { inner.removeRange(it.range) } ?: inner

        val body = parseBlocks(rest).ifEmpty {
            // Authors write the answer as bare text inside <details>, with no
            // wrapping <p>. Dropping it would hide the answer permanently.
            listOfNotNull(inlineSpans(rest).takeIf { it.isNotEmpty() }?.let(DocumentBlock::Paragraph))
        }

        return DocumentBlock.Collapsible(summary = inlineSpans(summary), body = body)
    }

    private fun figure(svg: String): DocumentBlock.Figure = DocumentBlock.Figure(
        svg = svg,
        title = SVG_TITLE_PATTERN.find(svg)?.groupValues?.get(1)?.let(::plainTextOf),
        description = SVG_DESC_PATTERN.find(svg)?.groupValues?.get(1)?.let(::plainTextOf),
    )

    /**
     * Splits a fragment into emphasised and plain runs.
     *
     * `<br>` becomes a newline rather than a space: it separates the lines of
     * the operator's address, and joining them would run the company name into
     * its email.
     */
    private fun inlineSpans(fragment: String): List<InlineSpan> {
        val spans = mutableListOf<InlineSpan>()
        var cursor = 0

        for (match in STRONG_PATTERN.findAll(fragment)) {
            appendText(spans, fragment.substring(cursor, match.range.first), strong = false)
            appendText(spans, match.groupValues[1], strong = true)
            cursor = match.range.last + 1
        }
        appendText(spans, fragment.substring(cursor), strong = false)

        return spans
    }

    private fun appendText(into: MutableList<InlineSpan>, raw: String, strong: Boolean) {
        val text = plainTextOf(raw)
        if (text.isNotEmpty()) into.add(InlineSpan(text, strong))
    }

    private fun plainTextOf(raw: String): String = raw
        // Parked behind a sentinel first. A `<br>` turned straight into a
        // newline is indistinguishable from the source's own line wrapping,
        // and the next step — which exists to undo that wrapping — would
        // collapse it back into a space.
        .replace(BREAK_PATTERN, LINE_BREAK_SENTINEL)
        .replace(TAG_PATTERN, "")
        .let(::decodeEntities)
        // After the tags are gone, because the maths arrives wrapped in
        // <span class="math"> and it is the delimiters inside that matter.
        .let(MathNotation::toReadableText)
        .replace(COLLAPSIBLE_SPACE, " ")
        .replace(LINE_BREAK_SENTINEL, "\n")
        .trim()

    private fun decodeEntities(text: String): String = text
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        // Last, so a literal "&amp;lt;" does not become "<".
        .replace("&amp;", "&")

    private fun DocumentBlock.isBlank(): Boolean = when (this) {
        is DocumentBlock.Title -> spans.plainText.isBlank()
        is DocumentBlock.Heading -> spans.plainText.isBlank()
        is DocumentBlock.Paragraph -> spans.plainText.isBlank()
        is DocumentBlock.BulletList -> items.isEmpty()
        is DocumentBlock.NumberedList -> items.isEmpty()
        is DocumentBlock.Table -> header.isEmpty() && rows.isEmpty()
        is DocumentBlock.Collapsible -> summary.plainText.isBlank() && body.isEmpty()
        is DocumentBlock.Figure -> svg.isBlank()
    }

    /**
     * Top-level blocks only.
     *
     * `svg` is matched before the paired tags so its inner `<text>`, `<title>`
     * and `<desc>` are never mistaken for content blocks. `details` likewise
     * comes before `p` so its body is parsed as part of the collapsible rather
     * than escaping alongside it.
     */
    private val BLOCK_PATTERN = Regex(
        "<(svg|details|h1|h2|h3|h4|p|ul|ol|table)\\b[^>]*>(.*?)</\\1>",
        RegexOption.DOT_MATCHES_ALL,
    )

    private val LIST_ITEM_PATTERN = Regex("<li\\b[^>]*>(.*?)</li>", RegexOption.DOT_MATCHES_ALL)
    private val ROW_PATTERN = Regex("<tr\\b[^>]*>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL)
    private val CELL_PATTERN = Regex("<(th|td)\\b[^>]*>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL)
    private val SUMMARY_PATTERN = Regex("<summary\\b[^>]*>(.*?)</summary>", RegexOption.DOT_MATCHES_ALL)
    private val SVG_TITLE_PATTERN = Regex("<title\\b[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
    private val SVG_DESC_PATTERN = Regex("<desc\\b[^>]*>(.*?)</desc>", RegexOption.DOT_MATCHES_ALL)
    private val STRONG_PATTERN = Regex("<(?:strong|b)\\b[^>]*>(.*?)</(?:strong|b)>", RegexOption.DOT_MATCHES_ALL)
    private val BREAK_PATTERN = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val TAG_PATTERN = Regex("<[^>]+>")
    private val COLLAPSIBLE_SPACE = Regex("[ \\t]*\\n[ \\t\\n]*|[ \\t]{2,}")

    /** A character the authored documents will never contain. */
    private const val LINE_BREAK_SENTINEL = " "
}
