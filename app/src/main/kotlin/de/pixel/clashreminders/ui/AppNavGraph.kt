package de.pixel.clashreminders.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.pixel.clashreminders.ClashRemindersApp
import de.pixel.clashreminders.R
import de.pixel.clashreminders.ui.screen.addaccount.AddAccountScreen
import de.pixel.clashreminders.ui.screen.home.HomeScreen
import de.pixel.clashreminders.ui.screen.onboarding.OnboardingScreen
import de.pixel.clashreminders.ui.screen.reminders.RemindersScreen
import de.pixel.clashreminders.ui.screen.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val REMINDERS = "reminders"
    const val SETTINGS = "settings"
    const val ADD_ACCOUNT = "addaccount"
}

private data class Tab(val route: String, val labelRes: Int, val icon: @Composable () -> Unit)

@Composable
fun AppNavGraph(app: ClashRemindersApp) {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (startDestination == null) {
            val hasKey = app.settingsRepository.apiKeyOnce() != null
            startDestination = if (hasKey) Routes.HOME else Routes.ONBOARDING
        }
    }

    val start = startDestination ?: return

    val tabs = listOf(
        Tab(Routes.HOME, R.string.tab_accounts) { Icon(Icons.Default.People, null) },
        Tab(Routes.REMINDERS, R.string.tab_reminders) { Icon(Icons.Default.Notifications, null) },
        Tab(Routes.SETTINGS, R.string.tab_settings) { Icon(Icons.Default.Settings, null) },
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in tabs.map { it.route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = tab.icon,
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    app = app,
                    onContinue = {
                        navController.navigate(Routes.ADD_ACCOUNT) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    app = app,
                    onAddAccount = { navController.navigate(Routes.ADD_ACCOUNT) },
                )
            }
            composable(Routes.REMINDERS) {
                RemindersScreen(app = app)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(app = app)
            }
            composable(Routes.ADD_ACCOUNT) {
                AddAccountScreen(
                    app = app,
                    onBack = { navController.popBackStack() },
                    onSaved = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}
