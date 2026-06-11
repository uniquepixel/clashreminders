package de.pixel.clashreminders.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clans")
data class ClanEntity(
    @PrimaryKey val tag: String,
    val name: String,
    val badgeUrl: String? = null,
    /** Last seen currentwar state, memory for the war-start transition trigger. */
    val lastWarState: String? = null,
    val sortOrder: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
)
