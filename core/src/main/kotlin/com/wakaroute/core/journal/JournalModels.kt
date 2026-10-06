package com.wakaroute.core.journal

import com.wakaroute.core.net.LenientInt
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

/**
 * Days in the 受験日記, as the API counts them: **Japanese calendar days**.
 *
 * Carried as `yyyy-MM-dd` strings in the models, exactly as the server writes
 * them, and as [LocalDate] in logic. Never as instants: 00:30 in Japan is still
 * yesterday in UTC, and a diary written after midnight would land on the wrong day.
 */
object JournalDates {
    val zone: ZoneId = ZoneId.of("Asia/Tokyo")

    /** How far back the server accepts a write. */
    const val WRITABLE_DAYS_BACK = 31L

    fun today(): LocalDate = LocalDate.now(zone)

    /** Checked before sending, so the student hears it before typing a paragraph. */
    fun isWritable(date: LocalDate, today: LocalDate = today()): Boolean {
        val daysAgo = ChronoUnit.DAYS.between(date, today)
        return daysAgo in 0..WRITABLE_DAYS_BACK
    }
}

/** The server's limits on one day. */
object JournalLimits {
    /** A day holds 24 hours and no more (`day_full`). */
    const val MINUTES_PER_DAY = 1440
    const val ENTRIES_PER_DAY = 50
    const val DIARY_TEXT_LENGTH = 1000
    const val SUBJECT_LENGTH = 60
    const val CONTENT_LENGTH = 200
}

/**
 * The kinds of time block a student can log.
 *
 * Kept as the server's code string rather than an enum, so a category added
 * after this build shipped arrives as data — not as a decoding failure that
 * blanks out the student's day.
 */
object JournalCategories {
    const val SCHOOL = "school"
    const val CLUB = "club"
    const val CRAM_SCHOOL = "cram_school"
    const val SELF_STUDY = "self_study"
    const val HOMEWORK = "homework"
    const val SCREEN = "screen"
    const val SLEEP = "sleep"
    const val FREE = "free"
    const val OTHER = "other"

    /** In the order they are offered. The same order as iOS. */
    val known = listOf(SELF_STUDY, HOMEWORK, CRAM_SCHOOL, SCHOOL, CLUB, SCREEN, SLEEP, FREE, OTHER)

    /**
     * Which count toward `studySelfMinutes`. Held here as well as on the server,
     * so a block queued offline is added up the same way before it is uploaded.
     */
    fun countsAsStudy(code: String) = code == CRAM_SCHOOL || code == SELF_STUDY || code == HOMEWORK

    fun displayName(code: String) = when (code) {
        SCHOOL -> "学校"
        CLUB -> "部活"
        CRAM_SCHOOL -> "塾・習い事"
        SELF_STUDY -> "自主学習"
        HOMEWORK -> "宿題"
        SCREEN -> "ゲーム・動画・SNS"
        SLEEP -> "睡眠"
        FREE -> "自由時間"
        OTHER -> "その他"
        else -> code
    }
}

/** 受験日記 — what the student wrote about a day. */
@Serializable
data class DiaryEntry(
    val date: String,
    /** できたこと */
    val achievements: String? = null,
    /** 困ったこと */
    val struggles: String? = null,
    /** 明日やること */
    val tomorrowPlan: String? = null,
    /** 集中度 1–5 */
    @Serializable(with = LenientInt::class) val focus: Int? = null,
    /** 疲労 1–5 */
    @Serializable(with = LenientInt::class) val fatigue: Int? = null,
    val updatedAt: String? = null,
)

/**
 * What the edit form sends back.
 *
 * `PUT /{date}/diary` **replaces** the whole diary — anything left out is
 * cleared. So the form is always opened from the current diary, never from
 * nothing: writing only 明日やること would otherwise erase this morning's できたこと.
 */
