package de.pixel.clashreminders.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

class ClanGamesWindowTest {

    @Test
    fun `window runs from 22nd 7am to 28th noon UTC`() {
        val window = ClanGamesCalendar.windowFor(YearMonth.of(2026, 6))
        assertEquals(Instant.parse("2026-06-22T07:00:00Z").toEpochMilli(), window.startUtcMillis)
        assertEquals(Instant.parse("2026-06-28T12:00:00Z").toEpochMilli(), window.endUtcMillis)
        assertEquals("2026-06", window.windowKey)
    }

    @Test
    fun `currentWindow returns window only while running`() {
        val inside = Instant.parse("2026-06-25T10:00:00Z").toEpochMilli()
        val before = Instant.parse("2026-06-21T10:00:00Z").toEpochMilli()
        val after = Instant.parse("2026-06-28T13:00:00Z").toEpochMilli()
        assertNotNull(ClanGamesCalendar.currentWindow(inside))
        assertNull(ClanGamesCalendar.currentWindow(before))
        assertNull(ClanGamesCalendar.currentWindow(after))
    }

    @Test
    fun `currentOrNextWindow rolls over to next month after the end`() {
        val after = Instant.parse("2026-06-28T13:00:00Z").toEpochMilli()
        val window = ClanGamesCalendar.currentOrNextWindow(after)
        assertEquals("2026-07", window.windowKey)
    }

    @Test
    fun `currentOrNextWindow keeps current month before and during the window`() {
        val before = Instant.parse("2026-06-10T00:00:00Z").toEpochMilli()
        assertEquals("2026-06", ClanGamesCalendar.currentOrNextWindow(before).windowKey)
        val during = Instant.parse("2026-06-23T00:00:00Z").toEpochMilli()
        assertEquals("2026-06", ClanGamesCalendar.currentOrNextWindow(during).windowKey)
    }

    @Test
    fun `contains is start-inclusive end-exclusive`() {
        val window = ClanGamesCalendar.windowFor(YearMonth.of(2026, 6))
        assertTrue(window.contains(window.startUtcMillis))
        assertFalse(window.contains(window.endUtcMillis))
    }
}
