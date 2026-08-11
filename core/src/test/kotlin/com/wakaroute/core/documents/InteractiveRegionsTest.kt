package com.wakaroute.core.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Telling a working widget apart from prose.
 *
 * Shaped after the one lesson that uses this — 一次方程式 の「両辺に同じ数を
 * 足す・引く」, a balance scale with three buttons — but written with invented
 * markup, because this repository is public and production bodies do not
 * belong in it.
 */
class InteractiveRegionsTest {

    private val widget = """
        <p>まずは、つり合っているところから。</p>
        <div class="demo">
          <svg viewBox="0 0 460 200"><title>てんびん</title><line id="beam"/></svg>
          <p id="msg">つり合っています。</p>
          <p><button onclick="both()">両方から 3 を取る</button></p>
        </div>
        <script>window.both = function () { return 1; };</script>
        <p>これで両辺が分かりました。</p>
    """.trimIndent()

    @Test
    fun `the widget and its script are captured as one block`() {
        val blocks = DocumentParser.parse(widget)
        val interactive = blocks.filterIsInstance<DocumentBlock.Interactive>().single()

        // Useless apart: the script only moves ids that live in the div.
        assertTrue(interactive.html.contains("<svg"))
        assertTrue(interactive.html.contains("<button"))
        assertTrue(interactive.html.contains("<script"))
    }

    @Test
    fun `the widget keeps its place in the lesson`() {
        val blocks = DocumentParser.parse(widget)

        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is DocumentBlock.Paragraph)
        assertTrue(blocks[1] is DocumentBlock.Interactive)
        assertTrue(blocks[2] is DocumentBlock.Paragraph)
    }

    @Test
    fun `the widget is not also drawn as static prose`() {
        // Otherwise the diagram appears twice and the button text a second
        // time as a dead paragraph underneath it.
        val blocks = DocumentParser.parse(widget)

        assertTrue("the svg must not become a separate figure", blocks.none { it is DocumentBlock.Figure })
        assertTrue(
            "the button label must not become a paragraph",
            blocks.filterIsInstance<DocumentBlock.Paragraph>()
                .none { it.spans.joinToString("") { s -> s.text }.contains("両方から") },
        )
    }

    @Test
    fun `a script with no container in front of it changes nothing`() {
        // Fails back to what the app did before: the prose renders, the script
        // is ignored. A poor experience, but never a swallowed lesson.
        val blocks = DocumentParser.parse("<p>本文です。</p><script>var x = 1;</script>")

        assertEquals(1, blocks.size)
        assertTrue(blocks.single() is DocumentBlock.Paragraph)
    }

    @Test
    fun `a lesson with no script is untouched`() {
        val blocks = DocumentParser.parse("<p>ふつうの本文です。</p><div><p>入れ子の段落。</p></div>")

        assertTrue(blocks.none { it is DocumentBlock.Interactive })
        assertEquals(2, blocks.size)
    }

    @Test
    fun `nested containers do not swallow the paragraph before the widget`() {
        // Counting matters: scanning back for the first `<div` finds the inner
        // one, and the region would then start in the middle of the widget.
        val nested = """
            <p>前置きです。</p>
            <div class="outer"><div class="inner"><button onclick="go()">押す</button></div></div>
            <script>window.go = function () {};</script>
        """.trimIndent()

        val blocks = DocumentParser.parse(nested)
        val interactive = blocks.filterIsInstance<DocumentBlock.Interactive>().single()

        assertTrue(interactive.html.startsWith("<div class=\"outer\""))
        assertTrue(blocks.first() is DocumentBlock.Paragraph)
    }
}
