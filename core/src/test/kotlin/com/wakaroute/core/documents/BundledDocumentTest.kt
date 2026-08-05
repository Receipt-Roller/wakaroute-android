package com.wakaroute.core.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledDocumentTest {

    @Test
    fun `every document is present in the bundle`() {
        // A missing resource is a blank 利用規約 on a student's phone, and the
        // app would still build and run.
        for (document in BundledDocument.entries) {
            assertNotNull("${document.title} is missing from core resources", document.html())
        }
    }

    @Test
    fun `no document still contains a placeholder`() {
        // 【運営者名】 in a published privacy policy is a legal document that
        // names nobody. This must fail the build rather than reach review.
        for (document in BundledDocument.entries) {
            assertEquals(
                "${document.title} still has unresolved placeholders",
                emptyList<String>(),
                document.placeholders(),
            )
        }
    }

    @Test
    fun `every document parses into blocks with a title`() {
        for (document in BundledDocument.entries) {
            val blocks = document.blocks()
            assertTrue("${document.title} produced no blocks", blocks.isNotEmpty())
            assertTrue(
                "${document.title} has no <h1>",
                blocks.firstOrNull() is DocumentBlock.Title,
            )
        }
    }

    @Test
    fun `the operator and contact address appear where they are required`() {
        // 特定商取引法 and the store listings both rely on these being reachable
        // inside the app rather than only on the web site.
        val mustNameOperator = listOf(
            BundledDocument.Terms,
            BundledDocument.Privacy,
            BundledDocument.ForParents,
        )

        for (document in mustNameOperator) {
            val text = document.blocks().joinToString("\n") { it.text() }
            assertTrue("${document.title} does not name the operator", text.contains("株式会社レシートローラー"))
            assertTrue("${document.title} has no contact address", text.contains("support@wakaroute.com"))
        }
    }

    @Test
    fun `the exam guide refuses to predict results`() {
        // サービス仕様 §3 and 共通判断規則 §7. The guide is the one screen most
        // tempted to reassure a student with a number.
        val text = BundledDocument.ExamGuide.blocks().joinToString("\n") { it.text() }
        assertTrue(text.contains("合否を予想することはしません"))
        assertTrue("the guide must send students to the primary sources", text.contains("教育委員会"))
    }

    @Test
    fun `the exam guide keeps the three 倍率 apart`() {
        // They are not interchangeable, and printing one as plain 「倍率」 next to
        // a school's published figure reads as an error.
        val text = BundledDocument.ExamGuide.blocks().joinToString("\n") { it.text() }
        for (kind in listOf("志願倍率", "受験倍率", "実質倍率")) {
            assertTrue("the guide should explain $kind", text.contains(kind))
        }
    }

    private fun DocumentBlock.text(): String = when (this) {
        is DocumentBlock.Title -> spans.plainText
        is DocumentBlock.Heading -> spans.plainText
        is DocumentBlock.Paragraph -> spans.plainText
        is DocumentBlock.BulletList -> items.joinToString("\n") { it.plainText }
    }
}
