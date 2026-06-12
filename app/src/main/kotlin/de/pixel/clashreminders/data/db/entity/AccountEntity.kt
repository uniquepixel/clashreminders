package de.pixel.clashreminders.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A player account of the app user. Everything in the app revolves around
 * these: reminders only fire for open attacks of these accounts, and the
 * set of clans to check is derived from their current clan memberships.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val tag: String,
    val name: String,
    val townHallLevel: Int? = null,
    /** Current clan membership, kept fresh by RefreshWorker; null = clanless. */
    val clanTag: String? = null,
    val clanName: String? = null,
    val clanBadgeUrl: String? = null,
    val sortOrder: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
)
