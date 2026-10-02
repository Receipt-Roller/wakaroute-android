package com.wakaroute.core.documents

/**
 * Turns the LaTeX in lesson bodies into text a 中学生 can read.
 *
 * Lesson bodies wrap maths in `<span class="math">\(…\)</span>` — MathJax's
 * convention. The web site runs a maths renderer over it; this app renders
 * natively, so without this the markup reached students verbatim:
 * 「\(3-5\) の答えを決めないまま」 in the opening question of the first lesson,
 * and `\dfrac{1}{2}` wherever a fraction was meant.
 *
 * Measured against production before writing: **69 of 293 maths lessons**
 * contain it, 14,536 inline expressions, and the whole command vocabulary is
 * the handful below. That is why this is a converter to plain text rather than
 * a maths layout engine: the input is small, closed and already linear.
 *
 * Tests are written differently: `$…$` rather than `\(…\)`, and they add
 * `^{2}`, `\leqq`, `\angle` and one `cases` block (all 330 questions on
 * production, 2026-10-02). Still small and closed, so still plain text.
 *
 * **Unknown commands are deliberately left alone.** A `\dfrac` that silently
 * became `dfrac` would read as corruption; left as `\foo`, it is conspicuous
 * in review and in a bug report, which is where an unhandled command should
 * surface rather than in a student's lesson.
 */
object MathNotation {

    /**
     * Rewrites every `\(…\)` and `\[…\]` region, leaving the rest untouched.
     *
     * [dollarDelimited] reads `$…$` instead, which is how test questions are
     * written. It is opt-in because a lesson body may contain a literal `$`.
     */
    fun toReadableText(text: String, dollarDelimited: Boolean = false): String {
        if (dollarDelimited) return convertDollarRegions(text)
        if (!text.contains("\\(") && !text.contains("\\[")) return text

        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val open = text.indexOf("\\(", i).nonNegativeOr(text.indexOf("\\[", i))
            if (open < 0) {
                out.append(text, i, text.length)
                break
            }
            val closing = if (text.startsWith("\\(", open)) "\\)" else "\\]"
            val close = text.indexOf(closing, open + 2)
            if (close < 0) {
                // Unbalanced. Better to leave the tail as authored than to
                // swallow the rest of a paragraph.
                out.append(text, i, text.length)
                break
            }

            out.append(text, i, open)
            out.append(convert(text.substring(open + 2, close)))
            i = close + 2
        }

