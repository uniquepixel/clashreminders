package de.pixel.clashreminders.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.pixel.clashreminders.domain.ReminderType

/**
 * Mirror of every AlarmManager alarm currently set, so BootReceiver can
 * re-arm them after reboot/update without touching the network.
 *
 * One reminder can have several alarms at once (a war-end reminder arms one
 * alarm per clan with a running war), so request codes are allocated by the
 * planner and kept stable per (reminderId, eventKey) across refreshes.
 */
@Entity(
    tableName = "scheduled_alarms",
    indices = [Index(value = ["reminderId", "eventKey"], unique = true)],
)
data class ScheduledAlarmEntity(
    @PrimaryKey val requestCode: Int,
    val reminderId: Long,
    /** Clan the alarm refers to (war/CWL); null for raid, clan games and the snapshot alarm. */
    val clanTag: String?,
    val type: ReminderType,
    val fireAtMillis: Long,
    val eventKey: String,
) {
    companion object {
        const val SNAPSHOT_REQUEST_CODE = -1
        const val SNAPSHOT_REMINDER_ID = -1L
    }
}
