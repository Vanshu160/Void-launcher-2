package com.voidlauncher.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Filled.Home),
    Versions("versions", "Versions", Icons.Filled.Layers),
    Downloads("downloads", "Downloads", Icons.Filled.Download),
    Settings("settings", "Settings", Icons.Filled.Settings),
    Logs("logs", "Logs", Icons.AutoMirrored.Filled.List),
}
