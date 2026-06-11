package de.pixel.clashreminders.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Raid weekends run Friday 07:00 UTC until Monday 07:00 UTC. Raid reminders
 * fire at a fixed local weekday + time instead of an offset before the end
 * (the end hour shifts with the timezone, which is too error-prone).
 */
object RaidWeekend {

    val allowedDays = listOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY)

    /**
     * Next occurrence of the configured weekday + local time, strictly after [nowMillis].
     */
    fun nextFireTime(day: DayOfWeek, timeMinutes: Int, nowMillis: Long, zone: ZoneId): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val time = LocalTime.of(timeMinutes / 60, timeMinutes % 60)
        var candidate = now.toLocalDate()
            .with(TemporalAdjusters.nextOrSame(day))
            .atTime(time)
            .atZone(zone)
        if (!candidate.toInstant().isAfter(Instant.ofEpochMilli(nowMillis))) {
            candidate = candidate.plusWeeks(1)
        }
        return candidate.toInstant().toEpochMilli()
    }

    /**
     * Dedupe key for the raid weekend a fire time belongs to: the date of
     * that weekend's Friday. Monday fire times resolve to the Friday 3 days back.
     */
    fun weekendKey(fireAtMillis: Long, zone: ZoneId): String {
        val date = Instant.ofEpochMilli(fireAtMillis).atZone(zone).toLocalDate()
        val friday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY))
        return "raid-" + friday.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    /** Whether [nowMillis] falls inside a raid weekend (UTC window). */
    fun isInWindow(nowMillis: Long): Boolean {
        val nowUtc = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneOffset.UTC)
        val friday = nowUtc.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY))
        val start = friday.atTime(7, 0)
        val end = friday.plusDays(3).atTime(7, 0)
        return !nowUtc.isBefore(start) && nowUtc.isBefore(end)
    }

    fun dayFromName(name: String?): DayOfWeek? =
        name?.let { n -> allowedDays.firstOrNull { it.name == n } }

    fun formatTime(timeMinutes: Int): String =
        "%02d:%02d".format(timeMinutes / 60, timeMinutes % 60)
}

private fun LocalDate.atTime(hour: Int, minute: Int): LocalDateTime =
    LocalDateTime.of(this, LocalTime.of(hour, minute))
