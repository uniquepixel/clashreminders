package de.pixel.clashreminders.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.pixel.clashreminders.data.db.entity.ClanSightingEntity

@Dao
interface ClanSightingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(sighting: ClanSightingEntity)

    @Query("SELECT * FROM clan_sightings")
    suspend fun getAll(): List<ClanSightingEntity>

    @Query("SELECT * FROM clan_sightings WHERE accountTag = :accountTag")
    suspend fun getForAccount(accountTag: String): List<ClanSightingEntity>

    @Query("DELETE FROM clan_sightings WHERE lastSeenAt < :olderThanMillis")
    suspend fun deleteOlderThan(olderThanMillis: Long)

    @Query("DELETE FROM clan_sightings WHERE accountTag = :accountTag")
    suspend fun deleteForAccount(accountTag: String)
}
