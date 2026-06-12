package de.pixel.clashreminders.data.repository

import de.pixel.clashreminders.api.CocApiClient
import de.pixel.clashreminders.api.dto.CurrentWarDto
import de.pixel.clashreminders.api.dto.RaidSeasonDto
import de.pixel.clashreminders.api.valueOrNull
import de.pixel.clashreminders.data.db.AppDatabase
import de.pixel.clashreminders.data.db.entity.AccountEntity
import de.pixel.clashreminders.data.db.entity.ClanSightingEntity
import de.pixel.clashreminders.domain.AccountRef
import de.pixel.clashreminders.domain.ClanGamesAnalysis
import de.pixel.clashreminders.domain.ClanGamesCalendar
import de.pixel.clashreminders.domain.RaidAnalysis
import de.pixel.clashreminders.domain.RaidWeekend
import de.pixel.clashreminders.domain.WarAnalysis

/** Live to-do status of one account, shown as chips on its card and in the widget. */
data class AccountStatus(
    /** preparation/inWar when the account is in a tracked clan's war lineup. */
    val warState: String? = null,
    val warAttacksDone: Int = 0,
    val warAttacksRequired: Int = 0,
    /** Raid attacks used/limit, only set during the raid weekend. */
    val raidAttacks: Int? = null,
    val raidLimit: Int? = null,
    /** Clan games points earned this window; null = no baseline yet. */
    val cgPoints: Int? = null,
    val cgActive: Boolean = false,
) {
    val anythingOpen: Boolean get() =
        (warState == CurrentWarDto.STATE_IN_WAR && warAttacksDone < warAttacksRequired) ||
        (raidAttacks != null && raidLimit != null && raidAttacks < raidLimit) ||
        (cgActive && (cgPoints == null || cgPoints < ClanGamesAnalysis.DEFAULT_THRESHOLD))
}

class AccountStatusLoader(
    private val database: AppDatabase,
    private val api: CocApiClient,
) {
    data class LoadedStatus(
        val account: AccountEntity,
        val status: AccountStatus,
        /** false when getPlayer failed — data may be stale */
        val playerFetched: Boolean,
    )

    /**
     * Computes the to-do status of every stored account. Calls [onEach] as
     * soon as each account is resolved (for incremental UI updates). Returns
     * the full list when done.
     */
    suspend fun load(
        now: Long = System.currentTimeMillis(),
        onEach: suspend (LoadedStatus) -> Unit = {},
    ): List<LoadedStatus> {
        val warCache = mutableMapOf<String, CurrentWarDto?>()
        val raidCache = mutableMapOf<String, RaidSeasonDto?>()
        val cgWindow = ClanGamesCalendar.currentWindow(now)
        val raidActive = RaidWeekend.isInWindow(now)
        val results = mutableListOf<LoadedStatus>()

        for (account in database.accountDao().getAll()) {
            val player = api.getPlayer(account.tag).valueOrNull()
            val fresh = if (player != null) {
                AccountSync.applyPlayer(database, account, player, now)
            } else {
                account
            }
            val ref = AccountRef(fresh.tag, fresh.name)

            val sightingTags = database.clanSightingDao()
                .getForAccount(fresh.tag)
                .filter { it.lastSeenAt >= now - ClanSightingEntity.RETENTION_MILLIS }
                .map { it.clanTag }
            val candidateClans = (listOfNotNull(fresh.clanTag) + sightingTags).distinct()

            var status = AccountStatus()
            for (clanTag in candidateClans) {
                val war = warCache.getOrPut(clanTag) {
                    api.getCurrentWar(clanTag).valueOrNull()
                } ?: continue
                val warActive = war.state == CurrentWarDto.STATE_PREPARATION ||
                    war.state == CurrentWarDto.STATE_IN_WAR
                if (!warActive) continue
                val side = WarAnalysis.ourSide(war, clanTag)
                val member = side?.members?.firstOrNull { it.tag == fresh.tag } ?: continue
                status = status.copy(
                    warState = war.state,
                    warAttacksDone = member.attacks.size,
                    warAttacksRequired = WarAnalysis.requiredAttacks(war),
                )
                break
            }

            if (raidActive) {
                for (clanTag in candidateClans) {
                    val raid = raidCache.getOrPut(clanTag) {
                        api.getRaidSeasons(clanTag).valueOrNull()
                            ?.items?.firstOrNull()
                            ?.takeIf { it.state == RaidSeasonDto.STATE_ONGOING }
                    } ?: continue
                    val raidStatus = if (clanTag == fresh.clanTag) {
                        RaidAnalysis.accountStatuses(listOf(ref), raid).first()
                    } else {
                        RaidAnalysis.participantStatuses(listOf(ref), raid)
                            .firstOrNull { it.open }
                    } ?: continue
                    status = status.copy(
                        raidAttacks = raidStatus.attacks,
                        raidLimit = raidStatus.limit,
                    )
                    break
                }
            }

            if (cgWindow != null && player != null) {
                val baseline = database.snapshotDao()
                    .getForPlayerWindow(fresh.tag, cgWindow.windowKey)?.points
                val points = player.clanGamesPoints()
                status = status.copy(
                    cgActive = true,
                    cgPoints = if (baseline != null && points != null) points - baseline else null,
                )
            }

            val loaded = LoadedStatus(fresh, status, playerFetched = player != null)
            results += loaded
            onEach(loaded)
        }
        return results
    }
}
