package com.wakaroute.core.goals

import com.wakaroute.core.net.LenientInt
import kotlinx.serialization.Serializable

/**
 * A 志望校.
 *
 * Stored against MANABU2 as a generic "goal", which is why the wire names are
 * `externalId` and `source` rather than anything school-shaped. [externalId] is
 * the ワカルート catalogue id and is the key; [name] is a cached copy for display
 * so a list can be drawn without a second round trip per school.
 */
@Serializable
data class TargetSchool(
    /** `high_school`. Present because the same endpoint serves other goal types. */
    val type: String = HIGH_SCHOOL,
    /** Which catalogue [externalId] belongs to. Always `wakaroute` for us. */
    val source: String = WAKAROUTE,
    /** The ワカルート school id. The key — never match on [name]. */
    val externalId: String,
    /**
     * The school's name as it was when the student added it.
     *
     * A cache, not an identifier. A school that is renamed keeps its id and
     * therefore keeps the student's goal; only the label goes stale, and it is
     * refreshed the next time the list is written.
     *
     * Defaulted because the API's own `GoalWriteDto` declares `name` as
     * nullable, so a null can be stored by any client and read back by us.
     * §6 says an undecodable response must not be a crash, and here the
     * alternative is worse than it sounds: one null name would fail the whole
     * list, and the student would lose every 志望校 rather than one label.
     */
    val name: String = "",
    /** 第一志望 is 0. Assigned by the server from the order it is sent. */
    @Serializable(with = LenientInt::class) val rank: Int = 0,
    /** 入試日, `yyyy-MM-dd`. Null when the school has not published one. */
    val targetDate: String? = null,
) {
    companion object {
        const val HIGH_SCHOOL = "high_school"
        const val WAKAROUTE = "wakaroute"

        /** The API rejects anything longer. */
        const val MAX_NAME_LENGTH = 200
        const val MAX_EXTERNAL_ID_LENGTH = 100
    }
}

/**
 * The whole list, plus what the server computed from it.
 *
 * [daysRemaining] is **the server's number** and is shown as-is. Recomputing it
 * on the device would sooner or later disagree — a phone in a different
 * timezone, or one whose clock is wrong — and two screens giving a student
 * different counts to their exam is worse than either number alone.
 */
@Serializable
data class TargetSchoolList(
    val goals: List<TargetSchool> = emptyList(),
    /** The earliest 入試日 among the goals. */
    val bindingDeadline: String? = null,
    @Serializable(with = LenientInt::class) val daysRemaining: Int? = null,
) {
    val isEmpty: Boolean get() = goals.isEmpty()

    /** 第一志望. */
    val first: TargetSchool? get() = goals.minByOrNull { it.rank }

    fun contains(schoolId: String): Boolean = goals.any { it.externalId == schoolId }

    companion object {
        val Empty = TargetSchoolList()
    }
}

/** The write shape. `rank` is absent on purpose — the server derives it from order. */
@Serializable
internal data class TargetSchoolWrite(
    val source: String,
    val externalId: String,
    val name: String,
    val targetDate: String? = null,
)

@Serializable
internal data class TargetSchoolListWrite(
    val type: String,
    val goals: List<TargetSchoolWrite>,
)
