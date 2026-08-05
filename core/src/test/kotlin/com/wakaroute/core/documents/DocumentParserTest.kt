package com.wakaroute.core.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentParserTest {

    @Test
    fun `headings, paragraphs and lists are recognised`() {
        val blocks = DocumentParser.parse(
            """
            <h1>題名</h1>
            <p>本文です。</p>
            <h2>見出し</h2>
            <ul><li>ひとつめ</li><li>ふたつめ</li></ul>
            """.trimIndent(),
        )

        assertEquals(4, blocks.size)
        assertTrue(blocks[0] is DocumentBlock.Title)
        assertTrue(blocks[1] is DocumentBlock.Paragraph)
        assertTrue(blocks[2] is DocumentBlock.Heading)
        assertEquals(2, (blocks[3] as DocumentBlock.BulletList).items.size)
    }

    @Test
    fun `emphasis is kept rather than flattened`() {
        // 「合否を予想するものではありません」 is bold because it is the sentence a
        // reader must not skim past.
        val paragraph = DocumentParser.parse("<p>これは<strong>大事</strong>です。</p>")
            .single() as DocumentBlock.Paragraph

        assertEquals(listOf(false, true, false), paragraph.spans.map { it.strong })
        assertEquals("これは大事です。", paragraph.spans.plainText)
    }

    @Test
    fun `br separates lines instead of joining them`() {
        // The operator block is three lines; joined, the company name runs into
        // its own email address.
        val paragraph = DocumentParser.parse("<p>株式会社レシートローラー<br>support@wakaroute.com</p>")
            .single() as DocumentBlock.Paragraph

        assertEquals("株式会社レシートローラー\nsupport@wakaroute.com", paragraph.spans.plainText)
    }

    @Test
    fun `an unknown inline tag loses its styling but not its text`() {
        // Losing a word of a legal document to an unexpected tag would be far
        // worse than losing its emphasis.
        val paragraph = DocumentParser.parse("<p>a<em>b</em>c</p>").single() as DocumentBlock.Paragraph
        assertEquals("abc", paragraph.spans.plainText)
    }

    @Test
    fun `entities are decoded, and ampersand last`() {
        val paragraph = DocumentParser.parse("<p>&amp;lt; &lt; &gt; &quot;</p>")
            .single() as DocumentBlock.Paragraph

        assertEquals("&lt; < > \"", paragraph.spans.plainText)
    }

    @Test
    fun `source line breaks inside a paragraph collapse to single spaces`() {
        val paragraph = DocumentParser.parse("<p>これは\n  ひとつの文です。</p>")
            .single() as DocumentBlock.Paragraph

        assertEquals("これは ひとつの文です。", paragraph.spans.plainText)
    }

    @Test
    fun `an empty document produces no blocks rather than a blank one`() {
        assertEquals(emptyList<DocumentBlock>(), DocumentParser.parse(""))
        assertEquals(emptyList<DocumentBlock>(), DocumentParser.parse("<p></p><ul></ul>"))
    }

    // --- lesson bodies -----------------------------------------------------
    //
    // The shapes below mirror real MANABU2 lesson HTML (structure copied,
    // wording written here). A 数学 course carries 58 tables, numbered steps,
    // collapsible answers and inline SVG — all of which the parser used to drop
    // without erroring, on a screen that still looked correct.

    @Test
    fun `a table keeps its header and its grid`() {
        val block = DocumentParser.parse(
            """
            <table>
            <thead><tr><th>場面</th><th>基準の0</th></tr></thead>
            <tbody>
            <tr><td>気温</td><td>0℃</td></tr>
            <tr><td>建物</td><td>入口の階</td></tr>
            </tbody>
            </table>
            """.trimIndent(),
        ).single() as DocumentBlock.Table

        assertEquals(listOf("場面", "基準の0"), block.header.map { it.plainText })
        assertEquals(2, block.rows.size)
        assertEquals(listOf("気温", "0℃"), block.rows.first().map { it.plainText })
    }

    @Test
    fun `a header row written without thead still reads as a header`() {
        // Which of the two the authoring tool emitted is not something a
        // reader can see, and both mean the same thing.
        val block = DocumentParser.parse(
            "<table><tr><th>用語</th><th>意味</th></tr><tr><td>負の数</td><td>0より小さい数</td></tr></table>",
        ).single() as DocumentBlock.Table

        assertEquals(listOf("用語", "意味"), block.header.map { it.plainText })
        assertEquals(1, block.rows.size)
    }

    @Test
    fun `numbered steps stay numbered`() {
        // 「①のあと②」 stops meaning anything if the steps are drawn as dots.
        val blocks = DocumentParser.parse("<ol><li>符号を見る</li><li>大きさを比べる</li></ol>")

        val block = blocks.single()
        assertTrue(block is DocumentBlock.NumberedList)
        assertEquals(2, (block as DocumentBlock.NumberedList).items.size)
    }

    @Test
    fun `bullets and numbers stay different kinds of list`() {
        val blocks = DocumentParser.parse("<ul><li>あ</li></ul><ol><li>い</li></ol>")

        assertTrue(blocks[0] is DocumentBlock.BulletList)
        assertTrue(blocks[1] is DocumentBlock.NumberedList)
    }

    @Test
    fun `a collapsible keeps its question and its answer apart`() {
        // The author wrote 「確認1：…」 expecting the student to try first.
        // Merging the two would hand them the answer with the question.
        val block = DocumentParser.parse(
            """
            <details>
              <summary>確認1：0℃より7℃低い気温を表そう</summary>
              −7℃です。0℃を基準にして低い側なので、負の符号を付けます。
            </details>
            """.trimIndent(),
        ).single() as DocumentBlock.Collapsible

        assertEquals("確認1：0℃より7℃低い気温を表そう", block.summary.plainText)
        assertTrue(block.body.isNotEmpty())
        assertTrue((block.body.single() as DocumentBlock.Paragraph).spans.plainText.startsWith("−7℃です"))
    }

    @Test
    fun `an svg diagram is captured whole, with the author's description`() {
        // Flattened to its text nodes this would come out as a row of loose
        // axis labels — 「−4 −3 −2 …」 with no indication it was ever a picture.
        val block = DocumentParser.parse(
            """
            <p>数直線で考えます。</p>
            <svg viewBox="0 0 680 180" role="img">
              <title>0を中心にした数直線</title>
              <desc>左からマイナス4、マイナス3、0、1、2が並んでいる</desc>
              <line x1="60" y1="88" x2="620" y2="88"/>
              <g><text x="100" y="126">−4</text><text x="160" y="126">−3</text></g>
            </svg>
            """.trimIndent(),
        )

        val figure = block.filterIsInstance<DocumentBlock.Figure>().single()
        assertEquals("0を中心にした数直線", figure.title)
        assertEquals("左からマイナス4、マイナス3、0、1、2が並んでいる", figure.description)
        assertTrue(figure.svg.startsWith("<svg"))

        // And the axis labels have not leaked out as prose.
        assertTrue(block.filterIsInstance<DocumentBlock.Paragraph>().none { it.spans.plainText.contains("−4") })
    }

    @Test
    fun `a lesson body keeps every block, in order`() {
        val blocks = DocumentParser.parse(
            """
            <h1 id="section">0より小さい数はどこにある？</h1>
            <h2 id="section-1">このレッスンの役割</h2>
            <p>負の数が必要になる理由を説明できるようになります。</p>
            <table><thead><tr><th>場面</th></tr></thead><tbody><tr><td>気温</td></tr></tbody></table>
            <ol><li>符号を見る</li></ol>
            <details><summary>確認1</summary>−7℃です。</details>
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                DocumentBlock.Title::class,
                DocumentBlock.Heading::class,
                DocumentBlock.Paragraph::class,
                DocumentBlock.Table::class,
                DocumentBlock.NumberedList::class,
                DocumentBlock.Collapsible::class,
            ),
            blocks.map { it::class },
        )
    }

}
