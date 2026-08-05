package com.wakaroute.core.map

/**
 * 「つぎにやること」, ready to draw.
 *
 * [StudyFocus.recommendations] decides *what*; this decides *how it is said*,
 * and the two are separated because the wording is where the damage happens.
 * 共通判断規則 §3 records that iOS shipped the same mistake three times, and
 * every time the cause was one model state standing in for situations that mean
 * different things to a student.
 *
 * The three [Tone]s below are that table, made impossible to conflate.
 */
data class NextStep(
    val element: LearningElement,
    val tone: Tone,
    /** What this unblocks, when the suggestion came from tracing backwards. */
    val unblocks: List<LearningElement>,
) {
    /**
     * How the row reads — and it must never be read from the level alone.
     *
     * 意味がわかる covers both 「読んでいる途中」 and 「受けて落ちた」. Only the second
     * is a problem, and only the second may be drawn as one.
     */
    enum class Tone {
        /** Not begun. **Not** a warning: most of the map is like this on day one. */
        NotStarted,

        /** Under way, nothing gone wrong. */
        InProgress,

        /** A quiz here was sat and missed. The only tone that signals a problem. */
        Stumbling,
    }

    /**
     * The sentence a student reads.
     *
     * All three end in 「◯◯に進めます」 rather than naming a failure, because the
     * point of the row is where they can get to — not what they have not done.
     * The 未着手 wording especially: **a student who has not begun must not be
     * told to go back.**
     */
    val message: String
        get() {
            val destination = unblocks.firstOrNull()?.name

            return when (tone) {
                Tone.NotStarted -> destination
                    ?.let { "ここから始めると、$it に進めます" }
                    ?: "ここから始められます"

                Tone.InProgress -> destination
                    ?.let { "ここを終えると、$it に進めます" }
                    ?: "続きから進められます"

                Tone.Stumbling -> destination
                    ?.let { "もう一度ここを固めると、$it に進めます" }
                    ?: "もう一度ここを固めましょう"
            }
        }
}

object HomeDigest {

    /**
     * What to put in front of the student, at most [limit] rows.
     *
     * **One row per 要素.** §4: 「同じコースは1行だけ。上位の行が下位を吸収します」 —
     * two rows saying different things about one course is worse than one row.
     * [StudyFocus.recommendations] already guarantees that; this preserves it.
     */
    fun nextSteps(subject: Subject, mastery: MasteryRecord, limit: Int = 3): List<NextStep> {
        val focus = StudyFocus(subject, mastery)

        return focus.recommendations(limit).map { recommendation ->
            NextStep(
                element = recommendation.element,
                tone = tone(recommendation, mastery),
                unblocks = recommendation.unblocks,
            )
        }
    }

    /**
     * Which of the three situations this is.
     *
     * Ordered so that evidence of a problem wins, and absence of activity is
     * never mistaken for one. Note what is **not** consulted: whether the 要素 is
     * `blocked`. On a first launch 23 of 26 are, and that is the ordinary
     * condition of work not yet reached.
     */
    private fun tone(recommendation: StudyRecommendation, mastery: MasteryRecord): NextStep.Tone = when {
        // The only hard evidence that something did not stick.
        recommendation.isStruggling -> NextStep.Tone.Stumbling

        mastery[recommendation.element.id] == MasteryLevel.NotStarted -> NextStep.Tone.NotStarted

        else -> NextStep.Tone.InProgress
    }
}
