package com.voidlauncher.app.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.voidlauncher.app.ui.components.VoidBackdrop
import com.voidlauncher.app.ui.components.VoidTab
import com.voidlauncher.app.ui.components.VoidTabRow
import com.voidlauncher.app.ui.home.HomeScreen
import com.voidlauncher.app.ui.mods.ModsScreen
import com.voidlauncher.app.ui.placeholder.PlaceholderScreen
import com.voidlauncher.app.ui.settings.SettingsScreen
import com.voidlauncher.app.ui.settings.SettingsViewModel
import com.voidlauncher.app.ui.versions.VersionsScreen

@Composable
fun VoidApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // One settings instance shared by the backdrop and the Settings screen.
    val settingsViewModel: SettingsViewModel = viewModel()
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()

    val destinations = Destination.entries
    val selectedIndex = destinations.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    val navigateTo: (Destination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    VoidBackdrop(animated = settings.showAnimations) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            VoidTabRow(
                tabs = destinations.map { VoidTab(it.label, it.icon) },
                selectedIndex = selectedIndex,
                onSelect = { index -> navigateTo(destinations[index]) },
            )

            NavHost(
                navController = navController,
                startDestination = Destination.Home.route,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 12.dp),
            ) {
                composable(Destination.Home.route) {
                    HomeScreen(onOpenVersions = { navigateTo(Destination.Versions) })
                }
                composable(Destination.Versions.route) {
                    VersionsScreen()
                }
                composable(Destination.Mods.route) {
                    ModsScreen()
                }
                composable(Destination.Downloads.route) {
                    PlaceholderScreen("Downloads", "The download manager arrives in a later milestone.")
                }
                composable(Destination.Settings.route) {
                    SettingsScreen(viewModel = settingsViewModel)
                }
                composable(Destination.Logs.route) {
                    PlaceholderScreen("Logs", "Logs and diagnostics arrive in a later milestone.")
                }
            }
        }
    }
}
