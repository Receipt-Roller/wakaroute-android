package com.wakaroute.core.map

/**
 * The identifier of a 教科.
 *
 * A value class rather than a bare String so that a course id and a subject id
 * cannot be passed to each other's parameters — they are both opaque hex
 * strings and the compiler is the only thing that would notice.
 */
@JvmInline
value class SubjectId(val value: String)

/**
 * The identifier of a 要素, which **is** the MANABU2 course id.
 *
 * The 共通判断規則 §1 is explicit: a 要素 is the course itself, not a separate
 * entity, and the name is never the key. Course names change, and on the day
 * one does, a name-keyed 要素 disappears from a student's map.
 */
@JvmInline
value class ElementId(val value: String)

/** A 領域 — the broad grouping inside a subject (数と式, 図形, 関数, データの活用). */
data class LearningDomain(
    /** The letter shown on the web map (A–D). */
    val code: String,
    val name: String,
) {
    val id: String get() = code
}

/** A 要素 — one unit of understanding, and what mastery is tracked against. */
data class LearningElement(
    val id: ElementId,
    val name: String,
    val domainCode: String,
    /** 中1–中3, when this is normally taught. Null when it spans grades. */
    val grade: Int? = null,
    /**
     * The 要素 that must be understood first.
     *
     * `A requires B` means 「B が固まっていないと A は身につかない」 — not
     * 「B の次に A を学ぶ」. Mere ordering would need only an index; the graph
     * exists because prerequisites cross 領域 boundaries, as 三平方の定理 (図形)
     * needing 平方根 (数と式) does.
     */
    val prerequisiteIds: List<ElementId> = emptyList(),
)

data class Subject(
    val id: SubjectId,
    val name: String,
    val domains: List<LearningDomain>,
    val elements: List<LearningElement>,
) {
    fun domain(code: String): LearningDomain? = domains.firstOrNull { it.code == code }

    fun elementsIn(domainCode: String): List<LearningElement> =
        elements.filter { it.domainCode == domainCode }

    fun element(id: ElementId): LearningElement? = elements.firstOrNull { it.id == id }
}

/**
 * The five 教科, which are fixed.
 *
 * Known rather than discovered, so a subject with no content yet still appears
 * — as 準備中 rather than silently missing, which is what the サービス仕様
 * requires of anything unbuilt.
 */
enum class SchoolSubject(val label: String) {
    Japanese("国語"),
    Math("数学"),
    English("英語"),
    Science("理科"),
    Social("社会");

    val id: SubjectId get() = SubjectId(label)
}
