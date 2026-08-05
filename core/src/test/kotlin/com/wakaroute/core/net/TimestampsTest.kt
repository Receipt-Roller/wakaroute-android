package com.wakaroute.core.net

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimestampsTest {

    @Test
    fun `seven digit fractional seconds are accepted`() {
        // The exact shape MANABU2 sends. iOS lost a whole production
        // registration flow to a parser that rejected it.
        val parsed = ApiTimestamp.parse("2026-08-02T14:48:17.0204684+00:00")
        assertEquals("2026-08-02T14:48:17.020468400Z", parsed.toString())
    }

    @Test
    fun `a zulu timestamp is accepted`() {
        assertEquals("2026-08-02T14:48:17Z", ApiTimestamp.parse("2026-08-02T14:48:17Z").toString())
    }

    @Test
    fun `a non-UTC offset is converted rather than dropped`() {
        assertEquals(
            ApiTimestamp.parse("2026-08-02T14:48:17Z"),
            ApiTimestamp.parse("2026-08-02T23:48:17+09:00"),
        )
    }

    @Test
    fun `a timestamp with no offset is read as UTC rather than discarded`() {
        // Instant.parse throws on this shape. It is still a real timestamp, and
        // dropping it would silently lose a record.
        assertEquals(
            ApiTimestamp.parse("2026-08-02T14:48:17Z"),
            ApiTimestamp.parse("2026-08-02T14:48:17"),
        )
    }

    @Test
    fun `unreadable input yields null rather than throwing`() {
        // A timestamp we cannot read is worth degrading over, never crashing.
        assertNull(ApiTimestamp.parse(null))
        assertNull(ApiTimestamp.parse(""))
        assertNull(ApiTimestamp.parse("   "))
        assertNull(ApiTimestamp.parse("昨日"))
        assertNull(ApiTimestamp.parse("2026-13-45T99:99:99Z"))
    }

    @Test
    fun `a catalogue date stays a calendar date`() {
        // Not an instant. Converting 入試日 through UTC moves a 2月21日 exam to
        // the 20th for every student in Japan.
        assertEquals(LocalDate.of(2026, 2, 21), CatalogDate.parse("2026-02-21"))
    }

    @Test
    fun `the catalogue's slash format is accepted too`() {
        // Verified against production on 2026-08-05: one school object carries
        // "lastVerifiedAt": "2025-05-01" and "openedOn": "2020/12/22".
        assertEquals(LocalDate.of(2020, 12, 22), CatalogDate.parse("2020/12/22"))
        assertEquals(LocalDate.of(2025, 5, 1), CatalogDate.parse("2025-05-01"))
    }

    @Test
    fun `an unparseable catalogue date yields null`() {
        assertNull(CatalogDate.parse("未定"))
        assertNull(CatalogDate.parse("2026-02"))
        assertNull(CatalogDate.parse(null))
    }
}
