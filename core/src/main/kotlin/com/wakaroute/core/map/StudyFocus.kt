package com.wakaroute.core.map

/**
 * What a student knows, keyed by 要素. Anything absent is [MasteryLevel.NotStarted].
 */
data class MasteryRecord(
    private val levels: Map<ElementId, MasteryLevel> = emptyMap(),
    /**
     * 要素 whose most recent quiz fell short.
     *
     * Kept apart from the level on purpose. 意味がわかる covers two quite
     * different situations — a student partway through reading, and a student
     * who sat the quiz and missed — and **only the second is a stumble**. Fold
     * them together and every student working through a subject in order is
     * told they are stuck.
     */
    val struggling: Set<ElementId> = emptySet(),
) {
    operator fun get(id: ElementId): MasteryLevel = levels[id] ?: MasteryLevel.NotStarted

    /** Nothing recorded at all — every 要素 is まだ. This is Phase 1's state. */
    val isEmpty: Boolean get() = levels.isEmpty() && struggling.isEmpty()

    companion object {
        val Empty = MasteryRecord()
    }
}

/** Why a 要素 is, or is not, something to work on now. */
sealed interface ElementReadiness {
    /** At or above the mastery threshold. Unreachable today — see [MasteryLevel.HighestMeasurable]. */
    data object Mastered : ElementReadiness

    /** Every prerequisite is solid, so this can be studied now. */
    data object Ready : ElementReadiness

    /**
     * A prerequisite is not solid yet.
     *
     * **Being blocked is the ordinary condition of everything a student has not
     * reached yet.** On a first launch, 23 of the 26 数学 要素 are blocked. That
     * is not a problem and must never be drawn as one.
     */
    data class Blocked(val missingPrerequisites: List<ElementId>) : ElementReadiness
}

/** A concrete thing to do next, with the reason attached. */
data class StudyRecommendation(
    val element: LearningElement,
    val currentLevel: MasteryLevel,
    /**
     * The 要素 this one is holding up, when the recommendation came from tracing
     * backwards. Empty when it is simply the natural next step.
     */
    val unblocks: List<LearningElement> = emptyList(),
    /**
     * The quiz here was sat and missed.
     *
     * Separate from the level so a screen can tell 「うまくいかなかった」 from
     * 「まだ途中」 — identical at 意味がわかる, and only the first may be drawn as
     * a problem.
     */
    val isStruggling: Boolean = false,
)

/**
 * Reads the prerequisite graph against what a student knows.
 *
 * This is the product's core idea in one type: rather than reporting
 * 「関数が苦手」, it finds the earliest 要素 actually blocking progress and points
 * there.
 */
class StudyFocus(
    private val subject: Subject,
    private val mastery: MasteryRecord,
) {
    private val elementsById: Map<ElementId, LearningElement> =
        subject.elements.associateBy { it.id }

    fun level(id: ElementId): MasteryLevel = mastery[id]

    fun readiness(element: LearningElement): ElementReadiness {
        if (mastery[element.id].level >= MasteryLevel.MasteredThreshold.level) {
            return ElementReadiness.Mastered
        }

        val missing = element.prerequisiteIds.filter {
            mastery[it].level < MasteryLevel.PrerequisiteThreshold.level
        }
        return if (missing.isEmpty()) ElementReadiness.Ready else ElementReadiness.Blocked(missing)
    }

    /** Everything the student could legitimately work on right now. */
    fun readyElements(): List<LearningElement> =
        subject.elements.filter { readiness(it) is ElementReadiness.Ready }

    /**
     * Whether this 要素 is held up by something that actually went wrong.
     *
     * A stumble needs evidence, and the only evidence is a prerequisite whose
     * quiz was sat and missed. Calling ordinary not-yet-reached work 「つまずき」
     * lights the warning on every 領域 for every student, permanently, and means
     * nothing — while telling a 中3 opening 関数 for the first time that they
     * have already failed at it.
     */
    fun isStumbling(element: LearningElement): Boolean {
        val readiness = readiness(element) as? ElementReadiness.Blocked ?: return false
        return readiness.missingPrerequisites.any { it in mastery.struggling }
    }

    /**
     * Walks backwards from a 要素 the student cannot yet do, to the earliest
     * unmet prerequisites that *are* ready — the actual place to restart.
     *
     * This is 「つまずきの前提まで戻る」. Telling a student stuck on 一次関数 to
     * practise 一次関数 is useless when the real gap is 文字を用いた式.
     */
    fun rootCauses(element: LearningElement): List<LearningElement> {
        val found = mutableListOf<LearningElement>()
        val seen = mutableSetOf<ElementId>()

        fun walk(current: LearningElement) {
            if (!seen.add(current.id)) return // also guards against cycles

            when (val readiness = readiness(current)) {
                is ElementReadiness.Mastered -> return
                is ElementReadiness.Ready -> found.add(current)
                is ElementReadiness.Blocked ->
                    readiness.missingPrerequisites
                        .mapNotNull { elementsById[it] }
                        .forEach(::walk)
            }
        }

        walk(element)
        return found
    }

    /**
     * What to put in front of the student.
     *
     * Ranked by how much each 要素 unblocks, not by how little has been done.
     * Sorting by level alone pushes every untouched 要素 above the one actually
     * holding the subject up — precisely the "study the symptom" behaviour this
     * map exists to avoid.
     *
     * Ties break toward the weaker foundation, then by id. The id tiebreak is
     * not cosmetic: without it the order shuffles between launches, and a
     * student cannot tell whether the advice changed or the app did.
     */
    fun recommendations(limit: Int = 3): List<StudyRecommendation> {
        val blockedBy = mutableMapOf<ElementId, MutableList<LearningElement>>()

        // Every blocked 要素 votes for the earliest prerequisite really at fault.
        for (element in subject.elements) {
            if (readiness(element) !is ElementReadiness.Blocked) continue
            for (cause in rootCauses(element)) {
                blockedBy.getOrPut(cause.id) { mutableListOf() }.add(element)
            }
        }

        val fromBlocking = blockedBy.mapNotNull { (id, blocked) ->
            elementsById[id]?.let {
                StudyRecommendation(
                    element = it,
                    currentLevel = mastery[id],
                    unblocks = blocked,
                    isStruggling = id in mastery.struggling,
                )
            }
        }

        // Then anything with nothing in its way, for a student who is not stuck.
        val alreadyListed = fromBlocking.map { it.element.id }.toSet()
        val fromReady = readyElements()
            .filterNot { it.id in alreadyListed }
            .map {
                StudyRecommendation(
                    element = it,
                    currentLevel = mastery[it.id],
                    isStruggling = it.id in mastery.struggling,
                )
            }

        return (fromBlocking + fromReady)
            .sortedWith(MostUrgentFirst)
            .take(limit)
    }

    companion object {
        /**
         * The single ordering rule for 「つぎにやること」.
         *
         * Kept in one place because applying it here and a level-only sort
         * somewhere else silently reverses the result — and the two screens then
         * disagree about what a student should do next.
         */
        val MostUrgentFirst: Comparator<StudyRecommendation> =
            compareByDescending<StudyRecommendation> { it.unblocks.size }
                .thenBy { it.currentLevel.level }
                .thenBy { it.element.id.value }
    }
}
