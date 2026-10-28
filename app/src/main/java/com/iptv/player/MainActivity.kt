package com.iptv.player

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var inPip by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addOnPictureInPictureModeChangedListener { info ->
            inPip = info.isInPictureInPictureMode
        }

        setContent {
            IptvTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(vm, inPip, onEnterPip = { enterPipIfPlaying() })
                }
            }
        }
    }

    /** Shrinks the video into a floating window. Returns false when that is not possible. */
    private fun enterPipIfPlaying(): Boolean {
        if (Build.VERSION.SDK_INT < 26 || vm.screen != 2 || !vm.controller.playWhenReady) return false
        return try {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        } catch (e: Exception) {
            false
        }
    }

    // Home button while a channel is playing -> picture-in-picture
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipIfPlaying()
    }

    // No background playback: stop when the app is no longer on screen (PiP keeps it visible)
    override fun onStop() {
        super.onStop()
        vm.onAppStopped()
    }

    override fun onStart() {
        super.onStart()
        vm.onAppStarted()
    }
}
