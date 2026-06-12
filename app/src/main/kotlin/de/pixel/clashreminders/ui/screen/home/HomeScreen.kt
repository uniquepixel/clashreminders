package de.pixel.clashreminders.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.pixel.clashreminders.ClashRemindersApp
import de.pixel.clashreminders.R
import de.pixel.clashreminders.api.dto.CurrentWarDto
import de.pixel.clashreminders.data.db.entity.AccountEntity
import de.pixel.clashreminders.ui.component.ClanBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    app: ClashRemindersApp,
    onAddAccount: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(app)),
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    LaunchedEffect(accounts.size) {
        if (accounts.isNotEmpty()) viewModel.loadStatuses()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp).size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.home_refresh))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddAccount) {
                Icon(Icons.Default.Add, stringResource(R.string.home_add_account))
            }
        },
    ) { padding ->
        if (accounts.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.home_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(accounts, key = { it.tag }) { account ->
                    AccountCard(
                        account = account,
                        status = statuses[account.tag],
                        onDelete = { viewModel.deleteAccount(account.tag) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountCard(
    account: AccountEntity,
    status: AccountStatus?,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TownHallBadge(account.townHallLevel)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        account.tag,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        stringResource(R.string.home_delete_account),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClanBadge(account.clanBadgeUrl, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    account.clanName ?: stringResource(R.string.home_no_clan),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            StatusChips(status)
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.home_delete_account)) },
            text = { Text(stringResource(R.string.home_delete_account_confirm, account.name)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text(stringResource(R.string.home_delete_account)) }
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
private fun StatusChips(status: AccountStatus?) {
    if (status == null) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.home_status_loading),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val chips = mutableListOf<@Composable () -> Unit>()
    if (status.warState != null) {
        val done = status.warAttacksDone >= status.warAttacksRequired
        chips += {
            StatusChip(
                text = if (status.warState == CurrentWarDto.STATE_PREPARATION) {
                    stringResource(R.string.home_chip_war_preparation)
                } else {
                    stringResource(
                        R.string.home_chip_war, status.warAttacksDone, status.warAttacksRequired
                    )
                },
                done = status.warState == CurrentWarDto.STATE_PREPARATION || done,
            )
        }
    }
    if (status.raidAttacks != null && status.raidLimit != null) {
        chips += {
            StatusChip(
                text = stringResource(R.string.home_chip_raid, status.raidAttacks, status.raidLimit),
                done = status.raidAttacks >= status.raidLimit,
            )
        }
    }
    if (status.cgActive) {
        chips += {
            StatusChip(
                text = status.cgPoints?.let { stringResource(R.string.home_chip_cg, it) }
                    ?: stringResource(R.string.home_chip_cg_unknown),
                done = false,
                neutral = true,
            )
        }
    }

    if (chips.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.home_status_nothing_open),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { it() }
        }
    }
}

@Composable
private fun StatusChip(text: String, done: Boolean, neutral: Boolean = false) {
    val container = when {
        neutral -> MaterialTheme.colorScheme.secondaryContainer
        done -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when {
        neutral -> MaterialTheme.colorScheme.onSecondaryContainer
        done -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = container,
        contentColor = contentColor,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun TownHallBadge(level: Int?) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = level?.toString() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}
