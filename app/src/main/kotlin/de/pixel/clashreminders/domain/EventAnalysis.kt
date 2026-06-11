package de.pixel.clashreminders.domain

import de.pixel.clashreminders.api.dto.ClanMemberDto
import de.pixel.clashreminders.api.dto.CurrentWarDto
import de.pixel.clashreminders.api.dto.LeagueGroupDto
import de.pixel.clashreminders.api.dto.RaidMemberDto
import de.pixel.clashreminders.api.dto.RaidSeasonDto
import de.pixel.clashreminders.api.dto.WarClanDto

/** A war member with fewer attacks than required. */
data class OpenWarAttacker(
    val tag: String,
    val name: String,
    val attacks: Int,
    val required: Int,
    val mapPosition: Int,
)

/** Port of buildCWMissedAttacksMessage / the CWL day handler in ListeningEvent.java. */
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

    fun openAttackers(side: WarClanDto, requiredAttacks: Int): List<OpenWarAttacker> =
        side.members
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

object CwlAnalysis {

    /** Active league group states, mirrored from Clan.java isCWLActive(). */
    fun isGroupActive(group: LeagueGroupDto): Boolean =
        group.state != null && group.state !in setOf("notInWar", "groupnotfound", "ended")

    /** War tags placeholder used by the API before a round is scheduled. */
    fun isRealWarTag(warTag: String): Boolean = warTag.isNotBlank() && warTag != "#0"
}

object RaidAnalysis {

    data class Result(
        /** Clan members that never joined the raid. */
        val notAttacked: List<ClanMemberDto>,
        /** Raid participants with attacks left (attackLimit + bonusAttackLimit). */
        val openAttacks: List<RaidMemberDto>,
    ) {
        val allDone: Boolean get() = notAttacked.isEmpty() && openAttacks.isEmpty()
    }

    fun analyze(clanMembers: List<ClanMemberDto>, raid: RaidSeasonDto): Result {
        val raidByTag = raid.members.associateBy { it.tag }
        val notAttacked = clanMembers.filter { it.tag !in raidByTag }
        val openAttacks = raid.members.filter { it.attacks < it.attackLimit + it.bonusAttackLimit }
        return Result(notAttacked, openAttacks)
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
     * Members below the threshold, port of handleClanGamesEvent: diff of the
     * "Games Champion" achievement against the window-start snapshot.
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
