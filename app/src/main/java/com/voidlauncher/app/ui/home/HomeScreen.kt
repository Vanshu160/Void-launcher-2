package com.voidlauncher.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voidlauncher.app.ui.components.VoidCard

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onOpenVersions: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Left: the hero area and the main action.
        Column(
            modifier = Modifier
                .weight(1.4f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "VOID LAUNCHER",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                )
                Text(
                    text = "Minecraft Java",
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.onBackground,
                )
                Text(
                    text = state.statusMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
            }

            // Shortcuts are placeholders until their milestones arrive.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ShortcutTile(Icons.Filled.Folder, "Game folder", Modifier.weight(1f))
                ShortcutTile(Icons.Filled.Image, "Screenshots", Modifier.weight(1f))
                ShortcutTile(Icons.Filled.Public, "Worlds", Modifier.weight(1f))
                ShortcutTile(Icons.AutoMirrored.Filled.List, "Logs", Modifier.weight(1f))
            }

            Button(
                onClick = { /* launching arrives with the engine adapter */ },
                enabled = state.canPlay,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
                shape = RoundedCornerShape(22.dp),
            ) {
                Text(
                    text = "Play",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }

        // Right: the current selection and the account.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VoidCard {
                Text(
                    text = "Version",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = state.selectedVersion ?: "None selected",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                )
                OutlinedButton(
                    onClick = onOpenVersions,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Choose a version")
                }
            }

            VoidCard {
                Text(
                    text = "Account",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = "Player - offline",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ShortcutTile(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.6f))
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
    }
}
