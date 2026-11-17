package com.biaruuu.kiro

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.biaruuu.kiro.ui.KiroApp
import com.biaruuu.kiro.ui.theme.KiroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        handleIntent(intent)
        setContent {
            KiroTheme {
                KiroApp()
            }
        }
    }

    /** Pressing Home while a video plays shrinks it into the floating window. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (com.biaruuu.kiro.ui.player.PipState.canEnter) com.biaruuu.kiro.ui.player.PipState.enter(this)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        com.biaruuu.kiro.ui.player.PipState.inPip.value = isInPictureInPictureMode
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent?.getStringExtra(com.biaruuu.kiro.data.UpdateWorker.EXTRA_OPEN) == "update") {
            com.biaruuu.kiro.data.UpdateChecker.request.value = "latest"
            intent.removeExtra(com.biaruuu.kiro.data.UpdateWorker.EXTRA_OPEN)
        } else if (intent?.getStringExtra(com.biaruuu.kiro.data.UpdateWorker.EXTRA_OPEN) == "notifications") {
            com.biaruuu.kiro.data.UpdateChecker.request.value = "notifications"
            intent.removeExtra(com.biaruuu.kiro.data.UpdateWorker.EXTRA_OPEN)
        } else if (intent?.getStringExtra(com.biaruuu.kiro.data.UpdateWorker.EXTRA_OPEN) == "anime") {
            val id = intent.getIntExtra("anime_id", 0)
            if (id > 0) com.biaruuu.kiro.data.DeepLink.request.value = "d:$id"
            intent.removeExtra(com.biaruuu.kiro.data.UpdateWorker.EXTRA_OPEN)
        }
    }
}
