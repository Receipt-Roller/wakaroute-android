package com.wakaroute.core.net

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Parses the timestamps the backend actually sends.
 *
 * MANABU2 is .NET and emits seven-digit fractional seconds
 * (`2026-08-02T14:48:17.0204684+00:00`). `java.time` accepts up to nine, so
 * that alone is not the hazard here that it was on iOS — where the built-in
 * ISO 8601 strategy rejected it and a whole production registration flow failed
 * on devices that had passed every test.
 *
 * What does bite on Android is the shape *without* an offset. `Instant.parse`
 * requires one and throws; the value is a real timestamp and dropping it would
 * silently lose a record. Those are read as UTC, which is what the server means
 * by an unqualified time.
 *
 * Returns null rather than throwing. A timestamp we cannot read is worth
 * degrading over — never worth a crash.
 */
object ApiTimestamp {

    fun parse(text: String?): Instant? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        return parseWithOffset(trimmed) ?: parseWithoutOffset(trimmed)
    }

    private fun parseWithOffset(text: String): Instant? = try {
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(text, Instant::from)
    } catch (e: DateTimeParseException) {
        null
    }

    private fun parseWithoutOffset(text: String): Instant? = try {
        LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC)
    } catch (e: DateTimeParseException) {
        null
    }
}

/**
 * The catalogue writes plain `yyyy-MM-dd` for 入試日 and 開校日 — a calendar date
 * with no time and no zone.
 *
 * Kept apart from [ApiTimestamp] on purpose. Turning a 入試日 into an instant
 * means choosing a timezone, and choosing UTC would move a 2月21日 exam to the
 * 20th for every student in Japan. That is the same class of bug as the
 * UTC-aggregated 「本日の売上推移」 filed against another Android app in RR26-Dev.
 */
object CatalogDate {

    /**
     * Two separators, because the catalogue uses both.
     *
     * Verified against production on 2026-08-05: the same school object carries
     * `"lastVerifiedAt": "2025-05-01"` and `"openedOn": "2020/12/22"`. Accepting
     * only the hyphenated form would drop every date of the other kind, and
     * silently — a null date looks exactly like a date the catalogue has not
     * published yet, which is a state that genuinely occurs.
     */
    fun parse(text: String?): LocalDate? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        return parseUsing(ISO, trimmed) ?: parseUsing(SLASHED, trimmed)
    }

    private fun parseUsing(formatter: DateTimeFormatter, text: String): LocalDate? = try {
        LocalDate.parse(text, formatter)
    } catch (e: DateTimeParseException) {
        null
    }

    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val SLASHED: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu/MM/dd")
}
