package com.wakaroute.core.documents

/**
 * Finds the parts of a lesson that are a working widget rather than prose.
 *
 * Authored as a container followed by its script:
 *
 * ```html
 * <div> <svg id="…">…</svg> <p id="bal-msg">…</p> <p><button onclick="…">…</button></p> </div>
 * <script> … </script>
 * ```
 *
 * The two halves are useless apart — the script only exists to move ids that
 * live in the div — so they are captured as one region and handed to the
 * renderer together.
 *
 * **A script with no container in front of it produces no region.** The
 * fragment then falls through to ordinary parsing, which is what happens
 * today: the diagram draws, the buttons become text, and nothing executes.
 * That is a poor experience but a safe one, and it is the right way to fail
 * when the markup is not the shape this understands.
 */
object InteractiveRegions {

    /** Ranges covering `<div>…</div><script>…</script>`, in document order. */
    fun find(html: String): List<IntRange> {
        if (!html.contains("<script", ignoreCase = true)) return emptyList()

        return SCRIPT.findAll(html)
            .mapNotNull { script -> containerBefore(html, script.range.first)?.let { it..script.range.last } }
            .toList()
    }

    /**
     * The `<div>` that closes immediately before [scriptStart].
     *
     * Counted rather than pattern-matched: the widget's own markup nests, and
     * the first `<div` scanning backwards is the innermost one, not the one
     * that owns the script.
     */
    private fun containerBefore(html: String, scriptStart: Int): Int? {
        var i = scriptStart - 1
        while (i >= 0 && html[i].isWhitespace()) i--

        val closeAt = i - CLOSING.length + 1
        if (closeAt < 0 || !html.regionMatches(closeAt, CLOSING, 0, CLOSING.length, ignoreCase = true)) {
            return null
        }

        var depth = 0
        var cursor = closeAt
        while (cursor >= 0) {
            when {
                html.regionMatches(cursor, CLOSING, 0, CLOSING.length, ignoreCase = true) -> depth++

                html.regionMatches(cursor, OPENING, 0, OPENING.length, ignoreCase = true) -> {
                    depth--
                    if (depth == 0) return cursor
                }
            }
            cursor--
        }

        // Unbalanced markup. Better to render it as prose than to swallow an
        // unknown amount of the lesson into a widget.
        return null
    }

    private val SCRIPT = Regex("<script\\b[^>]*>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private const val OPENING = "<div"
    private const val CLOSING = "</div>"
}
