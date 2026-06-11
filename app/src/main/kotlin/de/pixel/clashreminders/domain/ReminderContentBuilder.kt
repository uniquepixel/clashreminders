package de.pixel.clashreminders.domain

import android.content.Context
import de.pixel.clashreminders.R
import de.pixel.clashreminders.api.dto.CurrentWarDto

/**
 * Turns analysis results into localized notification title + body,
 * the app-side equivalent of the Discord message builders in
 * ListeningEvent.java.
 */
class ReminderContentBuilder(private val context: Context) {

    data class Content(val title: String, val text: String)

    fun warEnd(clanName: String, remainingMillis: Long, open: List<OpenWarAttacker>): Content {
        val title = context.getString(
            R.string.notif_war_end_title, clanName, formatRemaining(remainingMillis)
        )
        val text =
            if (open.isEmpty()) {
                context.getString(R.string.notif_war_end_all_done)
            } else {
                open.joinToString("\n") {
                    context.getString(R.string.notif_war_member_line, it.name, it.attacks, it.required)
                }
            }
        return Content(title, text)
    }

    fun warStart(clanName: String, warState: String?): Content {
        val title = context.getString(R.string.notif_war_start_title, clanName)
        val text =
            if (warState == CurrentWarDto.STATE_IN_WAR) {
                context.getString(R.string.notif_war_start_battle)
            } else {
                context.getString(R.string.notif_war_start_preparation)
            }
        return Content(title, text)
    }

    fun cwlDay(clanName: String, remainingMillis: Long, open: List<OpenWarAttacker>): Content {
        val title = context.getString(
            R.string.notif_cwl_title, clanName, formatRemaining(remainingMillis)
        )
        val text =
            if (open.isEmpty()) {
                context.getString(R.string.notif_cwl_all_done)
            } else {
                open.joinToString("\n") {
                    context.getString(R.string.notif_war_member_line, it.name, it.attacks, it.required)
                }
            }
        return Content(title, text)
    }

    fun raid(clanName: String, remainingMillis: Long, result: RaidAnalysis.Result): Content {
        val title = context.getString(
            R.string.notif_raid_title, clanName, formatRemaining(remainingMillis)
        )
        if (result.allDone) {
            return Content(title, context.getString(R.string.notif_raid_all_done))
        }
        val sections = mutableListOf<String>()
        if (result.notAttacked.isNotEmpty()) {
            sections += context.getString(R.string.notif_raid_not_attacked) + "\n" +
                result.notAttacked.joinToString("\n") { it.name }
        }
        if (result.openAttacks.isNotEmpty()) {
            sections += context.getString(R.string.notif_raid_open_attacks) + "\n" +
                result.openAttacks.joinToString("\n") {
                    context.getString(
                        R.string.notif_raid_member_line,
                        it.name, it.attacks, it.attackLimit + it.bonusAttackLimit,
                    )
                }
        }
        return Content(title, sections.joinToString("\n\n"))
    }

    /** Returns null when everyone is done — no notification then. */
    fun clanGames(
        clanName: String,
        remainingMillis: Long,
        below: List<ClanGamesAnalysis.MemberProgress>,
    ): Content? {
        if (below.isEmpty()) return null
        val title = context.getString(
            R.string.notif_cg_title, clanName, formatRemaining(remainingMillis)
        )
        val text = below.joinToString("\n") {
            if (it.points == null) {
                context.getString(R.string.notif_cg_no_baseline, it.name)
            } else {
                context.getString(R.string.notif_cg_member_line, it.name, it.points)
            }
        }
        return Content(title, text)
    }

    /** Fallback when the API stayed unreachable: fire without the member list. */
    fun degraded(type: ReminderType, clanName: String): Content {
        val title = when (type) {
            ReminderType.WAR_END -> context.getString(R.string.notif_war_end_title, clanName, "?")
            ReminderType.CWL_DAY_END -> context.getString(R.string.notif_cwl_title, clanName, "?")
            ReminderType.RAID -> context.getString(R.string.notif_raid_title, clanName, "?")
            ReminderType.CLAN_GAMES_END -> context.getString(R.string.notif_cg_title, clanName, "?")
            ReminderType.WAR_START -> context.getString(R.string.notif_war_start_title, clanName)
        }
        return Content(title, context.getString(R.string.notif_degraded_text))
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
