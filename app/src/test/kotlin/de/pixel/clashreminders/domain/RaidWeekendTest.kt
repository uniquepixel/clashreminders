package de.pixel.clashreminders.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset

class RaidWeekendTest {

    private val zone = ZoneOffset.UTC

    @Test
    fun `nextFireTime picks upcoming sunday`() {
        // 2026-06-12 is a Friday
        val now = Instant.parse("2026-06-12T10:00:00Z").toEpochMilli()
        val fireAt = RaidWeekend.nextFireTime(DayOfWeek.SUNDAY, 18 * 60, now, zone)
        assertEquals(Instant.parse("2026-06-14T18:00:00Z").toEpochMilli(), fireAt)
    }

    @Test
    fun `nextFireTime rolls to next week when time already passed`() {
        val now = Instant.parse("2026-06-14T19:00:00Z").toEpochMilli() // Sunday 19:00
        val fireAt = RaidWeekend.nextFireTime(DayOfWeek.SUNDAY, 18 * 60, now, zone)
        assertEquals(Instant.parse("2026-06-21T18:00:00Z").toEpochMilli(), fireAt)
    }

    @Test
    fun `weekendKey resolves monday to previous friday`() {
        val mondayFire = Instant.parse("2026-06-15T06:00:00Z").toEpochMilli()
        val sundayFire = Instant.parse("2026-06-14T18:00:00Z").toEpochMilli()
        assertEquals("raid-2026-06-12", RaidWeekend.weekendKey(mondayFire, zone))
        assertEquals("raid-2026-06-12", RaidWeekend.weekendKey(sundayFire, zone))
    }

    @Test
    fun `isInWindow matches friday 7am to monday 7am UTC`() {
        assertFalse(RaidWeekend.isInWindow(Instant.parse("2026-06-12T06:59:00Z").toEpochMilli()))
        assertTrue(RaidWeekend.isInWindow(Instant.parse("2026-06-12T07:00:00Z").toEpochMilli()))
        assertTrue(RaidWeekend.isInWindow(Instant.parse("2026-06-14T23:00:00Z").toEpochMilli()))
        assertTrue(RaidWeekend.isInWindow(Instant.parse("2026-06-15T06:59:00Z").toEpochMilli()))
        assertFalse(RaidWeekend.isInWindow(Instant.parse("2026-06-15T07:00:00Z").toEpochMilli()))
        assertFalse(RaidWeekend.isInWindow(Instant.parse("2026-06-17T12:00:00Z").toEpochMilli()))
    }

    @Test
    fun `dayFromName only accepts weekend days`() {
        assertEquals(DayOfWeek.FRIDAY, RaidWeekend.dayFromName("FRIDAY"))
        assertEquals(DayOfWeek.MONDAY, RaidWeekend.dayFromName("MONDAY"))
        assertEquals(null, RaidWeekend.dayFromName("WEDNESDAY"))
        assertEquals(null, RaidWeekend.dayFromName(null))
    }
}
