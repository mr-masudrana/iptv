package com.playerx.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.playerx.app.data.Channel

/**
 * Owns the single ExoPlayer. It is shared by MainActivity (lists) and PlayerActivity (video),
 * so the video can float in picture-in-picture while the list screen is used again.
 */
object PlayerManager {
    private var exo: ExoPlayer? = null
    val player: ExoPlayer
        get() = checkNotNull(exo) { "PlayerManager.init() was not called" }

    var current by mutableStateOf<Channel?>(null)
    var queue by mutableStateOf(emptyList<Channel>()) // channels that next/previous step through
    var playerError by mutableStateOf<String?>(null)

    fun init(context: Context) {
        if (exo != null) return
        val p = ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playerError = "Playback error: ${error.errorCodeName}"
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) playerError = null
            }
        })
        exo = p
    }

    /** [list] is the list the channel was picked from (respects group, favorites and search). */
    fun play(c: Channel, list: List<Channel> = listOf(c)) {
        queue = list
        start(c)
    }

    fun playNext() = step(+1)

    fun playPrevious() = step(-1)

    private fun step(delta: Int) {
        val idx = queue.indexOfFirst { it.url == current?.url }
        if (idx < 0) return
        queue.getOrNull(idx + delta)?.let { start(it) }
    }

    private fun start(c: Channel) {
        current = c
        playerError = null
        val p = player
        p.setMediaItem(MediaItem.fromUri(c.url))
        p.prepare()
        p.play()
    }

    fun stop() {
        val p = player
        p.stop()
        p.clearMediaItems()
        current = null
        queue = emptyList()
        playerError = null
    }
}
