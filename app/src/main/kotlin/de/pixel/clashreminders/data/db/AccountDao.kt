package de.pixel.clashreminders.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.pixel.clashreminders.data.db.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE tag = :tag")
    suspend fun delete(tag: String)

    @Query("SELECT * FROM accounts ORDER BY sortOrder, addedAt")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder, addedAt")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE tag = :tag")
    suspend fun getByTag(tag: String): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Query(
        "UPDATE accounts SET name = :name, townHallLevel = :townHallLevel, " +
            "clanTag = :clanTag, clanName = :clanName, clanBadgeUrl = :clanBadgeUrl WHERE tag = :tag"
    )
    suspend fun updateProfile(
        tag: String,
        name: String,
        townHallLevel: Int?,
        clanTag: String?,
        clanName: String?,
        clanBadgeUrl: String?,
    )
}
