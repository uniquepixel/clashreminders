package de.pixel.clashreminders.data.repository

import de.pixel.clashreminders.api.ApiResult
import de.pixel.clashreminders.api.CocApiClient
import de.pixel.clashreminders.api.dto.ClanDto
import de.pixel.clashreminders.data.db.ClanDao
import de.pixel.clashreminders.data.db.ReminderDao
import de.pixel.clashreminders.data.db.entity.ClanEntity
import de.pixel.clashreminders.data.db.entity.ReminderEntity
import de.pixel.clashreminders.domain.ClanGamesAnalysis
import de.pixel.clashreminders.domain.ReminderType
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek

class ClanRepository(
    private val clanDao: ClanDao,
    private val reminderDao: ReminderDao,
    private val api: CocApiClient,
) {

    fun observeClans(): Flow<List<ClanEntity>> = clanDao.observeAll()

    fun observeClan(tag: String): Flow<ClanEntity?> = clanDao.observeByTag(tag)

    fun observeReminders(clanTag: String): Flow<List<ReminderEntity>> =
        reminderDao.observeForClan(clanTag)

    fun observeAllReminders(): Flow<List<ReminderEntity>> = reminderDao.observeAll()

    suspend fun getClan(tag: String): ClanEntity? = clanDao.getByTag(tag)

    suspend fun lookupClan(tagInput: String): ApiResult<ClanDto> =
        api.getClan(normalizeTag(tagInput))

    suspend fun addClan(dto: ClanDto, createDefaults: Boolean) {
        clanDao.upsert(
            ClanEntity(
                tag = dto.tag,
                name = dto.name,
                badgeUrl = dto.badgeUrls?.medium ?: dto.badgeUrls?.small,
            )
        )
        if (createDefaults) {
            defaultReminders(dto.tag).forEach { reminderDao.insert(it) }
        }
    }

    suspend fun deleteClan(tag: String) = clanDao.delete(tag)

    suspend fun addReminder(reminder: ReminderEntity): Long = reminderDao.insert(reminder)

    suspend fun updateReminder(reminder: ReminderEntity) = reminderDao.update(reminder)

    suspend fun deleteReminder(id: Long) = reminderDao.delete(id)

    suspend fun getReminder(id: Long): ReminderEntity? = reminderDao.getById(id)

    private fun defaultReminders(clanTag: String): List<ReminderEntity> = listOf(
        ReminderEntity(clanTag = clanTag, type = ReminderType.WAR_START),
        ReminderEntity(clanTag = clanTag, type = ReminderType.WAR_END, offsetMinutes = 120),
        ReminderEntity(clanTag = clanTag, type = ReminderType.CWL_DAY_END, offsetMinutes = 120),
        ReminderEntity(
            clanTag = clanTag,
            type = ReminderType.RAID,
            raidDay = DayOfWeek.SUNDAY.name,
            raidTimeMinutes = 18 * 60,
        ),
        ReminderEntity(
            clanTag = clanTag,
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
