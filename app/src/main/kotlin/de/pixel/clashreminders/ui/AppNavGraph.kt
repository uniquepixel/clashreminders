package de.pixel.clashreminders.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.pixel.clashreminders.ClashRemindersApp
import de.pixel.clashreminders.ui.screen.addclan.AddClanScreen
import de.pixel.clashreminders.ui.screen.clandetail.ClanDetailScreen
import de.pixel.clashreminders.ui.screen.clanlist.ClanListScreen
import de.pixel.clashreminders.ui.screen.onboarding.OnboardingScreen
import de.pixel.clashreminders.ui.screen.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val CLANS = "clans"
    const val CLAN_DETAIL = "clan/{tag}"
    const val ADD_CLAN = "addclan"
    const val SETTINGS = "settings"

    fun clanDetail(tag: String) = "clan/${Uri.encode(tag)}"
}

@Composable
fun AppNavGraph(app: ClashRemindersApp, initialClanTag: String? = null) {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (startDestination == null) {
            val hasKey = app.settingsRepository.apiKeyOnce() != null
            startDestination = if (hasKey) Routes.CLANS else Routes.ONBOARDING
        }
    }

    val start = startDestination ?: return

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                app = app,
                onContinue = {
                    navController.navigate(Routes.ADD_CLAN) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.CLANS) {
            ClanListScreen(
                app = app,
                onClanClick = { navController.navigate(Routes.clanDetail(it)) },
                onAddClan = { navController.navigate(Routes.ADD_CLAN) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.CLAN_DETAIL,
            arguments = listOf(navArgument("tag") { type = NavType.StringType }),
        ) { backStackEntry ->
            val tag = backStackEntry.arguments?.getString("tag").orEmpty()
            ClanDetailScreen(
                app = app,
                clanTag = tag,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.ADD_CLAN) {
            AddClanScreen(
                app = app,
                onBack = { navController.popBackStack() },
                onSaved = {
                    navController.navigate(Routes.CLANS) {
                        popUpTo(Routes.CLANS) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                app = app,
                onBack = { navController.popBackStack() },
            )
        }
    }

    // Deep link from a notification: open the clan that fired it
    LaunchedEffect(initialClanTag, start) {
        if (initialClanTag != null && start == Routes.CLANS) {
            navController.navigate(Routes.clanDetail(initialClanTag))
        }
    }
}
