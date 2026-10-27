package com.iptv.player

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
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
    var screen by mutableStateOf(0) // 0 = playlist list, 1 = live channels
    var activePlaylist by mutableStateOf<PlaylistEntity?>(null)
    var channels by mutableStateOf(emptyList<Channel>())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var selectedGroup by mutableStateOf<String?>(null)
    var showFavorites by mutableStateOf(false)
    var current by mutableStateOf<Channel?>(null)
    var playerError by mutableStateOf<String?>(null)

    // Player lives in PlaybackService; we talk to it through a MediaController
    var controller by mutableStateOf<MediaController?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pending: Channel? = null

    val favorites: StateFlow<List<Channel>> = favDao.all()
        .map { list -> list.map { Channel(it.name, it.url, it.logo, it.groupName) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val playlists: StateFlow<List<PlaylistEntity>?> = playlistDao.all()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            playerError = "Playback error: ${error.errorCodeName}"
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) playerError = null
        }
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val c = future.get()
                c.addListener(playerListener)
                controller = c
                pending?.let {
                    pending = null
                    play(it)
                }
            } catch (e: Exception) {
                playerError = "Player unavailable"
            }
        }, ContextCompat.getMainExecutor(app))
    }

    // ---- Playlist list (home page) ----

    fun addPlaylistUrl(name: String, url: String) {
        val u = url.trim()
        if (u.isEmpty()) return
        viewModelScope.launch {
            val title = name.trim().ifBlank { Uri.parse(u).host ?: "Playlist" }
            playlistDao.insert(PlaylistEntity(name = title, source = u, isFile = false))
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
            playlistDao.insert(PlaylistEntity(name = name, source = uri.toString(), isFile = true))
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

    // ---- Live page ----

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

    fun closeLive() {
        pending = null
        controller?.run {
            stop()
            clearMediaItems()
        }
        current = null
        playerError = null
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

    // ---- Playback ----

    fun play(c: Channel) {
        current = c
        playerError = null
        val ctrl = controller
        if (ctrl == null) {
            pending = c
            return
        }
        val item = MediaItem.Builder()
            .setUri(c.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(c.name)
                    .setArtworkUri(c.logo?.let { Uri.parse(it) })
                    .build()
            )
            .build()
        ctrl.setMediaItem(item)
        ctrl.prepare()
        ctrl.play()
    }

    fun toggleFavorite(c: Channel) {
        viewModelScope.launch {
            if (favorites.value.any { it.url == c.url }) favDao.delete(c.url)
            else favDao.insert(FavoriteEntity(c.url, c.name, c.logo, c.group))
        }
    }

    override fun onCleared() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
