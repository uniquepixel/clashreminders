package de.pixel.clashreminders.ui.screen.clanlist

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.pixel.clashreminders.ClashRemindersApp
import de.pixel.clashreminders.R
import de.pixel.clashreminders.data.db.entity.ClanEntity
import de.pixel.clashreminders.ui.component.ClanBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClanListScreen(
    app: ClashRemindersApp,
    onClanClick: (String) -> Unit,
    onAddClan: () -> Unit,
    onSettings: () -> Unit,
    viewModel: ClanListViewModel = viewModel(factory = ClanListViewModel.factory(app)),
) {
    val clans by viewModel.clans.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.clan_list_title)) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.clan_list_refresh))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, stringResource(R.string.clan_list_settings))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClan) {
                Icon(Icons.Default.Add, stringResource(R.string.clan_list_add))
            }
        },
    ) { padding ->
        if (clans.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.clan_list_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(clans, key = { it.tag }) { clan ->
                    val activeReminders = reminders.count { it.clanTag == clan.tag && it.enabled }
                    ClanCard(
                        clan = clan,
                        activeReminders = activeReminders,
                        status = statuses[clan.tag],
                        onLoadStatus = { viewModel.loadStatus(clan.tag) },
                        onClick = { onClanClick(clan.tag) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ClanCard(
    clan: ClanEntity,
    activeReminders: Int,
    status: ClanStatus?,
    onLoadStatus: () -> Unit,
    onClick: () -> Unit,
) {
    LaunchedEffect(clan.tag) { onLoadStatus() }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClanBadge(clan.badgeUrl)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(clan.name, style = MaterialTheme.typography.titleMedium)
                Text(clan.tag, style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(R.string.clan_list_reminders_count, activeReminders),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = when (status) {
                        is ClanStatus.War -> stringResource(
                            R.string.clan_status_war, warStateName(status.state)
                        )
                        is ClanStatus.NoWar -> stringResource(R.string.clan_status_no_war)
                        else -> stringResource(R.string.clan_status_loading)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun warStateName(state: String): String = when (state) {
    "preparation" -> stringResource(R.string.war_state_preparation)
    "inWar" -> stringResource(R.string.war_state_in_war)
    else -> stringResource(R.string.war_state_ended)
}
