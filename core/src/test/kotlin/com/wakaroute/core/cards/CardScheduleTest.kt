package com.wakaroute.core.cards

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same rules as iOS's card tests, so a student's deck behaves alike on both. */
class CardScheduleTest {

    private val day = LocalDate.of(2026, 10, 6)

    @Test
    fun `a correct answer moves a card up one box`() {
        val first = CardScheduler.answering(null, correct = true, today = day)
        val second = CardScheduler.answering(first, correct = true, today = day)

        assertEquals(1, first.box)
        assertEquals(2, second.box)
    }

    @Test
    fun `a wrong answer sends a card back to the start`() {
        // One correct answer is not knowing something.
        val review = CardReview(box = 4, reviewedOnEpochDay = day.toEpochDay())

        assertEquals(0, CardScheduler.answering(review, correct = false, today = day).box)
    }

    @Test
    fun `five in a row retires a card`() {
        var review: CardReview? = null
        repeat(6) { review = CardScheduler.answering(review, correct = true, today = day) }

        assertEquals(CardReview.LEARNED_BOX, review!!.box)
        assertFalse(CardScheduler.isDue(review, day.plusYears(1)))
    }

    @Test
    fun `a card waits the interval for its box`() {
        val review = CardReview(box = 2, reviewedOnEpochDay = day.toEpochDay())

        assertFalse(CardScheduler.isDue(review, day.plusDays(2)))
        assertTrue(CardScheduler.isDue(review, day.plusDays(3)))
    }

    @Test
    fun `a review dated in the future is shown, not hidden for years`() {
        val review = CardReview(box = 3, reviewedOnEpochDay = day.plusYears(2).toEpochDay())

        assertTrue(CardScheduler.isDue(review, day))
    }

    @Test
    fun `cards without a back are never dealt`() {
        val cards = listOf(word("w1", rank = 1, meanings = emptyList()), word("w2", rank = 2))

        assertEquals(listOf("w2"), CardDeck.inScope(cards, CardDeck.Scope()).map { it.id })
    }

    @Test
    fun `commonest first, and unranked cards last rather than first`() {
        val cards = listOf(word("unranked", rank = null), word("rare", rank = 900), word("the", rank = 1))

        assertEquals(listOf("the", "rare", "unranked"), CardDeck.inScope(cards, CardDeck.Scope()).map { it.id })
    }

    @Test
    fun `the scope filters by year`() {
        val cards = listOf(word("w1", rank = 1, grade = 1), word("w2", rank = 2, grade = 2))

        assertEquals(listOf("w2"), CardDeck.inScope(cards, CardDeck.Scope(grade = 2)).map { it.id })
    }

    @Test
    fun `a sitting puts due cards before new ones, and stops at its size`() {
        val cards = (1..30).map { word("w$it", rank = it) }
        val progress = CardProgress(mapOf("w25" to CardReview(box = 0, reviewedOnEpochDay = day.toEpochDay())))

        val session = CardDeck.session(cards, CardDeck.Scope(), progress, day)

        assertEquals("w25", session.first().id)
        assertEquals(CardDeck.SESSION_SIZE, session.size)
    }

    @Test
    fun `a card answered today and not yet due stays out of the sitting`() {
        val cards = listOf(word("w1", rank = 1), word("w2", rank = 2))
        val progress = CardProgress(mapOf("w1" to CardReview(box = 1, reviewedOnEpochDay = day.toEpochDay())))

        assertEquals(listOf("w2"), CardDeck.session(cards, CardDeck.Scope(), progress, day).map { it.id })
    }

    private fun word(id: String, rank: Int?, grade: Int? = 1, meanings: List<String> = listOf("意味")) =
        WordCard(id = id, lemma = id, frequencyRank = rank, recommendedGrade = grade, meaningsJa = meanings)
}
