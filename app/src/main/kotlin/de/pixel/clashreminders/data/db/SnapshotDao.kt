package de.pixel.clashreminders.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.pixel.clashreminders.data.db.entity.ClanGamesSnapshotEntity

@Dao
interface SnapshotDao {

    /** IGNORE keeps the earliest snapshot per (clan, player, window) — the true baseline. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(snapshots: List<ClanGamesSnapshotEntity>)

    @Query("SELECT * FROM cg_snapshots WHERE clanTag = :clanTag AND windowKey = :windowKey")
    suspend fun getForClanWindow(clanTag: String, windowKey: String): List<ClanGamesSnapshotEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM cg_snapshots WHERE clanTag = :clanTag AND windowKey = :windowKey)")
    suspend fun hasSnapshot(clanTag: String, windowKey: String): Boolean

    @Query("DELETE FROM cg_snapshots WHERE takenAt < :olderThanMillis")
    suspend fun deleteOlderThan(olderThanMillis: Long)
}
