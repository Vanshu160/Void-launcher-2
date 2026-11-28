package com.voidlauncher.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.voidlauncher.app.data.launch.TouchControls
import com.voidlauncher.app.data.launch.TouchLayoutFormatException
import com.voidlauncher.app.data.launch.TouchLayoutJson
import java.io.IOException

/**
 * The list of control layouts. The active layout is the one the game uses. Each layout can be made active, exported or
 * deleted, a new one can be started from the active layout, and a file can be imported. The built-in default cannot be
 * deleted; it can only be restored to its original buttons.
 *
 * Used from Settings and from the game's menu. Editing the active layout happens in the game, through the game menu.
 */
@Composable
fun ManageLayoutsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val names by TouchControls.names.collectAsState()
    val active by TouchControls.activeName.collectAsState()
    val colors = MaterialTheme.colorScheme
    var message by remember { mutableStateOf<String?>(null) }
    var exportName by remember { mutableStateOf(TouchControls.DEFAULT_NAME) }
    var deleteName by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            message = try {
                val bytes = TouchLayoutJson.encode(TouchControls.loadNamed(exportName)).toByteArray(Charsets.UTF_8)
                val stream = context.contentResolver.openOutputStream(uri)
                    ?: throw IOException("The file could not be opened.")
                stream.use { it.write(bytes) }
                "Exported \"$exportName\"."
            } catch (e: IOException) {
                "The layout could not be exported."
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            message = try {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("The file could not be opened.")
                val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val layout = TouchLayoutJson.decode(text)
                val name = TouchControls.addLayout(layout, "Imported layout")
                "Imported as \"$name\"."
            } catch (e: TouchLayoutFormatException) {
                e.message ?: "The file is not a valid layout."
            } catch (e: IOException) {
                "The layout could not be read."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Control layouts",
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
        )
        Text(
            text = "The layout in use is the one the game shows. Open the game's menu and choose Edit Control Layout to change it.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = {
                TouchControls.addLayout(TouchControls.layout.value, "New layout")
                message = "A new layout was started from the one in use."
            }) {
                Text("New")
            }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) }) {
                Text("Import")
            }
        }
        message?.let { text ->
            Text(text = text, style = MaterialTheme.typography.bodySmall, color = colors.primary)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(listOf(TouchControls.DEFAULT_NAME) + names, key = { it }) { name ->
                LayoutRow(
                    name = name,
                    isActive = name == active,
                    isDefault = name == TouchControls.DEFAULT_NAME,
                    onUse = {
                        TouchControls.setActive(name)
                        message = "\"$name\" is now the layout in use."
                    },
                    onExport = {
                        exportName = name
                        exportLauncher.launch("$name.json")
                    },
                    onRemove = {
                        if (name == TouchControls.DEFAULT_NAME) {
                            TouchControls.restoreDefault()
                            message = "The default layout was restored."
                        } else {
                            deleteName = name
                        }
                    },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onDone) {
                Text("Done")
            }
        }
    }

    deleteName?.let { name ->
        AlertDialog(
            onDismissRequest = { deleteName = null },
            title = { Text("Delete \"$name\"?") },
            text = { Text("This layout will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    TouchControls.deleteLayout(name)
                    message = "\"$name\" was deleted."
                    deleteName = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteName = null }) {
                    Text("Keep")
                }
            },
        )
    }
}

/** One layout in the list, with its actions. */
@Composable
private fun LayoutRow(
    name: String,
    isActive: Boolean,
    isDefault: Boolean,
    onUse: () -> Unit,
    onExport: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(text = name, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                Text(
                    text = when {
                        isActive -> "In use"
                        isDefault -> "Built in"
                        else -> "Saved"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) colors.primary else colors.onSurfaceVariant,
                )
            }
            if (!isActive) {
                Button(onClick = onUse) {
                    Text("Use")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onExport) {
                Text("Export")
            }
            TextButton(onClick = onRemove) {
                Text(if (isDefault) "Restore default" else "Delete")
            }
        }
    }
}
