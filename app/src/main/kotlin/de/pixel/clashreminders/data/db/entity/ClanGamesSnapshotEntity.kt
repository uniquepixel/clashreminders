package de.pixel.clashreminders.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * "Games Champion" achievement value per account at the start of a clan
 * games window. Diffed against the live value when the reminder fires.
 * Account-based, so only the user's own accounts need to be snapshotted.
 */
@Entity(
    tableName = "cg_snapshots",
    indices = [Index(value = ["playerTag", "windowKey"], unique = true)],
)
data class ClanGamesSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playerTag: String,
    val points: Int,
    /** Clan games window key, e.g. "2026-06". */
    val windowKey: String,
    val takenAt: Long,
)