        return out.toString().replace(REPEATED_SPACE, " ")
    }

    private fun convertDollarRegions(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val open = text.indexOf('$', i)
            val close = if (open < 0) -1 else text.indexOf('$', open + 1)
            if (close < 0) {
                // No pair left. A lone `$` is shown as written.
                out.append(text, i, text.length)
                break
            }

            out.append(text, i, open)
            out.append(convert(text.substring(open + 1, close)))
            i = close + 1
        }

        return out.toString().replace(REPEATED_SPACE, " ")
    }

    private fun Int.nonNegativeOr(other: Int) = when {
        this < 0 -> other
        other < 0 -> this
        else -> minOf(this, other)
    }

    private fun convert(math: String): String {
        var s = math

        // A system of equations, read as its lines in order. Done first: the
        // `\\` between the lines is otherwise taken for a command.
        s = s.replace("\\begin{cases}", "").replace("\\end{cases}", "").replace("\\\\", "、")

        // Fractions and roots first: they take braced arguments, so they have
        // to be resolved before the brace-free symbol substitutions run.
        for (fraction in listOf("\\dfrac", "\\tfrac", "\\frac")) {
            s = expandArguments(s, fraction, 2, bracketWhenFollowed = true) { (a, b) -> "${group(a)}/${group(b)}" }
        }
        s = expandArguments(s, "\\sqrt", 1) { (a) -> "√${group(a)}" }

        s = superscripts(s)

        for ((command, replacement) in SYMBOLS) s = s.replace(command, replacement)

        // `\left(` and `\right)` only tell a typesetter how tall to draw the
        // bracket; the bracket itself is the character after them.
        s = s.replace("\\left", "").replace("\\right", "")

        // LaTeX swallows the space that ends a command name and typesets the
        // spacing itself, so `2\times 7` arrives here as `2× 7` — space on one
        // side only. Operators get one space either side; `+` and `-` are left
        // alone because here they are signs, and `+5/2` must not become `+ 5/2`.
        s = s.replace(BRACES, "").replace(OPERATORS) { " ${it.groupValues[1]} " }

        return s
            .replace(SPACE_AROUND_COMMA, "、")
            .replace(SPACE_AFTER_FIGURE) { it.groupValues[1] }
            .replace(REPEATED_SPACE, " ")
            .trim()
    }

    /**
     * `x^{2}` becomes x², which is how a 中学生 writes it by hand.
     *
     * Only when every character has a superscript form. Otherwise it is left
     * as `^(…)`, readable and plainly not a typo.
     */
    private fun superscripts(source: String): String = SUPERSCRIPT.replace(source) { match ->
        val exponent = match.groupValues[1].ifEmpty { match.groupValues[2] }
        val raised = exponent.map { SUPERSCRIPT_CHARACTERS[it] }
        if (raised.all { it != null }) raised.joinToString("") else "^($exponent)"
    }

    /**
     * Wraps a fraction part in brackets when it is more than a single term.
     *
     * `\dfrac{1}{2}` is 1/2, but `\dfrac{x+1}{2}` written as x+1/2 says
     * something different and wrong — which in a maths lesson is worse than
     * showing the markup.
     */
    private fun group(part: String): String {
        val trimmed = part.trim()
        val simple = trimmed.length == 1 || trimmed.all { it.isLetterOrDigit() || it == '.' || it == '−' }
        return if (simple) trimmed else "($trimmed)"
    }

    /**
     * Rewrites `command{…}{…}`, counting braces so nested fractions survive.
     *
     * A regex cannot do this: `\dfrac{\dfrac{1}{2}}{3}` needs the outer closing
     * brace, and `.*?` finds the inner one.
     */
    private fun expandArguments(
        source: String,
        command: String,
        arity: Int,
        bracketWhenFollowed: Boolean = false,
        render: (List<String>) -> String,
    ): String {
        var s = source
        while (true) {
            val start = s.indexOf(command)
            if (start < 0) return s

            var cursor = start + command.length
            val args = mutableListOf<String>()
            var ok = true

            repeat(arity) {
                while (cursor < s.length && s[cursor] == ' ') cursor++
                if (cursor >= s.length || s[cursor] != '{') { ok = false; return@repeat }

                var depth = 0
                val from = cursor + 1
                while (cursor < s.length) {
                    if (s[cursor] == '{') depth++
                    if (s[cursor] == '}') {
                        depth--
                        if (depth == 0) break
                    }
                    cursor++
                }
                if (cursor >= s.length) { ok = false; return@repeat }
                args += s.substring(from, cursor)
                cursor++
            }

            // Malformed — leave it visible rather than eating the rest of the line.
            if (!ok || args.size != arity) return s

            // `\dfrac{1}{2}x^{2}` as 1/2x² reads as 1/(2x²). A fraction that a
            // term follows directly is bracketed: (1/2)x².
            var rendered = render(args.map { convert(it) })
            if (bracketWhenFollowed && s.getOrNull(cursor)?.startsATerm() == true) rendered = "($rendered)"

            s = s.substring(0, start) + rendered + s.substring(cursor)
        }
    }

    /**
     * The commands the lesson bodies actually use.
     *
     * Ordered longest-first where one name prefixes another, so `\qquad` is not
     * matched as `\quad` with a stray `q`.
     */
    private val SYMBOLS = listOf(
        "\\qquad" to " ",
        "\\quad" to " ",
        "\\times" to "×",
        "\\div" to "÷",
        "\\cdot" to "·",
        "\\neq" to "≠",
        // `\leqq` before `\leq`, or it becomes ≤q.
        "\\leqq" to "≦",
        "\\geqq" to "≧",
        "\\leq" to "≤",
        "\\geq" to "≥",
        "\\angle" to "∠",
        "\\triangle" to "△",
        "\\pi" to "π",
        "\\approx" to "≈",
        "\\pm" to "±",
        "\\ldots" to "…",
        "\\dots" to "…",
        "\\square" to "□",
        "\\%" to "%",
        "\\," to " ",
        "\\;" to " ",
        "\\!" to "",
        "\\ " to " ",
    )

    private fun Char.startsATerm() = isLetterOrDigit() || this == '(' || this == '\\'

    private val BRACES = Regex("[{}]")
    private val SPACE_AROUND_COMMA = Regex("\\s*、\\s*")
    private val SPACE_AFTER_FIGURE = Regex("([∠△])\\s+")
    private val OPERATORS = Regex("\\s*([×÷·±≠≤≥≦≧≈=])\\s*")
    private val SUPERSCRIPT = Regex("\\^(?:\\{([^{}]*)\\}|([0-9a-zA-Z]))")
    private val SUPERSCRIPT_CHARACTERS = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '-' to '⁻', '+' to '⁺', 'n' to 'ⁿ',
    )
    private val REPEATED_SPACE = Regex(" {2,}")
}
