package de.pixel.clashreminders.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.pixel.clashreminders.domain.ReminderType

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = ClanEntity::class,
            parentColumns = ["tag"],
            childColumns = ["clanTag"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("clanTag")],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clanTag: String,
    val type: ReminderType,
    /** Lead time for WAR_END / CWL_DAY_END / CLAN_GAMES_END; unused for WAR_START and RAID. */
    val offsetMinutes: Int = 0,
    val enabled: Boolean = true,
    /** CLAN_GAMES_END only: members below this point diff get listed. */
    val cgThreshold: Int? = null,
    /** RAID only: java.time.DayOfWeek name, FRIDAY..MONDAY. */
    val raidDay: String? = null,
    /** RAID only: minutes since local midnight. */
    val raidTimeMinutes: Int? = null,
)
