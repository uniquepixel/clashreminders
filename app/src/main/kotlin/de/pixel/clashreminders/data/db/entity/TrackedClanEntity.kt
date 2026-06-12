package de.pixel.clashreminders.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A clan that is checked because at least one account is currently in it.
 * Rows are derived automatically by RefreshWorker — the user never manages
 * clans directly. Holds the per-clan war state memory for the war-start
 * transition trigger.
 */
@Entity(tableName = "tracked_clans")
data class TrackedClanEntity(
    @PrimaryKey val tag: String,
    val name: String,
    val badgeUrl: String? = null,
    /** Last seen currentwar state, memory for the war-start transition trigger. */
    val lastWarState: String? = null,
)
