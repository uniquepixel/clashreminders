package de.pixel.clashreminders.data.repository

import de.pixel.clashreminders.api.dto.PlayerDto
import de.pixel.clashreminders.data.db.AppDatabase
import de.pixel.clashreminders.data.db.entity.AccountEntity
import de.pixel.clashreminders.data.db.entity.ClanSightingEntity

/**
 * Applies a fresh player fetch to the stored account: profile update plus a
 * clan membership sighting. Every code path that fetches a player (presence
 * check, refresh, snapshot, app opens) goes through here so the sighting
 * window sees as many data points as possible.
 */
object AccountSync {

    suspend fun applyPlayer(
        database: AppDatabase,
        account: AccountEntity,
        player: PlayerDto,
        now: Long = System.currentTimeMillis(),
    ): AccountEntity {
        val updated = account.copy(
            name = player.name ?: account.name,
            townHallLevel = player.townHallLevel ?: account.townHallLevel,
            clanTag = player.clan?.tag,
            clanName = player.clan?.name,
            clanBadgeUrl = player.clan?.badgeUrls?.medium ?: player.clan?.badgeUrls?.small,
        )
        if (updated != account) database.accountDao().upsert(updated)
        val clan = player.clan
        if (clan?.tag != null) {
            database.clanSightingDao().upsert(
                ClanSightingEntity(
                    accountTag = account.tag,
                    clanTag = clan.tag,
                    clanName = clan.name ?: clan.tag,
                    clanBadgeUrl = clan.badgeUrls?.medium ?: clan.badgeUrls?.small,
                    lastSeenAt = now,
                )
            )
        }
        return updated
    }
}
