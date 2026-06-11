package de.pixel.clashreminders.ui.screen.clandetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.pixel.clashreminders.BuildConfig
import de.pixel.clashreminders.ClashRemindersApp
import de.pixel.clashreminders.R
import de.pixel.clashreminders.data.db.entity.ReminderEntity
import de.pixel.clashreminders.ui.component.AddReminderDialog
import de.pixel.clashreminders.ui.component.ClanBadge
import de.pixel.clashreminders.ui.component.ReminderTypeIcon
import de.pixel.clashreminders.ui.component.reminderLabel
import de.pixel.clashreminders.ui.component.reminderTypeName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClanDetailScreen(
    app: ClashRemindersApp,
    clanTag: String,
    onBack: () -> Unit,
    viewModel: ClanDetailViewModel = viewModel(
        factory = ClanDetailViewModel.factory(app, clanTag)
    ),
) {
    val clan by viewModel.clan.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(clan?.name ?: clanTag) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.clan_detail_delete_clan))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.clan_detail_add_reminder)) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClanBadge(clan?.badgeUrl)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(clan?.name ?: "", style = MaterialTheme.typography.titleLarge)
                    Text(clanTag, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text(
                stringResource(R.string.clan_detail_reminders),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            if (reminders.isEmpty()) {
                Text(
                    stringResource(R.string.clan_detail_no_reminders),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(reminders, key = { it.id }) { reminder ->
                        ReminderRow(
                            reminder = reminder,
                            onToggle = { viewModel.setEnabled(reminder, it) },
                            onDelete = { viewModel.deleteReminder(reminder) },
                            onFireNow = { viewModel.fireNow(reminder) },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddReminderDialog(
            onConfirm = {
                viewModel.addReminder(it)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.clan_detail_delete_clan)) },
            text = { Text(stringResource(R.string.clan_detail_delete_clan_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteClan(onDeleted = onBack)
                }) { Text(stringResource(R.string.clan_detail_delete_clan)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.add_reminder_cancel))
                }
            },
        )
    }
}

@Composable
private fun ReminderRow(
    reminder: ReminderEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onFireNow: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReminderTypeIcon(reminder.type)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(reminderTypeName(reminder.type), style = MaterialTheme.typography.titleSmall)
                Text(reminderLabel(reminder), style = MaterialTheme.typography.bodySmall)
            }
            if (BuildConfig.DEBUG) {
                IconButton(onClick = onFireNow) {
                    Icon(
                        Icons.Default.NotificationsActive,
                        stringResource(R.string.reminder_fire_now),
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, stringResource(R.string.reminder_delete))
            }
            Switch(checked = reminder.enabled, onCheckedChange = onToggle)
        }
    }
}
