package com.voidlauncher.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.voidlauncher.app.data.launch.GameInput
import com.voidlauncher.app.data.launch.GameSurfaceBridge
import com.voidlauncher.app.ui.game.GameSurfaceOverlay
import com.voidlauncher.app.ui.navigation.VoidApp
import com.voidlauncher.app.ui.theme.VoidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            VoidTheme {
                // The game surface sits above the launcher. Its state lives in GameSurfaceBridge, so it
                // survives rotation and backgrounding, and the surface is recreated by the TextureView.
                Box(modifier = Modifier.fillMaxSize()) {
                    VoidApp()
                    GameSurfaceOverlay()
                }
            }
        }
    }

    /** Releases every held key and button when the app leaves the foreground, so nothing stays pressed in the game. */
    override fun onPause() {
        super.onPause()
        GameSurfaceBridge.onAppPaused()
    }

    /**
     * Key events reach the activity rather than the game view, so while the game screen is shown they are
     * routed to [GameInput] first. Everything else follows the normal path.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (GameSurfaceBridge.active.value && GameInput.onKey(event)) return true
        return super.dispatchKeyEvent(event)
    }
}
