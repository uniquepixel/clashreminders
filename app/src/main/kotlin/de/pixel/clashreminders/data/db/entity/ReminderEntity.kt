package de.pixel.clashreminders.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.pixel.clashreminders.domain.ReminderType

/**
 * A global reminder rule. Reminders are user-based: they apply to all
 * accounts at once and only fire when at least one account still has
 * something open — war and CWL reminders cover every clan an account is
 * in, raid reminders check all of those clans in a single notification.
 */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ReminderType,
    /** Lead time for WAR_END / CWL_DAY_END / CLAN_GAMES_END; unused for WAR_START and RAID. */
    val offsetMinutes: Int = 0,
    val enabled: Boolean = true,
    /** CLAN_GAMES_END only: accounts below this point diff get listed. */
    val cgThreshold: Int? = null,
    /** RAID only: java.time.DayOfWeek name, FRIDAY..MONDAY. */
    val raidDay: String? = null,
    /** RAID only: minutes since local midnight. */
    val raidTimeMinutes: Int? = null,
)
