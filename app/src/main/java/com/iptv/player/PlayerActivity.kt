package com.iptv.player

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle

class PlayerActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels() // used for favorites
    private var inPip by mutableStateOf(false)
    private var resumeOnStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (PlayerManager.current == null) {
            finish()
            return
        }

        addOnPictureInPictureModeChangedListener { info ->
            inPip = info.isInPictureInPictureMode
            // PiP window closed with its X (not expanded): stop and leave
            if (!info.isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) {
                closePlayer()
            }
        }

        // Back: float the video and show the previous page; if PiP is impossible, just close
        onBackPressedDispatcher.addCallback(this) {
            if (!enterPipIfPlaying()) closePlayer()
        }

        setContent {
            IptvTheme {
                Surface(Modifier.fillMaxSize(), color = Color.Black) {
                    PlayerScreen(vm, inPip, onClose = { closePlayer() })
                }
            }
        }
    }

    private fun closePlayer() {
        PlayerManager.stop()
        finishAndRemoveTask()
    }

    private fun enterPipIfPlaying(): Boolean {
        if (Build.VERSION.SDK_INT < 26 || !PlayerManager.player.playWhenReady) return false
        return try {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        } catch (e: Exception) {
            false
        }
    }

    // Home button while playing -> picture-in-picture
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipIfPlaying()
    }

    // No background playback: when the video is no longer on screen, pause it
    override fun onStop() {
        super.onStop()
        val p = PlayerManager.player
        resumeOnStart = p.playWhenReady
        p.pause()
    }

    override fun onStart() {
        super.onStart()
        if (resumeOnStart && PlayerManager.current != null) {
            resumeOnStart = false
            val p = PlayerManager.player
            p.seekToDefaultPosition()
            p.prepare()
            p.play()
        }
    }
}
