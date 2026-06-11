package de.pixel.clashreminders.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.pixel.clashreminders.domain.ReminderType

/**
 * Mirror of every AlarmManager alarm currently set, so BootReceiver can
 * re-arm them after reboot/update without touching the network.
 */
@Entity(tableName = "scheduled_alarms")
data class ScheduledAlarmEntity(
    /** reminderId.toInt(); the global clan games snapshot alarm uses [SNAPSHOT_REQUEST_CODE]. */
    @PrimaryKey val requestCode: Int,
    val reminderId: Long,
    val clanTag: String,
    val type: ReminderType,
    val fireAtMillis: Long,
    val eventKey: String,
) {
    companion object {
        const val SNAPSHOT_REQUEST_CODE = -1
        const val SNAPSHOT_REMINDER_ID = -1L
    }
}
