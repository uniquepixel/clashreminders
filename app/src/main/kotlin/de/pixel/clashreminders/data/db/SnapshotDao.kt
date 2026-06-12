package de.pixel.clashreminders.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.pixel.clashreminders.data.db.entity.ClanGamesSnapshotEntity

@Dao
interface SnapshotDao {

    /** IGNORE keeps the earliest snapshot per (player, window) — the true baseline. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(snapshots: List<ClanGamesSnapshotEntity>)

    @Query("SELECT * FROM cg_snapshots WHERE windowKey = :windowKey")
    suspend fun getForWindow(windowKey: String): List<ClanGamesSnapshotEntity>

    @Query("SELECT * FROM cg_snapshots WHERE playerTag = :playerTag AND windowKey = :windowKey")
    suspend fun getForPlayerWindow(playerTag: String, windowKey: String): ClanGamesSnapshotEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM cg_snapshots WHERE playerTag = :playerTag AND windowKey = :windowKey)")
    suspend fun hasSnapshot(playerTag: String, windowKey: String): Boolean

    @Query("DELETE FROM cg_snapshots WHERE takenAt < :olderThanMillis")
    suspend fun deleteOlderThan(olderThanMillis: Long)
}
