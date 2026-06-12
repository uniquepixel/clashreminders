package de.pixel.clashreminders.ui.screen.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import de.pixel.clashreminders.ui.component.ReminderTypeIcon
import de.pixel.clashreminders.ui.component.reminderLabel
import de.pixel.clashreminders.ui.component.reminderTypeName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    app: ClashRemindersApp,
    viewModel: RemindersViewModel = viewModel(factory = RemindersViewModel.factory(app)),
) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.reminders_title)) })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.reminders_add)) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                stringResource(R.string.reminders_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (reminders.isEmpty()) {
                Text(
                    stringResource(R.string.reminders_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
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
