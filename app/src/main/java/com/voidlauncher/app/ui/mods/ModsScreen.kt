package com.voidlauncher.app.ui.mods

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voidlauncher.app.domain.model.InstalledMod
import com.voidlauncher.app.domain.model.ModSearchResult
import com.voidlauncher.app.ui.components.VoidCard

@Composable
fun ModsScreen(viewModel: ModsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val version = state.gameVersion

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(
                text = "Mods",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onBackground,
            )
            Text(
                text = if (version == null) {
                    "Select a version in Versions first."
                } else {
                    "For Minecraft $version with Fabric, from Modrinth"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text("Search mods") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { viewModel.search() },
                enabled = version != null && !state.isSearching,
            ) {
                Text(if (state.isSearching) "Searching..." else "Search")
            }
        }

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1.4f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.results, key = { it.projectId }) { result ->
                    ModResultRow(
                        result = result,
                        installing = state.installingProjectId == result.projectId,
                        enabled = state.installingProjectId == null && version != null,
                        onInstall = { viewModel.install(result) },
                    )
                }
            }

            VoidCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    text = "Installed",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                )
                if (state.installed.isEmpty()) {
                    Text(
                        text = "No mods installed for this version yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.installed, key = { it.fileName }) { mod ->
                        InstalledRow(mod = mod, onRemove = { viewModel.remove(mod.fileName) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ModResultRow(
    result: ModSearchResult,
    installing: Boolean,
    enabled: Boolean,
    onInstall: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface.copy(alpha = 0.78f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = result.description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(onClick = onInstall, enabled = enabled) {
            Text(if (installing) "Installing..." else "Install")
        }
    }
}

@Composable
private fun InstalledRow(mod: InstalledMod, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mod.fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${mod.sizeKb} KB",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRemove) {
            Text("Remove")
        }
    }
}
