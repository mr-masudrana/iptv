package com.iptv.player

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    private val prefs = app.getSharedPreferences("iptv", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // ---- UI state ----
    var screen by mutableStateOf(0) // 0 = playlists, 1 = channels
    var activePlaylist by mutableStateOf<PlaylistEntity?>(null)
    var channels by mutableStateOf(emptyList<Channel>())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var selectedGroup by mutableStateOf<String?>(null)
    var showFavorites by mutableStateOf(false)

    // Single-link plays, newest first
    var streamHistory by mutableStateOf(loadHistory())

    val favorites: StateFlow<List<Channel>> = favDao.all()
        .map { list -> list.map { Channel(it.name, it.url, it.logo, it.groupName) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val playlists: StateFlow<List<PlaylistEntity>?> = playlistDao.all()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---- Stream-link history ----

    private fun loadHistory(): List<String> =
        prefs.getString("stream_history", "").orEmpty().split("\n").filter { it.isNotBlank() }

    private fun saveHistory(list: List<String>) {
        streamHistory = list
        prefs.edit().putString("stream_history", list.joinToString("\n")).apply()
    }

    fun addStreamHistory(url: String) {
        val u = url.trim()
        if (u.isEmpty()) return
        saveHistory((listOf(u) + streamHistory.filter { it != u }).take(20))
    }

    fun removeStreamHistory(url: String) = saveHistory(streamHistory.filter { it != url })

    fun clearStreamHistory() = saveHistory(emptyList())

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

    fun toggleFavorite(c: Channel) {
        viewModelScope.launch {
            if (favorites.value.any { it.url == c.url }) favDao.delete(c.url)
            else favDao.insert(FavoriteEntity(c.url, c.name, c.logo, c.group))
        }
    }
}
