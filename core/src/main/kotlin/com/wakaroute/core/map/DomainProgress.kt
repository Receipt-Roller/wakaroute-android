package com.wakaroute.core.map

/**
 * One 領域's state — the answer to 「数学のどこが強くて、どこが弱いか」.
 */
data class DomainProgress(
    val domain: LearningDomain,
    val totalElements: Int,
    /**
     * 要素 at 基本を解ける or better.
     *
     * This, not a 習得 count, is the honest measure: the upper levels have no
     * 確認テスト behind them, so a 習得 counter reads 0 for a student who has
     * finished everything available.
     */
    val solidCount: Int,
    /** Stumbles only — see [StudyFocus.isStumbling]. */
    val stumblingCount: Int,
    val notStartedCount: Int,
    /** How many 要素 sit at each level, lowest first. Drives the stacked bar. */
    val levelCounts: List<Pair<MasteryLevel, Int>>,
) {
    val solidFraction: Double
        get() = if (totalElements == 0) 0.0 else solidCount.toDouble() / totalElements

    /**
     * A plain-language read on the 領域, so the student is not left to interpret
     * a bar on their own.
     */
    enum class Standing(val label: String) {
        NotStarted("これから"),
        Stumbling("手前でつまずき"),
        InProgress("学習中"),
        Strong("よく進んでいます"),
    }

    /**
     * Note the order: a 領域 the student has not touched reads as これから even
     * when its first 要素 is blocked by something in another 領域.
     *
     * 「つまずき」 means they tried and something earlier was missing. Saying it
     * about work not yet begun is both false and discouraging.
     */
    val standing: Standing
        get() = when {
            totalElements == 0 -> Standing.NotStarted
            notStartedCount == totalElements -> Standing.NotStarted
            stumblingCount > 0 -> Standing.Stumbling
            // Measured against 基本を解ける, not 習得. A threshold on a 習得
            // fraction would hold every 領域 at 学習中 forever, however much
            // the student had actually done.
            solidFraction >= 0.6 -> Standing.Strong
            else -> Standing.InProgress
        }

    companion object {
        fun of(domain: LearningDomain, subject: Subject, mastery: MasteryRecord): DomainProgress {
            val focus = StudyFocus(subject, mastery)
            val elements = subject.elementsIn(domain.code)

            return DomainProgress(
                domain = domain,
                totalElements = elements.size,
                solidCount = elements.count {
                    mastery[it.id].level >= MasteryLevel.PrerequisiteThreshold.level
                },
                stumblingCount = elements.count(focus::isStumbling),
                notStartedCount = elements.count { mastery[it.id] == MasteryLevel.NotStarted },
                levelCounts = MasteryLevel.entries.map { level ->
                    level to elements.count { mastery[it.id] == level }
                },
            )
        }
    }
}

/** One subject's state, for the five-across view on the home screen. */
data class SubjectProgress(
    val subjectId: SubjectId,
    val subjectName: String,
    val totalElements: Int,
    val solidCount: Int,
    val inProgressCount: Int,
    val notStartedCount: Int,
    val stumblingCount: Int,
) {
    val solidFraction: Double
        get() = if (totalElements == 0) 0.0 else solidCount.toDouble() / totalElements

    companion object {
        fun of(subject: Subject, mastery: MasteryRecord): SubjectProgress {
            val focus = StudyFocus(subject, mastery)

            var solid = 0
            var inProgress = 0
            var notStarted = 0
            var stumbling = 0

            for (element in subject.elements) {
                val level = mastery[element.id]
                when {
                    level == MasteryLevel.NotStarted -> notStarted++
                    level.level < MasteryLevel.MasteredThreshold.level -> inProgress++
                }
                if (level.level >= MasteryLevel.PrerequisiteThreshold.level) solid++
                // Only a stumble counts. Everything not yet reached is blocked
                // too, and reporting that would flag every subject a student
                // has begun.
                if (focus.isStumbling(element)) stumbling++
            }

            return SubjectProgress(
                subjectId = subject.id,
                subjectName = subject.name,
                totalElements = subject.elements.size,
                solidCount = solid,
                inProgressCount = inProgress,
                notStartedCount = notStarted,
                stumblingCount = stumbling,
            )
        }
    }
}

fun Subject.domainProgress(mastery: MasteryRecord): List<DomainProgress> =
    domains.map { DomainProgress.of(it, this, mastery) }
