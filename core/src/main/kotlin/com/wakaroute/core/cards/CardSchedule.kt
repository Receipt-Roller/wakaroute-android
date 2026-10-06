package com.wakaroute.core.cards

import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * How well one card is known, and when it was last answered.
 *
 * [box] counts consecutive correct answers; it is not a score. A wrong answer
 * resets it, because **one correct answer is not knowing something** — the
 * mistake this app once made by unlocking a whole 要素 from one right question.
 */
@Serializable
data class CardReview(
    val box: Int,
    /** The day it was last answered, as [LocalDate.toEpochDay]. */
    val reviewedOnEpochDay: Long,
) {
    val isLearned: Boolean get() = box >= LEARNED_BOX

    companion object {
        const val LEARNED_BOX = 5
    }
}

/**
 * Which cards this student has seen. **Lives only on this device.**
 *
 * Not carried across a handset change: `/me/link` moves study history and
 * 志望校, and there is no server to put cards on. The screen says so — losing
 * work silently is worse than not carrying it.
 */
@Serializable
data class CardProgress(val reviews: Map<String, CardReview> = emptyMap()) {
    operator fun get(cardId: String): CardReview? = reviews[cardId]

    fun recording(cardId: String, review: CardReview) = CardProgress(reviews + (cardId to review))
}

/**
 * Spaced repetition, deliberately small — the same rules as iOS.
 *
 * A correct answer moves a card one box up and further away; a wrong one sends
 * it back to the start. Five correct in a row retires it.
 */
object CardScheduler {

    /** Days to wait after reaching each box. Box 0 is always due. */
    val intervalsInDays = listOf(0, 1, 3, 7, 16, 35)

    fun interval(box: Int): Int = intervalsInDays[box.coerceIn(0, intervalsInDays.lastIndex)]

    fun answering(review: CardReview?, correct: Boolean, today: LocalDate): CardReview {
        val day = today.toEpochDay()
        if (!correct) return CardReview(box = 0, reviewedOnEpochDay = day)
        return CardReview(box = minOf((review?.box ?: 0) + 1, CardReview.LEARNED_BOX), reviewedOnEpochDay = day)
    }

    /**
     * Whether a card should be shown [today].
     *
     * A review dated in the future means the clock went backwards — a handset
     * whose time was wrong and then corrected. The card is shown rather than
     * hidden until the date catches up, which could be years.
     */
    fun isDue(review: CardReview?, today: LocalDate): Boolean {
        if (review == null) return true
        if (review.isLearned) return false

        val waited = today.toEpochDay() - review.reviewedOnEpochDay
        if (waited < 0) return true
        return waited >= interval(review.box)
    }
}

/** Picks which cards to show, and in what order. */
object CardDeck {

    /** The range a student is working on. Null means every year / both stages. */
    data class Scope(val grade: Int? = null, val stage: String? = null)

    /** One sitting. A deck a student cannot finish is a punishment. */
    const val SESSION_SIZE = 20

    /**
     * Cards in [scope], commonest first.
     *
     * By `frequencyRank` because that makes the early cards worth learning;
     * `sequence` breaks ties. Unranked cards go last — sorted as rank 0, one
     * would jump the whole deck.
     */
    fun <Card : StudyCard> inScope(cards: List<Card>, scope: Scope): List<Card> =
        cards
            .filter { card ->
                card.isStudiable &&
                    (scope.grade == null || card.recommendedGrade == scope.grade) &&
                    (scope.stage == null || card.studyStage == scope.stage)
            }
            .sortedWith(
                compareBy<Card>({ it.frequencyRank ?: Int.MAX_VALUE }, { it.sequence ?: Int.MAX_VALUE }, { it.id }),
            )

    /**
     * One sitting: cards already started and due, then new ones to fill.
     *
     * Due cards first, so a student who has been away is not handed new
     * material on top of a backlog.
     */
    fun <Card : StudyCard> session(
        cards: List<Card>,
        scope: Scope,
        progress: CardProgress,
        today: LocalDate,
        size: Int = SESSION_SIZE,
    ): List<Card> {
        val ordered = inScope(cards, scope)
        val due = ordered.filter { progress[it.id] != null && CardScheduler.isDue(progress[it.id], today) }
        val fresh = ordered.filter { progress[it.id] == null }
        return (due + fresh).take(size)
    }
}
