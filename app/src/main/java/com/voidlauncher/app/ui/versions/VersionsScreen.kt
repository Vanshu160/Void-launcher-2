package com.voidlauncher.app.ui.versions

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voidlauncher.app.domain.model.MinecraftVersion
import com.voidlauncher.app.domain.model.VersionFilter
import com.voidlauncher.app.domain.model.VersionType

@Composable
fun VersionsScreen(viewModel: VersionsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    val statusLine = when {
        state.isLoading -> "Checking Mojang for versions..."
        state.fromCache -> "Offline: showing the saved list"
        else -> "${state.versions.size} versions available"
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Versions",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onBackground,
                )
                Text(
                    text = statusLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh versions",
                    tint = colors.primary,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VersionFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { viewModel.setFilter(filter) },
                    label = { Text(filter.label) },
                )
            }
        }

        state.errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.error,
            )
        }

        if (state.visibleVersions.isEmpty() && !state.isLoading && state.errorMessage == null) {
            Text(
                text = "No versions to show.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.visibleVersions, key = { it.id }) { version ->
                VersionRow(
                    version = version,
                    selected = version.id == state.selectedId,
                    onSelect = { viewModel.select(version.id) },
                )
            }
        }
    }
}

@Composable
private fun VersionRow(
    version: MinecraftVersion,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (selected) colors.primary.copy(alpha = 0.18f) else colors.surface.copy(alpha = 0.78f),
        label = "version-row-background",
    )
    val (label, labelColor) = when (version.type) {
        VersionType.RELEASE -> "Release" to colors.primary
        VersionType.SNAPSHOT -> "Snapshot" to colors.secondary
        VersionType.OTHER -> "Other" to colors.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                text = version.id,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            Text(
                text = version.releaseTime.take(10),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
        )
    }
}
