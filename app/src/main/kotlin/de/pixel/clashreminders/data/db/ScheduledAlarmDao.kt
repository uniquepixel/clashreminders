package de.pixel.clashreminders.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.pixel.clashreminders.data.db.entity.ScheduledAlarmEntity

@Dao
interface ScheduledAlarmDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alarm: ScheduledAlarmEntity)

    @Query("DELETE FROM scheduled_alarms WHERE requestCode = :requestCode")
    suspend fun delete(requestCode: Int)

    @Query("DELETE FROM scheduled_alarms WHERE reminderId = :reminderId AND eventKey = :eventKey")
    suspend fun deleteByEvent(reminderId: Long, eventKey: String)

    @Query("SELECT * FROM scheduled_alarms")
    suspend fun getAll(): List<ScheduledAlarmEntity>
}
