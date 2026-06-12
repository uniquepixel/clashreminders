package de.pixel.clashreminders.data.db.entity

import androidx.room.Entity

/**
 * Membership sighting: account X was seen in clan Y at time Z. Fed by every
 * player fetch (periodic presence check, refresh, app opens). A clan stays
 * tracked for [RETENTION_MILLIS] after the last sighting of any account, so
 * clan-hopping for war hits keeps the war clan covered for a week even while
 * the account sits in its home clan.
 */
@Entity(tableName = "clan_sightings", primaryKeys = ["accountTag", "clanTag"])
data class ClanSightingEntity(
    val accountTag: String,
    val clanTag: String,
    val clanName: String,
    val clanBadgeUrl: String?,
    val lastSeenAt: Long,
) {
    companion object {
        /** Tracked window: a clan expires one week after the last sighting. */
        const val RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
