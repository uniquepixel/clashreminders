package de.pixel.clashreminders.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class CocTimeTest {

    @Test
    fun `parses CoC API timestamp as UTC`() {
        val millis = CocTime.parseMillis("20260612T071234.000Z")
        assertEquals(Instant.parse("2026-06-12T07:12:34Z").toEpochMilli(), millis)
    }

    @Test
    fun `parseMillisOrNull returns null for garbage`() {
        assertNull(CocTime.parseMillisOrNull("not-a-date"))
        assertNull(CocTime.parseMillisOrNull(null))
    }

    @Test
    fun `parseMillisOrNull parses valid input`() {
        assertEquals(
            Instant.parse("2026-01-01T00:00:00Z").toEpochMilli(),
            CocTime.parseMillisOrNull("20260101T000000.000Z"),
        )
    }
}