@Serializable
data class DiaryDraft(
    val achievements: String = "",
    val struggles: String = "",
    val tomorrowPlan: String = "",
    val focus: Int? = null,
    val fatigue: Int? = null,
) {
    /** An entirely empty diary is a 400, not a save. It is a delete. */
    val isEmpty: Boolean
        get() = listOf(achievements, struggles, tomorrowPlan).all { it.isBlank() } && focus == null && fatigue == null

    val isOverLimit: Boolean
        get() = listOf(achievements, struggles, tomorrowPlan).any { it.length > JournalLimits.DIARY_TEXT_LENGTH }

    companion object {
        fun from(diary: DiaryEntry?) = DiaryDraft(
            achievements = diary?.achievements.orEmpty(),
            struggles = diary?.struggles.orEmpty(),
            tomorrowPlan = diary?.tomorrowPlan.orEmpty(),
            focus = diary?.focus,
            fatigue = diary?.fatigue,
        )
    }
}

/** A block of time the student logged themselves. */
@Serializable
data class DayLogEntry(
    val entryId: String,
    val clientEntryId: String? = null,
    val date: String = "",
    val category: String = "",
    @Serializable(with = LenientInt::class) val durationMinutes: Int = 0,
    val subject: String? = null,
    val content: String? = null,
)

/**
 * A new block, on its way to `POST /{date}/entries`.
 *
 * [clientEntryId] is made once and never again. A block posted over a dying
 * connection can land on the server and still look failed here; sending the
 * same id again returns the original row (verified on production) instead of
 * logging 40 minutes of homework twice.
 */
@Serializable
data class NewDayLogEntry(
    val clientEntryId: String,
    val category: String,
    val durationMinutes: Int,
    val subject: String? = null,
    val content: String? = null,
)

/**
 * Study MANABU2 recorded by itself — the study timer. Shown so the day reads
 * whole, and never mistaken for the student's own entry.
 */
@Serializable
data class AutoStudyEntry(
    val sessionId: String = "",
    val subject: String? = null,
    @Serializable(with = LenientInt::class) val durationMinutes: Int = 0,
)

@Serializable
data class PracticeSummary(
    @Serializable(with = LenientInt::class) val sessions: Int = 0,
    @Serializable(with = LenientInt::class) val answered: Int = 0,
    @Serializable(with = LenientInt::class) val correct: Int = 0,
)

/**
 * The day's arithmetic, as the server does it.
 *
 * **[studySelfMinutes] and [studyAutoMinutes] are never added together.** A
 * student who runs the timer and also logs 自主学習 for the same hour studied
 * for one hour, not two, and nothing can tell how much overlaps.
 */
@Serializable
data class DayTotals(
    val minutesByCategory: Map<String, Int> = emptyMap(),
    @Serializable(with = LenientInt::class) val studySelfMinutes: Int = 0,
    @Serializable(with = LenientInt::class) val studyAutoMinutes: Int = 0,
)

/** One day, whole — the entire day screen in one request. */
@Serializable
data class JournalDay(
    val date: String,
    val diary: DiaryEntry? = null,
    val entries: List<DayLogEntry> = emptyList(),
    val autoStudy: List<AutoStudyEntry> = emptyList(),
    val practice: PracticeSummary? = null,
    val totals: DayTotals = DayTotals(),
)

/**
 * A day **without the diary's words** — what the week strip is built from, so
 * it stays safe on screen with somebody looking over the student's shoulder.
 */
@Serializable
data class JournalDaySummary(
    val date: String,
    val minutesByCategory: Map<String, Int> = emptyMap(),
    @Serializable(with = LenientInt::class) val studySelfMinutes: Int = 0,
    @Serializable(with = LenientInt::class) val studyAutoMinutes: Int = 0,
    val hasDiary: Boolean = false,
) {
    val loggedMinutes: Int get() = minutesByCategory.values.sum()
}

/** Where a study figure came from. */
enum class StudySource(val displayName: String) {
    /** 自主学習・宿題・塾 — including everything done away from the app. */
    Learner("自分で記録"),

    /** The study timer. */
    App("アプリが記録"),
}

/**
 * Which of the two study figures a screen leads with, when it can only lead
 * with one. **The student's own record wins** — the timer never sees 塾 or a
 * paper problem book — **except on a day they logged nothing**, where printing
 * 「0分」 above a 90-minute timer session would be wrong.
 */
fun headlineStudySource(selfMinutes: Int, autoMinutes: Int): StudySource =
    if (selfMinutes == 0 && autoMinutes > 0) StudySource.App else StudySource.Learner
