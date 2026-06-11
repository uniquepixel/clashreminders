package de.pixel.clashreminders.ui.screen.addclan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.pixel.clashreminders.ClashRemindersApp
import de.pixel.clashreminders.R
import de.pixel.clashreminders.ui.component.ClanBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddClanScreen(
    app: ClashRemindersApp,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddClanViewModel = viewModel(factory = AddClanViewModel.factory(app)),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_clan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = viewModel.tagInput,
                onValueChange = viewModel::onTagChanged,
                label = { Text(stringResource(R.string.add_clan_tag_label)) },
                placeholder = { Text(stringResource(R.string.add_clan_tag_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { viewModel.lookup() },
                enabled = viewModel.tagInput.isNotBlank() &&
                    viewModel.lookupState !is LookupState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.add_clan_validate))
            }

            when (val state = viewModel.lookupState) {
                is LookupState.Loading -> CircularProgressIndicator()
                is LookupState.Error -> Text(
                    stringResource(state.messageRes),
                    color = MaterialTheme.colorScheme.error,
                )
                is LookupState.Found -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ClanBadge(state.clan.badgeUrls?.medium ?: state.clan.badgeUrls?.small)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(state.clan.name, style = MaterialTheme.typography.titleMedium)
                                Text(state.clan.tag, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = viewModel.createDefaults,
                            onCheckedChange = { viewModel.createDefaults = it },
                        )
                        Text(
                            stringResource(R.string.add_clan_defaults),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        onClick = { viewModel.save(onSaved) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.add_clan_save))
                    }
                }
                is LookupState.Idle -> Unit
            }
        }
    }
}
