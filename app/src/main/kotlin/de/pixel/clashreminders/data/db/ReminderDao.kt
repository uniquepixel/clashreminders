package de.pixel.clashreminders.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import de.pixel.clashreminders.data.db.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE clanTag = :clanTag ORDER BY type, offsetMinutes")
    fun observeForClan(clanTag: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE clanTag = :clanTag AND enabled = 1")
    suspend fun getEnabledForClan(clanTag: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun getAllEnabled(): List<ReminderEntity>

    @Query("SELECT * FROM reminders")
    fun observeAll(): Flow<List<ReminderEntity>>
}
