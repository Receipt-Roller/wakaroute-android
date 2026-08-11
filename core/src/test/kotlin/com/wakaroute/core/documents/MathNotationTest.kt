package com.wakaroute.core.documents

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The maths markup a student was being shown raw.
 *
 * Every shape here was measured in production lesson bodies before being
 * written down; the values are invented but the constructs are the real ones.
 */
class MathNotationTest {

    @Test
    fun `bare delimiters disappear`() {
        // 「\(3-5\) の答えを決めないまま」 — the opening question of the first
        // maths lesson, exactly as a student saw it.
        assertEquals("3-5 の答えを決めないまま", MathNotation.toReadableText("\\(3-5\\) の答えを決めないまま"))
    }

    @Test
    fun `fractions become readable`() {
        assertEquals("1/2", MathNotation.toReadableText("\\(\\dfrac{1}{2}\\)"))
        assertEquals("3/4", MathNotation.toReadableText("\\(\\tfrac{3}{4}\\)"))
    }

    @Test
    fun `a compound numerator keeps its brackets`() {
        // x+1/2 is a different number from (x+1)/2, and in a maths lesson that
        // is worse than showing the markup.
        assertEquals("(x+1)/2", MathNotation.toReadableText("\\(\\dfrac{x+1}{2}\\)"))
        assertEquals("1/(n+1)", MathNotation.toReadableText("\\(\\dfrac{1}{n+1}\\)"))
    }

    @Test
    fun `nested fractions survive`() {
        // A regex cannot do this: `.*?` finds the inner closing brace.
        assertEquals("(1/2)/3", MathNotation.toReadableText("\\(\\dfrac{\\dfrac{1}{2}}{3}\\)"))
    }

    @Test
    fun `operators become the symbols they mean`() {
        assertEquals("2 × 3 ÷ 4", MathNotation.toReadableText("\\(2 \\times 3 \\div 4\\)"))
        assertEquals("a ≠ b", MathNotation.toReadableText("\\(a \\neq b\\)"))
        assertEquals("…", MathNotation.toReadableText("\\(\\ldots\\)"))
    }

    @Test
    fun `an operator gets space on both sides, not one`() {
        // LaTeX swallows the space that ends a command name, so `2\times 7`
        // used to come out as `2× 7`.
        assertEquals("2 × 7 = 14", MathNotation.toReadableText("\\(2\\times 7=14\\)"))
        assertEquals("1 ÷ 3 = 1/3", MathNotation.toReadableText("\\(1\\div 3=\\dfrac{1}{3}\\)"))
    }

    @Test
    fun `signs are not spaced out`() {
        // These are signs, not operators. `+ 5/2` reads as an addition with a
        // missing left operand.
        assertEquals("+5/2, -7/10", MathNotation.toReadableText("\\(+\\dfrac{5}{2},\\quad -\\dfrac{7}{10}\\)"))
    }

    @Test
    fun `sizing commands leave the bracket they were sizing`() {
        assertEquals("(a+b)", MathNotation.toReadableText("\\(\\left(a+b\\right)\\)"))
    }

    @Test
    fun `qquad is not mistaken for quad`() {
        assertEquals("a b", MathNotation.toReadableText("\\(a\\qquad b\\)"))
    }

    @Test
    fun `an unknown command is left visible rather than mangled`() {
        // Silently turning it into `oiint` would read as corruption. Left
        // alone it is conspicuous, which is where an unhandled command should
        // surface — in review, not in a lesson.
        assertEquals("\\oiint x", MathNotation.toReadableText("\\(\\oiint x\\)"))
    }

    @Test
    fun `an unclosed expression does not swallow the paragraph`() {
        val text = "答えは \\(3-5 で、つづきの文章があります。"
        assertEquals(text, MathNotation.toReadableText(text))
    }

    @Test
    fun `text without maths is returned untouched`() {
        val plain = "ワカルートは、入試の合否を予想するものではありません。"
        assertEquals(plain, MathNotation.toReadableText(plain))
    }

    @Test
    fun `the parser applies it to lesson bodies`() {
        val blocks = DocumentParser.parse(
            """<p>答えは <span class="math">\(\dfrac{1}{2}\)</span> です。</p>""",
        )
        assertEquals(
            "答えは 1/2 です。",
            (blocks.single() as DocumentBlock.Paragraph).spans.joinToString("") { it.text },
        )
    }
}
