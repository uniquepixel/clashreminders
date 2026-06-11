package de.pixel.clashreminders.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * Clan games run on hardcoded dates each month, mirrored from lostmanager:
 * 22nd 07:00 UTC until 28th 12:00 UTC.
 */
data class ClanGamesWindow(
    val startUtcMillis: Long,
    val endUtcMillis: Long,
    /** e.g. "2026-06" */
    val windowKey: String,
) {
    fun contains(nowMillis: Long): Boolean = nowMillis in startUtcMillis until endUtcMillis
}

object ClanGamesCalendar {

    fun windowFor(yearMonth: YearMonth): ClanGamesWindow {
        val start = LocalDateTime.of(yearMonth.year, yearMonth.month, 22, 7, 0)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        val end = LocalDateTime.of(yearMonth.year, yearMonth.month, 28, 12, 0)
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        return ClanGamesWindow(start, end, "%04d-%02d".format(yearMonth.year, yearMonth.monthValue))
    }

    /** The window currently running, or null when between windows. */
    fun currentWindow(nowMillis: Long): ClanGamesWindow? {
        val month = YearMonth.from(Instant.ofEpochMilli(nowMillis).atZone(ZoneOffset.UTC))
        val window = windowFor(month)
        return window.takeIf { it.contains(nowMillis) }
    }

    /** The running window, or the next upcoming one. */
    fun currentOrNextWindow(nowMillis: Long): ClanGamesWindow {
        val month = YearMonth.from(Instant.ofEpochMilli(nowMillis).atZone(ZoneOffset.UTC))
        val window = windowFor(month)
        return if (nowMillis < window.endUtcMillis) window else windowFor(month.plusMonths(1))
    }
}
