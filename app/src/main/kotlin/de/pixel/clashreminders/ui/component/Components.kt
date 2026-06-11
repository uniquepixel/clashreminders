package de.pixel.clashreminders.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.pixel.clashreminders.R
import de.pixel.clashreminders.data.db.entity.ReminderEntity
import de.pixel.clashreminders.domain.RaidWeekend
import de.pixel.clashreminders.domain.ReminderType
import java.time.DayOfWeek

@Composable
fun ClanBadge(badgeUrl: String?, modifier: Modifier = Modifier) {
    if (badgeUrl != null) {
        AsyncImage(
            model = badgeUrl,
            contentDescription = null,
            modifier = modifier.size(48.dp),
        )
    } else {
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            modifier = modifier.size(48.dp),
        )
    }
}

@Composable
fun ReminderTypeIcon(type: ReminderType, modifier: Modifier = Modifier) {
    val icon = when (type) {
        ReminderType.WAR_START -> Icons.Default.Flag
        ReminderType.WAR_END -> Icons.Default.MilitaryTech
        ReminderType.CWL_DAY_END -> Icons.Default.EmojiEvents
        ReminderType.RAID -> Icons.Default.LocalFireDepartment
        ReminderType.CLAN_GAMES_END -> Icons.Default.EmojiEvents
    }
    Icon(imageVector = icon, contentDescription = null, modifier = modifier)
}

@Composable
fun reminderTypeName(type: ReminderType): String = stringResource(
    when (type) {
        ReminderType.WAR_START -> R.string.type_war_start
        ReminderType.WAR_END -> R.string.type_war_end
        ReminderType.CWL_DAY_END -> R.string.type_cwl_day_end
        ReminderType.RAID -> R.string.type_raid
        ReminderType.CLAN_GAMES_END -> R.string.type_clan_games
    }
)

@Composable
fun raidDayName(day: DayOfWeek): String = stringResource(
    when (day) {
        DayOfWeek.FRIDAY -> R.string.day_friday
        DayOfWeek.SATURDAY -> R.string.day_saturday
        DayOfWeek.SUNDAY -> R.string.day_sunday
        else -> R.string.day_monday
    }
)

/** Localized one-line description of a reminder configuration. */
@Composable
fun reminderLabel(reminder: ReminderEntity): String = when (reminder.type) {
    ReminderType.WAR_START -> stringResource(R.string.reminder_war_start_label)
    ReminderType.WAR_END ->
        stringResource(R.string.reminder_offset_before_war_end, formatOffset(reminder.offsetMinutes))
    ReminderType.CWL_DAY_END ->
        stringResource(R.string.reminder_offset_before_cwl_day_end, formatOffset(reminder.offsetMinutes))
    ReminderType.CLAN_GAMES_END ->
        stringResource(R.string.reminder_offset_before_cg_end, formatOffset(reminder.offsetMinutes))
    ReminderType.RAID -> {
        val day = RaidWeekend.dayFromName(reminder.raidDay) ?: DayOfWeek.SUNDAY
        stringResource(
            R.string.reminder_raid_label,
            raidDayName(day),
            RaidWeekend.formatTime(reminder.raidTimeMinutes ?: 0),
        )
    }
}

fun formatOffset(offsetMinutes: Int): String {
    val hours = offsetMinutes / 60
    val minutes = offsetMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}
