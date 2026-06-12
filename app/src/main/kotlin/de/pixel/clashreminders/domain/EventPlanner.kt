package de.pixel.clashreminders.domain

import de.pixel.clashreminders.data.db.entity.ReminderEntity
import de.pixel.clashreminders.api.dto.CurrentWarDto

data class PlannedAlarm(
    val reminderId: Long,
    /** Clan the alarm refers to (war/CWL); null for raid and clan games. */
    val clanTag: String?,
    val type: ReminderType,
    val fireAtMillis: Long,
    val eventKey: String,
)

/**
 * Pure planning logic, port of the timestamp/overdue handling in
 * lostmanager Bot.java: fire time = event end - offset; slightly overdue
 * events still fire, anything older is silently marked as fired.
 */
object EventPlanner {

    /** Mirrors the 5-minute grace window in Bot.java lines 943-949. */
    const val OVERDUE_GRACE_MILLIS = 5 * 60 * 1000L

    sealed class Outcome {
        abstract val alarm: PlannedAlarm

        /** Fire time is in the future: set an exact alarm. */
        data class Schedule(override val alarm: PlannedAlarm) : Outcome()

        /** Fire time just passed (within grace): fire immediately. */
        data class FireNow(override val alarm: PlannedAlarm) : Outcome()

        /** Fire time long gone: record as fired without notifying. */
        data class MarkFiredSilently(override val alarm: PlannedAlarm) : Outcome()
    }

    fun planOffsetReminder(
        reminder: ReminderEntity,
        eventEndMillis: Long,
        eventKey: String,
        nowMillis: Long,
        clanTag: String? = null,
    ): Outcome {
        val fireAt = eventEndMillis - reminder.offsetMinutes * 60_000L
        val alarm = PlannedAlarm(reminder.id, clanTag, reminder.type, fireAt, eventKey)
        return when {
            fireAt > nowMillis -> Outcome.Schedule(alarm)
            nowMillis - fireAt <= OVERDUE_GRACE_MILLIS -> Outcome.FireNow(alarm)
            else -> Outcome.MarkFiredSilently(alarm)
        }
    }

    /**
     * War-start trigger condition from Bot.java: a war becomes visible when
     * the stored state was inactive and the fresh state is active. A null
     * old state (clan just discovered) must NOT fire — only store.
     */
    fun isWarStartTransition(oldState: String?, newState: String?): Boolean =
        oldState != null &&
            oldState in INACTIVE_WAR_STATES &&
            newState in ACTIVE_WAR_STATES

    private val INACTIVE_WAR_STATES = setOf(
        CurrentWarDto.STATE_NOT_IN_WAR,
        CurrentWarDto.STATE_WAR_ENDED,
    )
    private val ACTIVE_WAR_STATES = setOf(
        CurrentWarDto.STATE_PREPARATION,
        CurrentWarDto.STATE_IN_WAR,
    )
}
