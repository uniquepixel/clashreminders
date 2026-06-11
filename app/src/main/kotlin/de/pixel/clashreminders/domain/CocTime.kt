package de.pixel.clashreminders.domain

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Parser for the CoC API timestamp format, same pattern lostmanager uses. */
object CocTime {

    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'")

    fun parseMillis(value: String): Long =
        LocalDateTime.parse(value, formatter).toInstant(ZoneOffset.UTC).toEpochMilli()

    fun parseMillisOrNull(value: String?): Long? =
        value?.let {
            try {
                parseMillis(it)
            } catch (_: Exception) {
                null
            }
        }
}
