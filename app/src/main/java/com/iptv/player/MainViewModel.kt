package com.iptv.player

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.iptv.player.data.AppDatabase
import com.iptv.player.data.Channel
import com.iptv.player.data.FavoriteEntity
import com.iptv.player.data.M3uParser
import com.iptv.player.data.PlaylistEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val favDao = db.favoriteDao()
    private val playlistDao = db.playlistDao()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // ---- UI state ----
    var screen by mutableStateOf(0) // 0 = playlists, 1 = channels, 2 = player
    var activePlaylist by mutableStateOf<PlaylistEntity?>(null)
    var channels by mutableStateOf(emptyList<Channel>())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var selectedGroup by mutableStateOf<String?>(null)
    var showFavorites by mutableStateOf(false)
    var current by mutableStateOf<Channel?>(null)
    var playerError by mutableStateOf<String?>(null)
    var queue by mutableStateOf(emptyList<Channel>()) // channels next/previous step through

    private var playerBackScreen = 1 // where the player's close button returns to
    private var resumeOnStart = false

    // The player lives and dies with the app screen: no background playback.
    val controller: ExoPlayer = ExoPlayer.Builder(app)
        .setAudioAttributes(AudioAttributes.DEFAULT, true)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    val favorites: StateFlow<List<Channel>> = favDao.all()
        .map { list -> list.map { Channel(it.name, it.url, it.logo, it.groupName) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val playlists: StateFlow<List<PlaylistEntity>?> = playlistDao.all()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        controller.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playerError = "Playback error: ${error.errorCodeName}"
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) playerError = null
            }
        })
    }

    // ---- App lifecycle (called by MainActivity) ----

    /** App left the screen (and is not in picture-in-picture): stop playing. */
    fun onAppStopped() {
        resumeOnStart = screen == 2 && controller.playWhenReady
        controller.pause()
    }

    /** App is visible again: pick the stream up at the live edge. */
    fun onAppStarted() {
        if (resumeOnStart) {
            resumeOnStart = false
            controller.seekToDefaultPosition()
            controller.prepare()
            controller.play()
        }
    }

    // ---- Playlist list (home page) ----

    fun addPlaylistUrl(name: String, url: String) {
        val u = url.trim()
        if (u.isEmpty()) return
        viewModelScope.launch {
            val title = name.trim().ifBlank { Uri.parse(u).host ?: "Playlist" }
            val next = (playlistDao.maxPosition() ?: -1) + 1
            playlistDao.insert(PlaylistEntity(name = title, source = u, isFile = false, position = next))
        }
    }

    fun importFile(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // not every provider allows a persistable permission; reading right now still works
        }
        val name = try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) {
            null
        } ?: "Local playlist"
        viewModelScope.launch {
            val next = (playlistDao.maxPosition() ?: -1) + 1
            playlistDao.insert(PlaylistEntity(name = name, source = uri.toString(), isFile = true, position = next))
        }
    }

    fun updatePlaylist(p: PlaylistEntity, name: String, source: String) {
        viewModelScope.launch {
            playlistDao.update(
                p.copy(
                    name = name.trim().ifBlank { p.name },
                    source = if (p.isFile) p.source else source.trim().ifBlank { p.source }
                )
            )
        }
    }

    fun deletePlaylist(p: PlaylistEntity) {
        viewModelScope.launch { playlistDao.delete(p.id) }
    }

    fun reorderPlaylists(newOrder: List<PlaylistEntity>) {
        viewModelScope.launch {
            playlistDao.updateAll(newOrder.mapIndexed { i, p -> p.copy(position = i) })
        }
    }

    // ---- Channels page ----

    fun openPlaylist(p: PlaylistEntity) {
        activePlaylist = p
        channels = emptyList()
        query = ""
        selectedGroup = null
        showFavorites = false
        error = null
        screen = 1
        load(p)
    }

    fun refresh() {
        activePlaylist?.let { load(it) }
    }

    fun closeChannels() {
        query = ""
        showFavorites = false
        selectedGroup = null
        screen = 0
    }

    private fun load(p: PlaylistEntity) {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val parsed = fetch(p.source, p.isFile)
                if (activePlaylist?.id == p.id) {
                    if (parsed.isEmpty()) error = "No channels found in this playlist"
                    else channels = parsed
                }
            } catch (e: Exception) {
                if (activePlaylist?.id == p.id) error = e.message ?: "Failed to load playlist"
            } finally {
                if (activePlaylist?.id == p.id) loading = false
            }
        }
    }

    private suspend fun fetch(source: String, isFile: Boolean): List<Channel> =
        withContext(Dispatchers.IO) {
            val text = if (isFile) {
                getApplication<Application>().contentResolver
                    .openInputStream(Uri.parse(source))
                    ?.bufferedReader()?.use { it.readText() }
                    ?: throw IOException("Cannot read file")
            } else {
                val req = Request.Builder().url(source).header("User-Agent", "Mozilla/5.0").build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    resp.body?.string().orEmpty()
                }
            }
            M3uParser.parse(text)
        }

    // ---- Player page ----

    /** [list] is the list the channel was picked from (respects group, favorites and search). */
    fun openChannel(c: Channel, list: List<Channel> = listOf(c)) {
        playerBackScreen = 1
        queue = list
        play(c)
        screen = 2
    }

    fun playNext() = step(+1)

    fun playPrevious() = step(-1)

    private fun step(delta: Int) {
        val idx = queue.indexOfFirst { it.url == current?.url }
        if (idx < 0) return
        queue.getOrNull(idx + delta)?.let { play(it) }
    }

    /** Play a single stream link (e.g. one .m3u8) straight from the home page. */
    fun playStream(url: String) {
        val u = url.trim()
        if (u.isEmpty()) return
        playerBackScreen = 0
        queue = emptyList()
        play(Channel(Uri.parse(u).host ?: "Stream", u, null, ""))
        screen = 2
    }

    fun closePlayer() {
        resumeOnStart = false
        controller.stop()
        controller.clearMediaItems()
        current = null
        queue = emptyList()
        playerError = null
        screen = playerBackScreen
    }

    private fun play(c: Channel) {
        current = c
        playerError = null
        controller.setMediaItem(MediaItem.fromUri(c.url))
        controller.prepare()
        controller.play()
    }

    fun toggleFavorite(c: Channel) {
        viewModelScope.launch {
            if (favorites.value.any { it.url == c.url }) favDao.delete(c.url)
            else favDao.insert(FavoriteEntity(c.url, c.name, c.logo, c.group))
        }
    }

    override fun onCleared() {
        controller.release()
    }
}
