package com.iptv.player

import android.app.Application
import android.content.ComponentName
import android.content.Context
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

    private val prefs = app.getSharedPreferences("iptv", Context.MODE_PRIVATE)
    private val db = AppDatabase.get(app)
    private val favDao = db.favoriteDao()
    private val playlistDao = db.playlistDao()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // ---- UI state ----
    var urlInput by mutableStateOf("")
    var channels by mutableStateOf(emptyList<Channel>())
    var activePlaylistId by mutableStateOf(prefs.getLong("active", -1L))
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var selectedGroup by mutableStateOf<String?>(null)
    var tab by mutableStateOf(0) // 0 = Live, 1 = Favorites, 2 = Playlists
    var current by mutableStateOf<Channel?>(null)
    var playerError by mutableStateOf<String?>(null)

    // Player (lives in PlaybackService; we talk to it through a MediaController)
    var controller by mutableStateOf<MediaController?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pending: Channel? = null

    val favorites: StateFlow<List<Channel>> = favDao.all()
        .map { list -> list.map { Channel(it.name, it.url, it.logo, it.groupName) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // null = still loading from the database
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
                c.currentMediaItem?.let { mi ->
                    current = Channel(
                        mi.mediaMetadata.title?.toString().orEmpty(),
                        mi.localConfiguration?.uri?.toString().orEmpty(),
                        mi.mediaMetadata.artworkUri?.toString(),
                        ""
                    )
                }
                pending?.let {
                    pending = null
                    play(it)
                }
            } catch (e: Exception) {
                playerError = "Player unavailable"
            }
        }, ContextCompat.getMainExecutor(app))

        // Re-open the last used playlist
        viewModelScope.launch {
            val list = playlistDao.allOnce()
            val active = list.firstOrNull { it.id == activePlaylistId } ?: list.firstOrNull()
            if (active != null) selectPlaylist(active)
        }
    }

    // ---- Playlists ----

    fun addPlaylist(source: String, isFile: Boolean, name: String) {
        val s = source.trim()
        if (s.isEmpty()) {
            error = "Enter a playlist URL"
            return
        }
        runLoad {
            val parsed = fetch(s, isFile)
            if (parsed.isEmpty()) {
                error = "No channels found in this playlist"
            } else {
                val title = name.ifBlank { Uri.parse(s).host ?: "Playlist" }
                val id = playlistDao.insert(PlaylistEntity(name = title, source = s, isFile = isFile))
                applyChannels(id, parsed)
            }
        }
    }

    fun importFile(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // not all providers allow persistable permission; reading now still works
        }
        val name = try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) {
            null
        } ?: "Local playlist"
        addPlaylist(uri.toString(), true, name)
    }

    fun selectPlaylist(p: PlaylistEntity) {
        runLoad {
            val parsed = fetch(p.source, p.isFile)
            if (parsed.isEmpty()) error = "No channels found in this playlist"
            else applyChannels(p.id, parsed)
        }
    }

    fun deletePlaylist(p: PlaylistEntity) {
        viewModelScope.launch {
            playlistDao.delete(p.id)
            if (activePlaylistId == p.id) {
                activePlaylistId = -1L
                channels = emptyList()
                prefs.edit().remove("active").apply()
            }
        }
    }

    private fun applyChannels(id: Long, parsed: List<Channel>) {
        channels = parsed
        selectedGroup = null
        tab = 0
        activePlaylistId = id
        prefs.edit().putLong("active", id).apply()
    }

    private fun runLoad(block: suspend () -> Unit) {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                block()
            } catch (e: Exception) {
                error = e.message ?: "Failed to load playlist"
            } finally {
                loading = false
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
