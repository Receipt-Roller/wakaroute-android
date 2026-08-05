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
}
