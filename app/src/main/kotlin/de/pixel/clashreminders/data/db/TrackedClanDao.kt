package de.pixel.clashreminders.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.pixel.clashreminders.data.db.entity.TrackedClanEntity

@Dao
interface TrackedClanDao {

    /** IGNORE keeps the existing row — lastWarState must survive re-derivation. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(clan: TrackedClanEntity)

    @Query("SELECT * FROM tracked_clans")
    suspend fun getAll(): List<TrackedClanEntity>

    @Query("SELECT * FROM tracked_clans WHERE tag = :tag")
    suspend fun getByTag(tag: String): TrackedClanEntity?

    @Query("DELETE FROM tracked_clans WHERE tag NOT IN (:tags)")
    suspend fun deleteAllExcept(tags: List<String>)

    @Query("DELETE FROM tracked_clans")
    suspend fun deleteAll()

    @Query("UPDATE tracked_clans SET lastWarState = :state WHERE tag = :tag")
    suspend fun updateLastWarState(tag: String, state: String?)

    @Query("UPDATE tracked_clans SET name = :name, badgeUrl = :badgeUrl WHERE tag = :tag")
    suspend fun updateInfo(tag: String, name: String, badgeUrl: String?)
}
