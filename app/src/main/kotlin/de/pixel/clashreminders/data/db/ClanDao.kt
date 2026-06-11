package de.pixel.clashreminders.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.pixel.clashreminders.data.db.entity.ClanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(clan: ClanEntity)

    @Query("DELETE FROM clans WHERE tag = :tag")
    suspend fun delete(tag: String)

    @Query("SELECT * FROM clans ORDER BY sortOrder, addedAt")
    fun observeAll(): Flow<List<ClanEntity>>

    @Query("SELECT * FROM clans ORDER BY sortOrder, addedAt")
    suspend fun getAll(): List<ClanEntity>

    @Query("SELECT * FROM clans WHERE tag = :tag")
    suspend fun getByTag(tag: String): ClanEntity?

    @Query("SELECT * FROM clans WHERE tag = :tag")
    fun observeByTag(tag: String): Flow<ClanEntity?>

    @Query("UPDATE clans SET lastWarState = :state WHERE tag = :tag")
    suspend fun updateLastWarState(tag: String, state: String?)

    @Query("UPDATE clans SET name = :name, badgeUrl = :badgeUrl WHERE tag = :tag")
    suspend fun updateInfo(tag: String, name: String, badgeUrl: String?)
}
