package de.pixel.clashreminders.domain

import de.pixel.clashreminders.data.db.entity.ReminderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventPlannerTest {

    private fun reminder(offsetMinutes: Int) = ReminderEntity(
        id = 7,
        type = ReminderType.WAR_END,
        offsetMinutes = offsetMinutes,
    )

    @Test
    fun `future fire time is scheduled`() {
        val now = 1_000_000_000_000L
        val end = now + 4 * 60 * 60 * 1000L
        val outcome = EventPlanner.planOffsetReminder(reminder(120), end, "war-$end", now, "#TEST")
        assertTrue(outcome is EventPlanner.Outcome.Schedule)
        assertEquals(end - 120 * 60_000L, outcome.alarm.fireAtMillis)
        assertEquals("war-$end", outcome.alarm.eventKey)
        assertEquals(7L, outcome.alarm.reminderId)
        assertEquals("#TEST", outcome.alarm.clanTag)
    }

    @Test
    fun `slightly overdue fires immediately`() {
        val now = 1_000_000_000_000L
        val end = now + 120 * 60_000L - 3 * 60_000L // fire time was 3 min ago
        val outcome = EventPlanner.planOffsetReminder(reminder(120), end, "k", now)
        assertTrue(outcome is EventPlanner.Outcome.FireNow)
    }

    @Test
    fun `long overdue is marked fired silently`() {
        val now = 1_000_000_000_000L
        val end = now + 120 * 60_000L - 10 * 60_000L // fire time was 10 min ago
        val outcome = EventPlanner.planOffsetReminder(reminder(120), end, "k", now)
        assertTrue(outcome is EventPlanner.Outcome.MarkFiredSilently)
    }

    @Test
    fun `war start transition fires only from inactive to active`() {
        assertTrue(EventPlanner.isWarStartTransition("notInWar", "preparation"))
        assertTrue(EventPlanner.isWarStartTransition("notInWar", "inWar"))
        assertTrue(EventPlanner.isWarStartTransition("warEnded", "preparation"))
        assertFalse(EventPlanner.isWarStartTransition("preparation", "inWar"))
        assertFalse(EventPlanner.isWarStartTransition("inWar", "inWar"))
        assertFalse(EventPlanner.isWarStartTransition(null, "preparation"))
        assertFalse(EventPlanner.isWarStartTransition("notInWar", "notInWar"))
        assertFalse(EventPlanner.isWarStartTransition("notInWar", null))
    }
}
