package com.wakaroute.core.cards

import com.wakaroute.core.net.LenientInt
import kotlinx.serialization.Serializable

/**
 * A licence a card set is published under.
 *
 * NGSL and KANJIDIC2 are **CC BY-SA**, so showing the cards without this is a
 * licence breach. [attribution] is the server's wording, shown verbatim.
 */
@Serializable
data class CardLicense(
    val name: String? = null,
    val spdxId: String? = null,
    val url: String? = null,
    val attribution: String? = null,
)

/** Anything that can be put on a card and ordered. */
interface StudyCard {
    val id: String

    /** 1–3, or null for the 小学校 review words. */
    val recommendedGrade: Int?

    /** Lower is commoner. */
    val frequencyRank: Int?
    val sequence: Int?

    /** `elementary-review` or `junior-high`; null where it does not apply. */
    val studyStage: String?

    /** False when the card has no back — nothing to be tested on. */
    val isStudiable: Boolean
}

/** 英単語カード. */
@Serializable
data class WordCard(
    override val id: String,
    val lemma: String = "",
    val stage: String? = null,
    @Serializable(with = LenientInt::class) override val recommendedGrade: Int? = null,
    @Serializable(with = LenientInt::class) override val sequence: Int? = null,
    @Serializable(with = LenientInt::class) override val frequencyRank: Int? = null,
    val partsOfSpeech: List<String> = emptyList(),
    /** Dictionary wording, often long and with more than one sense. */
    val meaningsJa: List<String> = emptyList(),
    val pronunciations: List<String> = emptyList(),
) : StudyCard {
    override val studyStage: String? get() = stage

    /**
     * Seven of the 2,450 published words have no Japanese meaning (`i` and
     * `email` among them). A card with a blank back is left out, not shown empty.
     */
    override val isStudiable: Boolean get() = meaningsJa.isNotEmpty()
}

/** 漢字カード. */
@Serializable
data class KanjiCard(
    override val id: String,
    val character: String = "",
    val officialStage: String? = null,
    @Serializable(with = LenientInt::class) override val recommendedGrade: Int? = null,
    @Serializable(with = LenientInt::class) override val sequence: Int? = null,
    @Serializable(with = LenientInt::class) override val frequencyRank: Int? = null,
    @Serializable(with = LenientInt::class) val strokeCount: Int? = null,
    /** Usually several of each. A card that shows one teaches a half-truth. */
    val onReadings: List<String> = emptyList(),
    val kunReadings: List<String> = emptyList(),
) : StudyCard {
    override val studyStage: String? get() = officialStage
    override val isStudiable: Boolean get() = onReadings.isNotEmpty() || kunReadings.isNotEmpty()
}

/**
 * 数学・理科・社会 の一問一答. ワカルート's own writing.
 *
 * [order] is not in the payload: it is the card's position in the published
 * set, assigned on download and kept on the device. These carry no frequency
 * ranking, so the published order — 教科, then 領域 — is the order to study in.
 * Without storing it, a reloaded set would fall back to sorting by id.
 */
@Serializable
data class SubjectCard(
    override val id: String,
    /** `math`, `science`, `social-studies`. */
    val subject: String = "",
    /** `numbers`, `geometry`, … — the same 領域 as the learning paths. */
    val domain: String = "",
    @Serializable(with = LenientInt::class) override val recommendedGrade: Int? = null,
    val cardType: String? = null,
    val prompt: String = "",
    val answer: String = "",
    val explanation: String? = null,
    val tags: List<String> = emptyList(),
    val order: Int = 0,
) : StudyCard {
    override val frequencyRank: Int? get() = null
    override val sequence: Int? get() = order
    override val studyStage: String? get() = null
    override val isStudiable: Boolean get() = prompt.isNotEmpty() && answer.isNotEmpty()
}

/**
 * One download of a card set, as kept on the device.
 *
 * [asOf] and [datasetVersion] are kept because a cache without the age of its
 * contents cannot be reasoned about. [entityTag] is sent back as
 * `If-None-Match`; empty when the server sent none.
 */
@Serializable
data class CardCatalog<Card>(
    val datasetVersion: String = "",
    val asOf: String = "",
    val licenses: List<CardLicense> = emptyList(),
    val cards: List<Card> = emptyList(),
    val entityTag: String = "",
)

/** The word set lists `licenses`; the kanji and subject sets a single `license`. */
@Serializable
internal data class CardDataset<Card>(
    val datasetVersion: String? = null,
    val asOf: String? = null,
    val licenses: List<CardLicense>? = null,
    val license: CardLicense? = null,
    val items: List<Card> = emptyList(),
) {
    fun toCatalog(entityTag: String, cards: List<Card> = items) = CardCatalog(
        datasetVersion = datasetVersion.orEmpty(),
        asOf = asOf.orEmpty(),
        licenses = licenses ?: listOfNotNull(license),
        cards = cards,
        entityTag = entityTag,
    )
}

/** The 教科 and 領域 names behind the ids the subject cards use. */
object CardSubjects {
    const val MATH = "math"
    const val SCIENCE = "science"
    const val SOCIAL_STUDIES = "social-studies"

    // The ids are stable and the names are not, so the names live here rather
    // than being read out of the data — the same rule as on iOS.
    private val domains = mapOf(
        MATH to mapOf("numbers" to "数と式", "geometry" to "図形", "functions" to "関数", "data" to "データの活用"),
        SCIENCE to mapOf("energy" to "エネルギー", "particles" to "粒子", "life" to "生命", "earth" to "地球"),
        SOCIAL_STUDIES to mapOf("geography" to "地理", "history" to "歴史", "civics" to "公民", "inquiry" to "資料と考察"),
    )

    fun domainName(subject: String, domain: String): String? = domains[subject]?.get(domain)
}
