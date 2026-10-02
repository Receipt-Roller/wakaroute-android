package com.wakaroute.core.map

/**
 * How well a single 要素 is understood.
 *
 * Deliberately not a percentage. 「80%終わった」 says nothing about whether a
 * student can use the idea in an exam, and it invites grinding for a number.
 * These five levels describe capability instead, and they are the ones on the
 * web 理解マップ — the wording must match, because a student reading both must
 * not be told two different things.
 */
enum class MasteryLevel(val level: Int, val label: String) {
    NotStarted(0, "まだ"),
    UnderstandsMeaning(1, "意味がわかる"),
    SolvesBasics(2, "基本を解ける"),
    ConnectsReasoning(3, "根拠をつなげる"),
    AppliesToNovel(4, "初見で使える"),
    StableUnderTime(5, "時間内に安定する");

    companion object {
        /**
         * The point at which a 要素 is solid enough that what builds on it can
         * be studied without the student silently falling back.
         */
        val PrerequisiteThreshold = SolvesBasics

        /** The point at which a 要素 counts as finished for exam purposes. */
        val MasteredThreshold = AppliesToNovel

        /**
         * The highest level any available evidence can support.
         *
         * Levels 3 and above need a per-要素 確認テスト, and none has been
         * authored (the スタート診断 cover a whole 領域, not one 要素);
         * level 5 additionally needs test time limits (LMS-DEV t-d1bea71).
         *
         * Two consequences that are easy to get wrong, and both matter:
         *
         * 1. Screens must draw everything above this as **準備中**, not as
         *    「達成していない」. A student has not failed a level nobody can measure.
         * 2. Nothing can ever reach [MasteredThreshold] today. A counter or ring
         *    built on 習得 reads 0/26 for a student who has finished the whole
         *    subject. Count [PrerequisiteThreshold] or better instead.
         */
        val HighestMeasurable = SolvesBasics
    }
}

/** True when this level is one the app is able to justify claiming. */
val MasteryLevel.isMeasurable: Boolean
    get() = level <= MasteryLevel.HighestMeasurable.level
