package com.voidlauncher.app.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voidlauncher.app.data.log.AppLog
import com.voidlauncher.app.ui.components.VoidCard

@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val selected = state.selectedId
    var refresh by remember { mutableIntStateOf(0) }
    val logText = remember(state.message, state.isLaunching, refresh) { AppLog.read() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Game files",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onBackground,
        )

        VoidCard {
            Text(
                text = if (selected == null) "No version selected" else "Selected: Minecraft $selected",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            Text(
                text = "Downloads the client, libraries and assets. Files already on this device are checked and skipped.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface.copy(alpha = 0.7f),
            )

            if (state.isDownloading) {
                val progress = state.progress
                if (progress != null) {
                    Text(
                        text = "${progress.stage}: ${progress.done} / ${progress.total}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                    )
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            Button(
                onClick = viewModel::startDownload,
                enabled = selected != null && !state.isDownloading && !state.isLaunching,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isDownloading) "Downloading..." else "Download game files")
            }

            if (state.isDownloading) {
                OutlinedButton(
                    onClick = viewModel::cancel,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel")
                }
            }

            Button(
                onClick = viewModel::startGame,
                enabled = selected != null && !state.isDownloading && !state.isLaunching,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isLaunching) "Starting..." else "Start game (first test)")
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.isError) colors.error else colors.primary,
                )
            }
        }

        VoidCard {
            Text(
                text = "Launch log (long-press to copy)",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            SelectionContainer {
                Text(
                    text = logText.ifEmpty { "No log yet. Tap Start game to create one." },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.onSurface,
                )
            }
            OutlinedButton(
                onClick = {
                    AppLog.clear()
                    refresh++
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear log")
            }
        }
    }
}
