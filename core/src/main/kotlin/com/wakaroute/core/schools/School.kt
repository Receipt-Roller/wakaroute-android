package com.wakaroute.core.schools

import com.wakaroute.core.net.CatalogDate
import com.wakaroute.core.net.LenientDouble
import com.wakaroute.core.net.LenientInt
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class SchoolOwnership(val wire: String, val label: String) {
    National("national", "国立"),
    Public("public", "公立"),
    Private("private", "私立");

    companion object {
        fun fromWire(value: String?): SchoolOwnership? =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) }
    }
}

/**
 * A school as the ワカルート catalogue returns it.
 *
 * [id] is the permanent key, and the サービス仕様 states it never changes after
 * publication. Names are not identifiers: they are not unique, and they change
 * when a school merges or is renamed.
 *
 * Almost every field is nullable because the catalogue is generated from the
 * 文部科学省 school code CSV, where columns are genuinely absent for some
 * entries — 分校 in particular. Declaring them non-null would turn a real,
 * documented gap into a decode failure for the whole page.
 */
@Serializable
data class School(
    val id: String,
    val name: String,
    val nameKana: String? = null,
    val prefectureCode: String? = null,
    val prefecture: String? = null,
    val address: String? = null,
    val postalCode: String? = null,
    @SerialName("ownership") private val ownershipWire: String? = null,
    val ownershipLabel: String? = null,
    val campusTypeLabel: String? = null,
    val officialUrl: String? = null,
    @Serializable(with = LenientDouble::class) val latitude: Double? = null,
    @Serializable(with = LenientDouble::class) val longitude: Double? = null,
    val tags: List<String> = emptyList(),
) {
    val ownership: SchoolOwnership? get() = SchoolOwnership.fromWire(ownershipWire)

    /**
     * What to show as the 設置区分.
     *
     * The server's own label wins where it exists. It knows about cases our
     * enum does not, and disagreeing with the web site about whether a school
     * is 公立 would be worse than not classifying it at all.
     */
    val ownershipDisplay: String? get() = ownershipLabel ?: ownership?.label
}

@Serializable
data class SchoolSearchPage(
    /**
     * The date the catalogue itself is current as of — not when we fetched it.
     * §7 requires both to be kept, so a list is never mistaken for live data.
     */
    val asOf: String? = null,
    @Serializable(with = LenientInt::class) val totalCount: Int = 0,
    @Serializable(with = LenientInt::class) val page: Int = 1,
    @Serializable(with = LenientInt::class) val pageSize: Int = DEFAULT_PAGE_SIZE,
    @Serializable(with = LenientInt::class) val totalPages: Int = 0,
    val items: List<School> = emptyList(),
) {
    val hasMorePages: Boolean get() = page < totalPages
}

/**
 * The parameters of one search.
 *
 * Clamped in the constructor rather than at the call site: the API rejects a
 * `pageSize` above 48, and a screen that computed one from a grid width would
 * turn a layout change into a 400.
 */
data class SchoolSearchQuery(
    val keyword: String? = null,
    val prefectureCode: String? = null,
    val ownership: SchoolOwnership? = null,
    val page: Int = 1,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    val normalizedPage: Int = page.coerceAtLeast(1)
    val normalizedPageSize: Int = pageSize.coerceIn(1, MAX_PAGE_SIZE)

    /** True when nothing narrows the search — the whole catalogue. */
    val isEmpty: Boolean
        get() = keyword.isNullOrBlank() && prefectureCode.isNullOrBlank() && ownership == null

    fun queryParameters(): List<Pair<String, String>> = buildList {
        keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { add("q" to it) }
        prefectureCode?.trim()?.takeIf { it.isNotEmpty() }?.let { add("prefecture" to it) }
        ownership?.let { add("ownership" to it.wire) }
        add("page" to normalizedPage.toString())
        add("pageSize" to normalizedPageSize.toString())
    }
}

const val DEFAULT_PAGE_SIZE = 24

/** The API caps this and rejects anything larger. */
const val MAX_PAGE_SIZE = 48

/**
 * `GET /api/schools/{id}`.
 *
 * Every collection here was empty for every school sampled by the iOS team on
 * 2026-08-02: the catalogue publishes its structure before the data exists. So
 * they default to empty rather than failing, and the UI must treat absence as
 * ordinary rather than as an error.
 */
