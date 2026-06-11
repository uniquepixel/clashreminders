package de.pixel.clashreminders.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.pixel.clashreminders.R
import de.pixel.clashreminders.domain.ClanGamesAnalysis
import de.pixel.clashreminders.domain.RaidWeekend
import de.pixel.clashreminders.domain.ReminderType
import java.time.DayOfWeek

data class NewReminder(
    val type: ReminderType,
    val offsetMinutes: Int,
    val raidDay: DayOfWeek?,
    val raidTimeMinutes: Int?,
    val cgThreshold: Int?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderDialog(
    onConfirm: (NewReminder) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by remember { mutableStateOf(ReminderType.WAR_END) }
    var typeExpanded by remember { mutableStateOf(false) }
    var offsetHours by remember { mutableStateOf("2") }
    var offsetMinutes by remember { mutableStateOf("0") }
    var raidDay by remember { mutableStateOf(DayOfWeek.SUNDAY) }
    var raidDayExpanded by remember { mutableStateOf(false) }
    var raidHour by remember { mutableStateOf("18") }
    var raidMinute by remember { mutableStateOf("0") }
    var threshold by remember { mutableStateOf(ClanGamesAnalysis.DEFAULT_THRESHOLD.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_reminder_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it },
                ) {
                    OutlinedTextField(
                        value = reminderTypeName(type),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.add_reminder_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                    ) {
                        ReminderType.entries.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(reminderTypeName(entry)) },
                                onClick = {
                                    type = entry
                                    typeExpanded = false
                                },
                            )
                        }
                    }
                }

                when (type) {
                    ReminderType.WAR_START -> Unit

                    ReminderType.RAID -> {
                        ExposedDropdownMenuBox(
                            expanded = raidDayExpanded,
                            onExpandedChange = { raidDayExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = raidDayName(raidDay),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.add_reminder_raid_day)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(raidDayExpanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                            )
                            ExposedDropdownMenu(
                                expanded = raidDayExpanded,
                                onDismissRequest = { raidDayExpanded = false },
                            ) {
                                RaidWeekend.allowedDays.forEach { day ->
                                    DropdownMenuItem(
                                        text = { Text(raidDayName(day)) },
                                        onClick = {
                                            raidDay = day
                                            raidDayExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = raidHour,
                                onValueChange = { raidHour = it.filter(Char::isDigit).take(2) },
                                label = { Text(stringResource(R.string.add_reminder_offset_hours)) },
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = raidMinute,
                                onValueChange = { raidMinute = it.filter(Char::isDigit).take(2) },
                                label = { Text(stringResource(R.string.add_reminder_offset_minutes)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    else -> {
                        Text(stringResource(R.string.add_reminder_offset_hint))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = offsetHours,
                                onValueChange = { offsetHours = it.filter(Char::isDigit).take(3) },
                                label = { Text(stringResource(R.string.add_reminder_offset_hours)) },
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = offsetMinutes,
                                onValueChange = { offsetMinutes = it.filter(Char::isDigit).take(2) },
                                label = { Text(stringResource(R.string.add_reminder_offset_minutes)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (type == ReminderType.CLAN_GAMES_END) {
                            OutlinedTextField(
                                value = threshold,
                                onValueChange = { threshold = it.filter(Char::isDigit).take(5) },
                                label = { Text(stringResource(R.string.add_reminder_cg_threshold)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val offset = (offsetHours.toIntOrNull() ?: 0) * 60 + (offsetMinutes.toIntOrNull() ?: 0)
                val raidTime = ((raidHour.toIntOrNull() ?: 0).coerceIn(0, 23)) * 60 +
                    ((raidMinute.toIntOrNull() ?: 0).coerceIn(0, 59))
                onConfirm(
                    NewReminder(
                        type = type,
                        offsetMinutes = if (type == ReminderType.WAR_START) 0 else offset,
                        raidDay = if (type == ReminderType.RAID) raidDay else null,
                        raidTimeMinutes = if (type == ReminderType.RAID) raidTime else null,
                        cgThreshold = if (type == ReminderType.CLAN_GAMES_END) threshold.toIntOrNull() else null,
                    )
                )
            }) { Text(stringResource(R.string.add_reminder_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.add_reminder_cancel)) }
        },
    )
}
