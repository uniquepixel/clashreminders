package de.pixel.clashreminders.ui.screen.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.pixel.clashreminders.ClashRemindersApp
import de.pixel.clashreminders.R
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    app: ClashRemindersApp,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(app)),
) {
    val context = LocalContext.current
    val savedKey by viewModel.savedApiKey.collectAsStateWithLifecycle()
    val lastRefresh by viewModel.lastRefreshAt.collectAsStateWithLifecycle()
    var showKey by remember { mutableStateOf(false) }

    LaunchedEffect(savedKey) {
        if (viewModel.keyInput.isEmpty() && savedKey != null) {
            viewModel.keyInput = savedKey.orEmpty()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // --- API key ---
            Text(stringResource(R.string.settings_api_key), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_api_key_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = viewModel.keyInput,
                onValueChange = {
                    viewModel.keyInput = it
                },
                label = { Text(stringResource(R.string.settings_api_key)) },
                visualTransformation = if (showKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            stringResource(R.string.settings_api_key_show),
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.saveKey() },
                enabled = viewModel.keyInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_api_key_save)) }
            if (viewModel.keySaved) {
                Text(
                    stringResource(R.string.settings_api_key_saved),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = { viewModel.testConnection() },
                enabled = viewModel.testState !is TestState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_test_connection)) }
            when (val test = viewModel.testState) {
                is TestState.Loading -> CircularProgressIndicator()
                is TestState.Done -> Text(
                    stringResource(test.messageRes),
                    color = if (test.success) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                is TestState.Idle -> Unit
            }

            NotificationPermissionCard()
            ExactAlarmCard()

            Text(
                text = lastRefresh?.let {
                    stringResource(
                        R.string.settings_last_refresh,
                        DateFormat.getDateTimeInstance().format(Date(it)),
                    )
                } ?: stringResource(R.string.settings_last_refresh_never),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun NotificationPermissionCard() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasNotificationPermission(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    LifecycleResumeEffect(Unit) {
        granted = hasNotificationPermission(context)
        onPauseOrDispose { }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.settings_notifications_title),
                style = MaterialTheme.typography.titleSmall,
            )
            if (granted) {
                Text(
                    stringResource(R.string.settings_notifications_granted),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    stringResource(R.string.settings_notifications_rationale),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) { Text(stringResource(R.string.settings_notifications_grant)) }
            }
        }
    }
}

@Composable
private fun ExactAlarmCard() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(canScheduleExactAlarms(context)) }

    LifecycleResumeEffect(Unit) {
        granted = canScheduleExactAlarms(context)
        onPauseOrDispose { }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.settings_exact_alarm_title),
                style = MaterialTheme.typography.titleSmall,
            )
            if (granted) {
                Text(
                    stringResource(R.string.settings_exact_alarm_granted),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    stringResource(R.string.settings_exact_alarm_rationale),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    }
                }) { Text(stringResource(R.string.settings_exact_alarm_grant)) }
            }
        }
    }
}

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return alarmManager.canScheduleExactAlarms()
}