@Serializable
data class SchoolDetail(
    val school: School,
    val examSchedules: List<SchoolExamSchedule> = emptyList(),
    val admissions: List<SchoolAdmissionResult> = emptyList(),
    val deviationScores: List<SchoolDeviationScore> = emptyList(),
) {
    /** The most recent 偏差値, whichever provider published it. */
    val latestDeviationScore: SchoolDeviationScore?
        get() = deviationScores.maxByOrNull { it.academicYear }

    /**
     * The earliest sitting still ahead of [today].
     *
     * A catalogue carrying several years would otherwise offer a date that has
     * already passed.
     */
    fun nextExamDate(today: LocalDate): LocalDate? =
        examSchedules
            .flatMap { it.testDates }
            .mapNotNull(CatalogDate::parse)
            .filter { !it.isBefore(today) }
            .minOrNull()
}

/** 入試日程 for one selection (推薦, 一般, …) in one academic year. */
@Serializable
data class SchoolExamSchedule(
    @Serializable(with = LenientInt::class) val academicYear: Int = 0,
    val selectionLabel: String? = null,
    /** 未発表 / 発表済. A schedule exists before its dates are fixed. */
    val statusLabel: String? = null,
    val applicationPeriod: String? = null,
    /** `yyyy-MM-dd`, more than one when a selection runs over several days. */
    val testDates: List<String> = emptyList(),
    val resultDate: String? = null,
    val notes: List<String> = emptyList(),
)

@Serializable
data class SchoolDeviationScore(
    @Serializable(with = LenientInt::class) val academicYear: Int = 0,
    val provider: String? = null,
    @Serializable(with = LenientDouble::class) val value: Double? = null,
    @Serializable(with = LenientDouble::class) val valueLow: Double? = null,
    @Serializable(with = LenientDouble::class) val valueHigh: Double? = null,
    /** Which cohort the figure describes. A bare number means little without it. */
    val population: String? = null,
    val sourceUrl: String? = null,
) {
    val displayText: String?
        get() = when {
            value != null -> format(value)
            valueLow != null && valueHigh != null -> "${format(valueLow)}〜${format(valueHigh)}"
            else -> (valueLow ?: valueHigh)?.let(::format)
        }

    private fun format(number: Double): String = number.toInt().toString()
}

/** 入試結果 — applicants against capacity, per selection and department. */
@Serializable
data class SchoolAdmissionResult(
    @Serializable(with = LenientInt::class) val academicYear: Int = 0,
    val selectionLabel: String? = null,
    val department: String? = null,
    @Serializable(with = LenientInt::class) val capacity: Int? = null,
    @Serializable(with = LenientInt::class) val applicants: Int? = null,
    @Serializable(with = LenientInt::class) val examinees: Int? = null,
    @Serializable(with = LenientInt::class) val admitted: Int? = null,
    val note: String? = null,
) {
    /**
     * 倍率, carrying which of the three it is.
     *
     * These are not interchangeable and must never share a label. For a school
     * with 312 applicants, 300 examinees, 240 places and 245 admitted:
     * 志願倍率 1.30, 受験倍率 1.25, 実質倍率 1.22. Printing 1.22 as plain 「倍率」
     * beside a published 志願倍率 of 1.30 looks like an error — or is taken as one.
     */
    data class CompetitionRatio(val value: Double, val kind: Kind) {
        enum class Kind(val label: String, val explanation: String) {
            /** 受験者 ÷ 合格者 — what a candidate actually faced. */
            Actual("実質倍率", "受験した人数を合格した人数で割った、実際の競争率です。"),
            Examinee("受験倍率", "受験した人数を募集定員で割った値です。"),
            /** Published before the exam, so withdrawals usually inflate it. */
            Applicant("志願倍率", "出願した人数を募集定員で割った、入試前の値です。"),
        }
    }

    /** The most informative ratio the published numbers support. */
    val competitionRatio: CompetitionRatio?
        get() = when {
            examinees != null && admitted != null && admitted > 0 ->
                CompetitionRatio(examinees.toDouble() / admitted, CompetitionRatio.Kind.Actual)

            examinees != null && capacity != null && capacity > 0 ->
                CompetitionRatio(examinees.toDouble() / capacity, CompetitionRatio.Kind.Examinee)

            applicants != null && capacity != null && capacity > 0 ->
                CompetitionRatio(applicants.toDouble() / capacity, CompetitionRatio.Kind.Applicant)

            else -> null
        }
}
