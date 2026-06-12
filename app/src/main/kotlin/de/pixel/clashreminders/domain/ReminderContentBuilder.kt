package de.pixel.clashreminders.domain

import android.content.Context
import de.pixel.clashreminders.R
import de.pixel.clashreminders.api.dto.CurrentWarDto

/**
 * Turns analysis results into localized notification title + body. All
 * builders are account-based: they list the user's own accounts with open
 * tasks and return null when everything is done — done means silence.
 */
class ReminderContentBuilder(private val context: Context) {

    data class Content(val title: String, val text: String)

    /** Open raid attacks of the user's accounts in one clan. */
    data class ClanRaidOpen(
        val clanName: String,
        val open: List<RaidAnalysis.AccountRaidStatus>,
    )

    fun warStart(clanName: String, warState: String?, accountNames: List<String>): Content {
        val title = context.getString(R.string.notif_war_start_title, clanName)
        val phase =
            if (warState == CurrentWarDto.STATE_IN_WAR) {
                context.getString(R.string.notif_war_start_battle)
            } else {
                context.getString(R.string.notif_war_start_preparation)
            }
        val text = if (accountNames.isEmpty()) {
            phase
        } else {
            phase + "\n" + context.getString(
                R.string.notif_war_start_accounts, accountNames.joinToString(", ")
            )
        }
        return Content(title, text)
    }

    /** Null when none of the accounts has attacks left — no notification. */
    fun warEnd(clanName: String, remainingMillis: Long, open: List<OpenWarAttacker>): Content? {
        if (open.isEmpty()) return null
        val title = context.getString(
            R.string.notif_war_end_title, clanName, formatRemaining(remainingMillis)
        )
        return Content(title, accountAttackLines(open))
    }

    /** Null when none of the accounts has its CWL hit left — no notification. */
    fun cwlDay(clanName: String, remainingMillis: Long, open: List<OpenWarAttacker>): Content? {
        if (open.isEmpty()) return null
        val title = context.getString(
            R.string.notif_cwl_title, clanName, formatRemaining(remainingMillis)
        )
        return Content(title, accountAttackLines(open))
    }

    /**
     * One notification covering all clans of the user's accounts.
     * Null when every account has used all raid attacks — no notification.
     */
    fun raid(remainingMillis: Long, perClan: List<ClanRaidOpen>): Content? {
        val withOpen = perClan.filter { it.open.isNotEmpty() }
        if (withOpen.isEmpty()) return null
        val title = context.getString(R.string.notif_raid_title, formatRemaining(remainingMillis))
        val text = withOpen.joinToString("\n\n") { clan ->
            val lines = clan.open.joinToString("\n") {
                context.getString(R.string.notif_raid_member_line, it.name, it.attacks, it.limit)
            }
            if (withOpen.size == 1 && perClan.size == 1) lines else clan.clanName + "\n" + lines
        }
        return Content(title, text)
    }

    /** Null when every account reached the threshold — no notification. */
    fun clanGames(
        remainingMillis: Long,
        below: List<ClanGamesAnalysis.MemberProgress>,
        threshold: Int,
    ): Content? {
        if (below.isEmpty()) return null
        val title = context.getString(R.string.notif_cg_title, formatRemaining(remainingMillis))
        val text = below.joinToString("\n") {
            if (it.points == null) {
                context.getString(R.string.notif_cg_no_baseline, it.name)
            } else {
                context.getString(R.string.notif_cg_member_line, it.name, it.points, threshold)
            }
        }
        return Content(title, text)
    }

    /** Fallback when the API stayed unreachable: fire without the account list. */
    fun degraded(type: ReminderType, clanName: String?): Content {
        val title = when (type) {
            ReminderType.WAR_END ->
                context.getString(R.string.notif_war_end_title, clanName.orEmpty(), "?")
            ReminderType.CWL_DAY_END ->
                context.getString(R.string.notif_cwl_title, clanName.orEmpty(), "?")
            ReminderType.RAID -> context.getString(R.string.notif_raid_title, "?")
            ReminderType.CLAN_GAMES_END -> context.getString(R.string.notif_cg_title, "?")
            ReminderType.WAR_START ->
                context.getString(R.string.notif_war_start_title, clanName.orEmpty())
        }
        return Content(title, context.getString(R.string.notif_degraded_text))
    }

    private fun accountAttackLines(open: List<OpenWarAttacker>): String =
        open.joinToString("\n") {
            context.getString(R.string.notif_war_member_line, it.name, it.attacks, it.required)
        }

    fun formatRemaining(millis: Long): String {
        val totalMinutes = millis.coerceAtLeast(0) / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            context.getString(R.string.notif_remaining_hours_minutes, hours, minutes)
        } else {
            context.getString(R.string.notif_remaining_minutes, minutes)
        }
    }
}
