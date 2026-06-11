package de.pixel.clashreminders.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.pixel.clashreminders.data.db.entity.FiredEventEntity

@Dao
interface FiredEventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: FiredEventEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM fired_events WHERE reminderId = :reminderId AND eventKey = :eventKey)")
    suspend fun exists(reminderId: Long, eventKey: String): Boolean

    @Query("DELETE FROM fired_events WHERE firedAt < :olderThanMillis")
    suspend fun deleteOlderThan(olderThanMillis: Long)
}
