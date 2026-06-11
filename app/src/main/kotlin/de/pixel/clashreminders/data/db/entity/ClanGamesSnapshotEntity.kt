package de.pixel.clashreminders.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * "Games Champion" achievement value per member at the start of a clan games
 * window. Diffed against the live value when the reminder fires.
 */
@Entity(
    tableName = "cg_snapshots",
    indices = [Index(value = ["clanTag", "playerTag", "windowKey"], unique = true)],
)
data class ClanGamesSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clanTag: String,
    val playerTag: String,
    val playerName: String,
    val points: Int,
    /** Clan games window key, e.g. "2026-06". */
    val windowKey: String,
    val takenAt: Long,
)
