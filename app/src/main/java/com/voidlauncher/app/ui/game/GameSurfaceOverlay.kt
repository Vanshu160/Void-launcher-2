package com.voidlauncher.app.ui.game

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Process
import android.view.MotionEvent
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.voidlauncher.app.data.launch.GameInput
import com.voidlauncher.app.data.launch.GameSurfaceBridge
import com.voidlauncher.app.data.launch.GameTextInput
import com.voidlauncher.app.data.launch.TouchRouter
import com.voidlauncher.app.data.log.AppLog
import com.voidlauncher.app.ui.settings.ManageLayoutsScreen

/**
 * Full-screen game screen shown above the launcher while Minecraft starts and runs. Its TextureView
 * forwards surface events to [GameSurfaceBridge] and touch and generic motion events to [GameInput].
 *
 * The TextureView stays in the composition from the start of the launch, because the surface it provides
 * is what GLFW needs. Until GLFW holds that surface, a loading status and a Cancel button are drawn over it.
 * Once GLFW holds the surface, the on-screen controls, a gear for the game menu, and the Keyboard and Quit buttons are
 * drawn instead.
 *
 * The game menu opens the in-game layout editor or the layout list. While either is open, the game gets no touches,
 * and held inputs are released when it opens.
 *
 * The overlay is removed when the game stops. Removing it detaches the TextureView, which
 * triggers the destroy callback, so GLFW always gets a destroy before the window goes away.
 */
@Composable
fun GameSurfaceOverlay() {
    val active by GameSurfaceBridge.active.collectAsState()
    if (!active) return
    val ready by GameSurfaceBridge.surfaceReady.collectAsState()
    val status by GameSurfaceBridge.status.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    val layoutOpen = editing || managing

    // Touches belong to a layout screen while it is open. Leaving the composition also turns routing back on.
    DisposableEffect(layoutOpen) {
        TouchRouter.setSuspended(layoutOpen)
        onDispose { TouchRouter.setSuspended(false) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context -> GameTextureView(context) },
        )
        GameKeyboardHost()
        if (ready) {
            when {
                editing -> InGameLayoutEditor(onExit = { editing = false })
                managing -> ManageLayoutsScreen(onDone = { managing = false })
                else -> {
                    TouchControlsOverlay()
                    GameControls(
                        onEditLayout = { editing = true },
                        onReplaceLayout = { managing = true },
                    )
                }
            }
        } else {
            LaunchStatus(status)
        }
    }
}

/**
 * The 1 dp input view the soft keyboard attaches to. It is created once per overlay and attached to
 * [GameTextInput] while the overlay is shown. Leaving the composition detaches it, which also closes the keyboard.
 */
@Composable
private fun GameKeyboardHost() {
    val context = LocalContext.current
    val inputView = remember(context) { GameTextInputView(context) }
    DisposableEffect(inputView) {
        GameTextInput.attach(inputView)
        onDispose { GameTextInput.detach(inputView) }
    }
    AndroidView(
        modifier = Modifier.size(1.dp),
        factory = { inputView },
    )
}

/** The loading indicator, status text and Cancel button shown until GLFW holds the game surface. */
@Composable
private fun LaunchStatus(status: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = status.ifBlank { "Starting Minecraft..." },
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        // Cancel is only offered before Minecraft starts. The launch thread checks for it and then ends the game screen.
        TextButton(onClick = { GameSurfaceBridge.cancelLaunch() }) {
            Text("Cancel", color = Color.White)
        }
    }
}

/**
 * The gear that opens the game menu, and the Keyboard and Quit buttons shown while the game is running. The menu offers
 * the layout editor and the layout list. Quit asks first. The app process is ended, because native Minecraft cannot be
 * stopped safely from inside the process.
 */
@Composable
private fun BoxScope.GameControls(
    onEditLayout: () -> Unit,
    onReplaceLayout: () -> Unit,
) {
    val context = LocalContext.current
    var confirmQuit by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    IconButton(
        onClick = { menuOpen = true },
        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = "Game menu",
            tint = Color.White,
        )
    }

    Row(
        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = {
            if (GameTextInput.isShown()) GameTextInput.hide() else GameTextInput.show(context)
        }) {
            Text("Keyboard", color = Color.White)
        }
        TextButton(onClick = { confirmQuit = true }) {
            Text("Quit", color = Color.White)
        }
    }

    if (menuOpen) {
        AlertDialog(
            onDismissRequest = { menuOpen = false },
            title = { Text("Game menu") },
            text = {
                Column {
                    TextButton(onClick = {
                        menuOpen = false
                        onEditLayout()
                    }) {
                        Text("Edit control layout")
                    }
                    TextButton(onClick = {
                        menuOpen = false
                        onReplaceLayout()
                    }) {
                        Text("Replace control layout")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuOpen = false }) {
                    Text("Close")
                }
            },
        )
    }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("Quit Minecraft?") },
            text = { Text("Unsaved progress will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    AppLog.write("Quit requested from the in-game menu. Closing the app process.")
                    Process.killProcess(Process.myPid())
                }) {
                    Text("Quit")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmQuit = false }) {
                    Text("Stay")
                }
            },
        )
    }
}

/** A TextureView that forwards surface lifecycle events to [GameSurfaceBridge] and input to [GameInput]. */
private class GameTextureView(context: Context) : TextureView(context) {
    init {
        setOpaque(true)
        surfaceTextureListener = object : SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                texture.setDefaultBufferSize(width, height)
                GameSurfaceBridge.onSurfaceCreated(texture, width, height)
            }

            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                texture.setDefaultBufferSize(width, height)
                GameSurfaceBridge.onSurfaceUpdated(width, height)
            }

            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                GameSurfaceBridge.onSurfaceDestroyed(texture)
                // false: the bridge releases the texture after GLFW has finished with the window,
                // so the UI thread never waits for the game.
                return false
            }

            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
        }
    }

    // The game screen takes every touch while it is shown, so none reach the launcher underneath. TouchRouter gives
    // each finger to an on-screen control or to GameInput.
    override fun onTouchEvent(event: MotionEvent): Boolean = TouchRouter.onMotion(this, event)

    override fun onGenericMotionEvent(event: MotionEvent): Boolean =
        GameInput.onGenericMotion(event) || super.onGenericMotionEvent(event)
}
