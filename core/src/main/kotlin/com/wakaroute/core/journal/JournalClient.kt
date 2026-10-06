package com.wakaroute.core.journal

import com.wakaroute.core.config.AppEnvironment
import com.wakaroute.core.net.ApiError
import com.wakaroute.core.net.HttpClient
import com.wakaroute.core.net.HttpRequest
import com.wakaroute.core.net.ProblemDetails
import com.wakaroute.core.net.WakaRouteJson
import com.wakaroute.core.net.retryingReads
import com.wakaroute.core.net.sendDecoding
import com.wakaroute.core.net.sendExpectingNoContent
import java.time.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Why the server refused a write, in terms a screen can act on.
 *
 * From `code`, never from `detail` — that is prose and may change.
 */
enum class JournalRefusal {
    /** In the future, or more than 31 days ago. */
    DateNotWritable,

    /** The day would hold more than 24 hours. */
    DayFull,

    /** The day already holds 50 blocks. */
    TooManyEntries,

    /** Malformed: text over the limit, a duration out of range. */
    InvalidRequest;

    companion object {
        fun of(error: ApiError): JournalRefusal? = when ((error as? ApiError.Http)?.problem?.code) {
            "date_not_writable" -> DateNotWritable
            "day_full" -> DayFull
            "too_many_entries" -> TooManyEntries
            "invalid_request" -> InvalidRequest
            else -> null
        }
    }
}

/**
 * 受験日記 and the day's time log, as MANABU2 stores them (`/api/v1/me/journal`).
 *
 * The app keeps no copy of a diary that could disagree with the server's. The
 * one thing held on the device is [JournalOutbox] — writes not yet uploaded.
 */
class JournalClient(
    private val http: HttpClient,
    private val environment: AppEnvironment,
) {
    suspend fun day(date: LocalDate): JournalDay = retryingReads {
        http.sendDecoding(get("/$date"), JournalDay.serializer())
    }

    /** Oldest first, one element per day including empty ones, and no diary text. */
    suspend fun summary(from: LocalDate, to: LocalDate): List<JournalDaySummary> = retryingReads {
        http.sendDecoding(get("/summary?from=$from&to=$to"), ListSerializer(JournalDaySummary.serializer()))
    }

    /**
     * Replaces the day's diary. An empty draft is a delete — the server answers
     * 400 to a diary with nothing in it, and "I cleared it all" means delete.
     */
    suspend fun saveDiary(draft: DiaryDraft, date: LocalDate) {
        if (draft.isEmpty) return deleteDiary(date)
        requireWritable(date)

        // Every field, every time, with blanks as explicit nulls: the endpoint
        // replaces the whole diary, and an empty string would make `hasDiary`
        // true for a diary with no words in it.
        val body = buildJsonObject {
            put("achievements", draft.achievements.trimmedOrNull())
            put("struggles", draft.struggles.trimmedOrNull())
            put("tomorrowPlan", draft.tomorrowPlan.trimmedOrNull())
            put("focus", draft.focus?.let(::JsonPrimitive) ?: JsonNull)
            put("fatigue", draft.fatigue?.let(::JsonPrimitive) ?: JsonNull)
        }
        http.sendDecoding(write(HttpRequest.Method.PUT, "/$date/diary", body.toString()), DiaryEntry.serializer())
    }

    suspend fun deleteDiary(date: LocalDate) {
        requireWritable(date)
        http.sendExpectingNoContent(write(HttpRequest.Method.DELETE, "/$date/diary"))
    }

    /** Safe to repeat with the same entry after a timeout; see [NewDayLogEntry]. */
    suspend fun addEntry(entry: NewDayLogEntry, date: LocalDate): DayLogEntry {
        requireWritable(date)
        val body = WakaRouteJson.encodeToString(NewDayLogEntry.serializer(), entry)
        return http.sendDecoding(write(HttpRequest.Method.POST, "/$date/entries", body), DayLogEntry.serializer())
    }

    suspend fun deleteEntry(entryId: String) {
        http.sendExpectingNoContent(write(HttpRequest.Method.DELETE, "/entries/$entryId"))
    }

    /** Refuses a write the server is certain to refuse, before the student loses a paragraph to it. */
    private fun requireWritable(date: LocalDate) {
        if (!JournalDates.isWritable(date)) {
            throw ApiError.Http(400, ProblemDetails(status = 400, code = "date_not_writable"))
        }
    }

    private fun url(path: String) = environment.manabu2BaseUrl.trimEnd('/') + "/api/v1/me/journal" + path

    private fun get(path: String) =
        HttpRequest(HttpRequest.Method.GET, url(path), mapOf("Accept" to "application/json"))

    private fun write(method: HttpRequest.Method, path: String, body: String? = null) = HttpRequest(
        method = method,
        url = url(path),
        headers = mapOf("Accept" to "application/json", "Content-Type" to "application/json"),
        body = body,
    )

    private fun String.trimmedOrNull() = trim().takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull
}
