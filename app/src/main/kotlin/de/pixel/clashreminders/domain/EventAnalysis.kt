package de.pixel.clashreminders.domain

import de.pixel.clashreminders.api.dto.CurrentWarDto
import de.pixel.clashreminders.api.dto.LeagueGroupDto
import de.pixel.clashreminders.api.dto.RaidSeasonDto
import de.pixel.clashreminders.api.dto.WarClanDto

/** Minimal account reference so the pure analysis logic stays entity-free. */
data class AccountRef(
    val tag: String,
    val name: String,
)

/** A war member with fewer attacks than required. */
data class OpenWarAttacker(
    val tag: String,
    val name: String,
    val attacks: Int,
    val required: Int,
    val mapPosition: Int,
)

/**
 * War analysis centered on the user's own accounts: reminders only care
 * about attacks the user can still do themselves.
 */
object WarAnalysis {

    const val DEFAULT_ATTACKS_PER_MEMBER = 2
    const val CWL_ATTACKS_PER_DAY = 1

    fun requiredAttacks(war: CurrentWarDto): Int =
        war.attacksPerMember ?: DEFAULT_ATTACKS_PER_MEMBER

    /** The side of the war that belongs to [clanTag], regardless of home/away. */
    fun ourSide(war: CurrentWarDto, clanTag: String): WarClanDto? = when (clanTag) {
        war.clan?.tag -> war.clan
        war.opponent?.tag -> war.opponent
        else -> null
    }

    fun isOurWar(war: CurrentWarDto, clanTag: String): Boolean = ourSide(war, clanTag) != null

    /** The given accounts that are part of this war's lineup. */
    fun accountsInRoster(side: WarClanDto, accounts: List<AccountRef>): List<AccountRef> {
        val rosterTags = side.members.map { it.tag }.toSet()
        return accounts.filter { it.tag in rosterTags }
    }

    /**
     * Of the user's accounts, those in the lineup with attacks left,
     * sorted by map position. Accounts not in the lineup are not listed —
     * they have nothing to do in this war.
     */
    fun openAccountAttacks(
        side: WarClanDto,
        requiredAttacks: Int,
        accounts: List<AccountRef>,
    ): List<OpenWarAttacker> {
        val accountTags = accounts.map { it.tag }.toSet()
        return side.members
            .filter { it.tag in accountTags }
            .map {
                OpenWarAttacker(
                    tag = it.tag,
                    name = it.name,
                    attacks = it.attacks.size,
                    required = requiredAttacks,
                    mapPosition = it.mapPosition ?: Int.MAX_VALUE,
                )
            }
            .filter { it.attacks < it.required }
            .sortedBy { it.mapPosition }
    }
}

object CwlAnalysis {

    /** Active league group states, mirrored from Clan.java isCWLActive(). */
    fun isGroupActive(group: LeagueGroupDto): Boolean =
        group.state != null && group.state !in setOf("notInWar", "groupnotfound", "ended")

    /** War tags placeholder used by the API before a round is scheduled. */
    fun isRealWarTag(warTag: String): Boolean = warTag.isNotBlank() && warTag != "#0"
}

object RaidAnalysis {

    /** Regular + bonus attacks a member can make at most per raid weekend. */
    const val MAX_ATTACKS_PER_MEMBER = 6

    data class AccountRaidStatus(
        val tag: String,
        val name: String,
        val attacks: Int,
        val limit: Int,
    ) {
        val open: Boolean get() = attacks < limit
    }

    /**
     * Raid progress for each of the user's accounts in this clan's raid.
     * Accounts that have not joined the raid yet count as 0 attacks used.
     * Use for accounts currently in the clan.
     */
    fun accountStatuses(accounts: List<AccountRef>, raid: RaidSeasonDto): List<AccountRaidStatus> {
        val raidByTag = raid.members.associateBy { it.tag }
        return accounts.map { account ->
            val member = raidByTag[account.tag]
            AccountRaidStatus(
                tag = account.tag,
                name = account.name,
                attacks = member?.attacks ?: 0,
                limit = member?.let { it.attackLimit + it.bonusAttackLimit }
                    ?: MAX_ATTACKS_PER_MEMBER,
            )
        }
    }

    /**
     * Raid progress only for accounts that actually joined this clan's raid.
     * Use for accounts that are NOT currently in the clan (hopped back home):
     * they only matter here if they started raiding and left attacks open.
     */
    fun participantStatuses(accounts: List<AccountRef>, raid: RaidSeasonDto): List<AccountRaidStatus> {
        val raidByTag = raid.members.associateBy { it.tag }
        return accounts.mapNotNull { account ->
            raidByTag[account.tag]?.let { member ->
                AccountRaidStatus(
                    tag = account.tag,
                    name = account.name,
                    attacks = member.attacks,
                    limit = member.attackLimit + member.bonusAttackLimit,
                )
            }
        }
    }
}

object ClanGamesAnalysis {

    const val DEFAULT_THRESHOLD = 4000

    data class MemberProgress(
        val tag: String,
        val name: String,
        /** Points earned this window; null when no baseline snapshot exists. */
        val points: Int?,
    )

    /**
     * Accounts below the threshold: diff of the "Games Champion" achievement
     * against the window-start snapshot.
     */
    fun belowThreshold(
        currentPoints: List<MemberProgress>,
        baseline: Map<String, Int>,
        threshold: Int,
    ): List<MemberProgress> =
        currentPoints
            .map { member ->
                val base = baseline[member.tag]
                when {
                    member.points == null || base == null -> member.copy(points = null)
                    else -> member.copy(points = member.points - base)
                }
            }
            .filter { it.points == null || it.points < threshold }
            .sortedBy { it.points ?: -1 }
}
