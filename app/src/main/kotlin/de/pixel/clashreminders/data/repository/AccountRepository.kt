package de.pixel.clashreminders.data.repository

import de.pixel.clashreminders.api.ApiResult
import de.pixel.clashreminders.api.CocApiClient
import de.pixel.clashreminders.api.dto.PlayerDto
import de.pixel.clashreminders.data.db.AccountDao
import de.pixel.clashreminders.data.db.ReminderDao
import de.pixel.clashreminders.data.db.entity.AccountEntity
import de.pixel.clashreminders.data.db.entity.ReminderEntity
import de.pixel.clashreminders.domain.ClanGamesAnalysis
import de.pixel.clashreminders.domain.ReminderType
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek

class AccountRepository(
    private val accountDao: AccountDao,
    private val reminderDao: ReminderDao,
    private val api: CocApiClient,
) {

    fun observeAccounts(): Flow<List<AccountEntity>> = accountDao.observeAll()

    fun observeReminders(): Flow<List<ReminderEntity>> = reminderDao.observeAll()

    suspend fun getAccount(tag: String): AccountEntity? = accountDao.getByTag(tag)

    suspend fun lookupPlayer(tagInput: String): ApiResult<PlayerDto> =
        api.getPlayer(normalizeTag(tagInput))

    /**
     * Adds an account from a player lookup. The first account also seeds
     * one default reminder per type so notifications work out of the box.
     */
    suspend fun addAccount(player: PlayerDto) {
        val tag = player.tag ?: return
        accountDao.upsert(
            AccountEntity(
                tag = tag,
                name = player.name ?: tag,
                townHallLevel = player.townHallLevel,
                clanTag = player.clan?.tag,
                clanName = player.clan?.name,
                clanBadgeUrl = player.clan?.badgeUrls?.medium ?: player.clan?.badgeUrls?.small,
            )
        )
        if (reminderDao.count() == 0) {
            defaultReminders().forEach { reminderDao.insert(it) }
        }
    }

    suspend fun deleteAccount(tag: String) = accountDao.delete(tag)

    suspend fun addReminder(reminder: ReminderEntity): Long = reminderDao.insert(reminder)

    suspend fun updateReminder(reminder: ReminderEntity) = reminderDao.update(reminder)

    suspend fun deleteReminder(id: Long) = reminderDao.delete(id)

    private fun defaultReminders(): List<ReminderEntity> = listOf(
        ReminderEntity(type = ReminderType.WAR_START),
        ReminderEntity(type = ReminderType.WAR_END, offsetMinutes = 120),
        ReminderEntity(type = ReminderType.CWL_DAY_END, offsetMinutes = 120),
        ReminderEntity(
            type = ReminderType.RAID,
            raidDay = DayOfWeek.SUNDAY.name,
            raidTimeMinutes = 18 * 60,
        ),
        ReminderEntity(
            type = ReminderType.CLAN_GAMES_END,
            offsetMinutes = 24 * 60,
            cgThreshold = ClanGamesAnalysis.DEFAULT_THRESHOLD,
        ),
    )

    companion object {
        /** Uppercases, prepends '#' and fixes the common O/0 mixup — CoC tags never contain 'O'. */
        fun normalizeTag(input: String): String {
            val cleaned = input.trim().uppercase().removePrefix("#").replace("O", "0")
            return "#$cleaned"
        }
    }
}
