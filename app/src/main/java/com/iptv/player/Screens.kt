package com.iptv.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.iptv.player.data.Channel
import com.iptv.player.data.PlaylistEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// ───────────────────────── Root: Playlist → Channels → Player ─────────────────────────

@Composable
fun AppRoot(vm: MainViewModel) {
    // Back on the channels page returns to the playlists; on the playlists it leaves the app
    BackHandler(enabled = vm.screen == 1) { vm.closeChannels() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (vm.screen) {
            1 -> ChannelsScreen(vm)
            else -> PlaylistScreen(vm)
        }
    }
}

/** The video page lives in its own activity so it can float (PiP) over these pages. */
private fun openPlayer(context: Context) {
    context.startActivity(Intent(context, PlayerActivity::class.java))
}

private fun openChannel(context: Context, c: Channel, list: List<Channel>) {
    PlayerManager.play(c, list)
    openPlayer(context)
}

private fun playStream(context: Context, url: String) {
    val u = url.trim()
    if (u.isEmpty()) return
    PlayerManager.play(Channel(Uri.parse(u).host ?: "Stream", u, null, ""))
    openPlayer(context)
}

// ───────────────────────── Playlist page ─────────────────────────

@Composable
fun PlaylistScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val playlists by vm.playlists.collectAsState()
    val list = playlists.orEmpty()
    var menu by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showStream by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PlaylistEntity?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(uri)
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_app_logo),
                contentDescription = null,
                modifier = Modifier.size(38.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "IPTV Player",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Add URL") },
                        onClick = {
                            menu = false
                            showAdd = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Select File") },
                        onClick = {
                            menu = false
                            picker.launch(arrayOf("*/*"))
                        }
                    )
                }
            }
        }

        if (list.isEmpty() && playlists != null) {
            EmptyState(
                title = "No playlists yet",
                body = "Tap the menu at the top right, then choose Add URL or Select File."
            )
        } else {
            ReorderablePlaylists(vm, list, onEdit = { editing = it })
        }
    }

        FloatingActionButton(
            onClick = { showStream = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) { Icon(Icons.Filled.PlayArrow, contentDescription = "Play a stream link") }
    }

    if (showStream) {
        StreamDialog(
            history = vm.streamHistory,
            onDismiss = { showStream = false },
            onPlay = { u ->
                vm.addStreamHistory(u)
                showStream = false
                playStream(context, u)
            },
            onRemove = { vm.removeStreamHistory(it) },
            onClear = { vm.clearStreamHistory() }
        )
    }

    if (showAdd) {
        AddUrlDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, url ->
                vm.addPlaylistUrl(name, url)
                showAdd = false
            }
        )
    }
    editing?.let { p ->
        EditPlaylistDialog(
            p = p,
            onDismiss = { editing = null },
            onSave = { name, source ->
                vm.updatePlaylist(p, name, source)
                editing = null
            },
            onDelete = {
                vm.deletePlaylist(p)
                editing = null
            }
        )
    }
}

/** Playlist list: long-press a card, then drag it up or down to reorder. */
@Composable
fun ReorderablePlaylists(
    vm: MainViewModel,
    list: List<PlaylistEntity>,
    onEdit: (PlaylistEntity) -> Unit
) {
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentList by rememberUpdatedState(list)

    var local by remember { mutableStateOf<List<PlaylistEntity>?>(null) } // order while dragging
    var draggingKey by remember { mutableStateOf<Long?>(null) }
    var dragDelta by remember { mutableStateOf(0f) }
    var suppressClick by remember { mutableStateOf(false) }
    val shown = local ?: list

    // Drop the temporary order once the database order has caught up
    LaunchedEffect(list) {
        val l = local
        if (l != null && draggingKey == null) {
            val a = l.map { it.id }
            val b = list.map { it.id }
            if (a == b || a.toSet() != b.toSet()) local = null
        }
    }

    fun trySwap() {
        val key = draggingKey ?: return
        val infos = listState.layoutInfo.visibleItemsInfo
        val info = infos.firstOrNull { it.key == key } ?: return
        val center = info.offset + dragDelta + info.size / 2f
        val target = infos.firstOrNull {
            it.key != key && center >= it.offset && center <= it.offset + it.size
        } ?: return
        val cur = local ?: return
        val from = cur.indexOfFirst { it.id == key }
        val to = cur.indexOfFirst { it.id == target.key }
        if (from < 0 || to < 0 || from == to) return
        local = cur.toMutableList().apply { add(to, removeAt(from)) }
        val newOffset = if (to > from) target.offset + target.size - info.size else target.offset
        dragDelta += (info.offset - newOffset)
    }

    fun finish(commit: Boolean) {
        val l = local
        draggingKey = null
        dragDelta = 0f
        if (commit && l != null && l.map { it.id } != currentList.map { it.id }) {
            vm.reorderPlaylists(l)
        } else {
            local = null
        }
        scope.launch {
            delay(150)
            suppressClick = false
        }
    }

    // Keep scrolling while the dragged card is held near the top or bottom edge
    LaunchedEffect(draggingKey) {
        while (draggingKey != null) {
            val key = draggingKey
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
            if (info != null) {
                val top = info.offset + dragDelta
                val bottom = top + info.size
                val step = when {
                    bottom > listState.layoutInfo.viewportEndOffset - 40 -> 16f
                    top < listState.layoutInfo.viewportStartOffset + 40 -> -16f
                    else -> 0f
                }
                if (step != 0f) {
                    dragDelta += listState.scrollBy(step)
                    trySwap()
                }
            }
            delay(16)
        }
    }

    LazyColumn(
        state = listState,
        userScrollEnabled = draggingKey == null,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val y = offset.y.toInt()
                        val hit = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { y >= it.offset && y <= it.offset + it.size }
                        if (hit != null) {
                            draggingKey = hit.key as? Long
                            dragDelta = 0f
                            local = currentList
                            suppressClick = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { change, amount ->
                        if (draggingKey != null) {
                            change.consume()
                            dragDelta += amount.y
                            trySwap()
                        }
                    },
                    onDragEnd = { finish(true) },
                    onDragCancel = { finish(false) }
                )
            }
    ) {
        items(shown, key = { it.id }) { p ->
            val dragging = p.id == draggingKey
            PlaylistCard(
                p,
                modifier = Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (dragging) dragDelta else 0f
                        scaleX = if (dragging) 1.03f else 1f
                        scaleY = if (dragging) 1.03f else 1f
                    },
                onClick = { if (!suppressClick) vm.openPlaylist(p) },
                onEdit = { onEdit(p) }
            )
        }
    }
}

@Composable
fun PlaylistCard(
    p: PlaylistEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surface)
            .border(1.dp, cs.outline, shape)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                p.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                p.source,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit playlist")
        }
    }
}

@Composable
fun AddUrlDialog(onDismiss: () -> Unit, onAdd: (name: String, url: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Add URL") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("M3U link") },
                    placeholder = { Text("https://") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, url) },
                enabled = url.trim().startsWith("http", ignoreCase = true)
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun StreamDialog(
    history: List<String>,
    onDismiss: () -> Unit,
    onPlay: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var url by remember { mutableStateOf("") }
    val canPlay = url.trim().startsWith("http", ignoreCase = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        title = { Text("Play a stream link") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Stream link (.m3u8)") },
                    placeholder = { Text("https://") },
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { if (canPlay) onPlay(url) }),
                    modifier = Modifier.fillMaxWidth()
                )
                if (history.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "History",
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClear) { Text("Clear") }
                    }
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(history, key = { it }) { h ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onPlay(h) }
                                    .padding(start = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    h,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(vertical = 8.dp)
                                )
                                IconButton(
                                    onClick = { onRemove(h) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Remove from history",
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPlay(url) }, enabled = canPlay) { Text("Play") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun EditPlaylistDialog(
    p: PlaylistEntity,
    onDismiss: () -> Unit,
    onSave: (name: String, source: String) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(p.name) }
    var source by remember { mutableStateOf(p.source) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Edit playlist") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text(if (p.isFile) "File" else "M3U link") },
                    enabled = !p.isFile,
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { onSave(name, source) }) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ───────────────────────── Channels page ─────────────────────────

@Composable
fun ChannelsScreen(vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val favs by vm.favorites.collectAsState()
    val favUrls = remember(favs) { favs.map { it.url }.toSet() }
    var searching by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }
    // Registered after AppRoot's handler, so it wins while the search box is open
    BackHandler(enabled = searching) {
        searching = false
        vm.query = ""
    }

    val groups = remember(vm.channels) { vm.channels.map { it.group }.distinct().sorted() }
    val source = if (vm.showFavorites) favs else vm.channels
    val list = remember(source, vm.query, vm.selectedGroup, vm.showFavorites) {
        source.filter { c ->
            (vm.showFavorites || vm.selectedGroup == null || c.group == vm.selectedGroup) &&
                c.name.contains(vm.query, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Top bar: back, playlist name, search
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (searching) {
                IconButton(onClick = {
                    searching = false
                    vm.query = ""
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search") }
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = { vm.query = it },
                    placeholder = { Text("Search channels") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).focusRequester(focus).padding(end = 8.dp)
                )
            } else {
                IconButton(onClick = { vm.closeChannels() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        vm.activePlaylist?.name.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${list.size} channels",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                IconButton(onClick = { searching = true }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
            }
        }

        // Categories: All, Favorites, then the playlist's groups
        if (vm.channels.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Pill("All", !vm.showFavorites && vm.selectedGroup == null) {
                        vm.showFavorites = false
                        vm.selectedGroup = null
                    }
                }
                item {
                    Pill("Favorites", vm.showFavorites) {
                        vm.showFavorites = true
                        vm.selectedGroup = null
                    }
                }
                items(groups) { g ->
                    Pill(g, !vm.showFavorites && vm.selectedGroup == g) {
                        vm.showFavorites = false
                        vm.selectedGroup = g
                    }
                }
            }
        }

        PullRefreshBox(
            refreshing = vm.loading,
            onRefresh = { vm.refresh() },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            when {
                vm.error != null && vm.channels.isEmpty() -> EmptyState(
                    title = "Couldn't load playlist",
                    body = vm.error.orEmpty() + "\nPull down to try again."
                )
                vm.channels.isEmpty() && !vm.loading -> EmptyState(
                    title = "No channels found",
                    body = "Pull down to refresh."
                )
                vm.showFavorites && favs.isEmpty() -> EmptyState(
                    title = "No favorites yet",
                    body = "Tap the heart on a channel to keep it here."
                )
                list.isEmpty() && !vm.loading -> EmptyState(
                    title = "No matches",
                    body = "Try a different name or group."
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(list) { c ->
                        ChannelCard(
                            c = c,
                            isFav = c.url in favUrls,
                            onClick = { openChannel(context, c, list) },
                            onFav = { vm.toggleFavorite(c) }
                        )
                    }
                }
            }
        }
    }
}

/** Pull the list down from the top to refresh; shows a small spinner while [refreshing]. */
@Composable
fun PullRefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val thresholdPx = with(density) { 72.dp.toPx() }
    val restPx = with(density) { 56.dp.toPx() }
    var pull by remember { mutableStateOf(0f) }
    val isRefreshing by rememberUpdatedState(refreshing)
    val doRefresh by rememberUpdatedState(onRefresh)

    val connection = remember(thresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pull > 0f && available.y < 0f) {
                    val used = maxOf(available.y, -pull)
                    pull += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!isRefreshing && source == NestedScrollSource.Drag && available.y > 0f) {
                    pull = (pull + available.y * 0.5f).coerceAtMost(thresholdPx * 1.6f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull >= thresholdPx && !isRefreshing) doRefresh()
                pull = 0f
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                pull = 0f
                return Velocity.Zero
            }
        }
    }

    val shown by animateFloatAsState(if (refreshing) restPx else pull, label = "pull")

    Box(modifier.nestedScroll(connection)) {
        content()
        if (shown > 1f) {
            Surface(
                shape = CircleShape,
                color = cs.surfaceVariant,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(40.dp)
                    .graphicsLayer { translationY = shown - size.height }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = cs.primary
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { (pull / thresholdPx).coerceIn(0f, 1f) },
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = cs.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelCard(c: Channel, isFav: Boolean, onClick: () -> Unit, onFav: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .clip(shape)
            .background(cs.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            ChannelLogo(
                c,
                Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape).background(Color.White)
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000))
                    .clickable(onClick = onFav),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFav) HeartPink else Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            c.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ChannelLogo(c: Channel, modifier: Modifier) {
    var loaded by remember(c.logo) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (!loaded) {
            Image(
                painter = painterResource(R.drawable.ic_app_logo),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.62f)
            )
        }
        if (!c.logo.isNullOrBlank()) {
            AsyncImage(
                model = c.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onState = { loaded = it is AsyncImagePainter.State.Success },
                modifier = Modifier.fillMaxSize().padding(12.dp)
            )
        }
    }
}

@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = cs.surfaceVariant,
            labelColor = cs.onSurfaceVariant,
            selectedContainerColor = cs.primary,
            selectedLabelColor = cs.onPrimary
        )
    )
}

@Composable
fun EmptyState(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ───────────────────────── Player page ─────────────────────────

private val RESIZE_MODES = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Fill",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom"
)

private fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun currentBrightness(activity: Activity?): Float {
    val a = activity ?: return 0.5f
    val set = a.window.attributes.screenBrightness
    if (set >= 0f) return set
    return try {
        Settings.System.getInt(a.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    } catch (e: Exception) {
        0.5f
    }
}

private fun Player.setCaptions(on: Boolean) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !on)
        .build()
}

private data class AudioOption(
    val group: Tracks.Group,
    val index: Int,
    val label: String,
    val selected: Boolean
)

/** Distinct video heights (e.g. 1080, 720, 480) the stream offers, best first. */
private fun videoHeights(tracks: Tracks): List<Int> {
    val heights = mutableSetOf<Int>()
    for (g in tracks.groups) {
        if (g.type != C.TRACK_TYPE_VIDEO) continue
        for (i in 0 until g.length) {
            val h = g.getTrackFormat(i).height
            if (g.isTrackSupported(i) && h > 0) heights.add(h)
        }
    }
    return heights.sortedDescending()
}

/** Every selectable audio track (dual audio, several languages). */
private fun audioOptions(tracks: Tracks): List<AudioOption> {
    val raw = mutableListOf<AudioOption>()
    for (g in tracks.groups) {
        if (g.type != C.TRACK_TYPE_AUDIO) continue
        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i)) continue
            val f = g.getTrackFormat(i)
            val name = f.label
                ?: f.language
                    ?.takeIf { it.isNotBlank() && it != "und" }
                    ?.let { Locale(it).displayLanguage.replaceFirstChar { ch -> ch.uppercase() } }
                ?: ""
            raw.add(AudioOption(g, i, name, g.isTrackSelected(i)))
        }
    }
    // Readable, unique labels
    return raw.mapIndexed { n, o ->
        val same = raw.count { it.label == o.label }
        val label = when {
            o.label.isBlank() -> "Audio ${n + 1}"
            same > 1 -> "${o.label} ${raw.take(n + 1).count { it.label == o.label }}"
            else -> o.label
        }
        o.copy(label = label)
    }
}

/** height <= 0 = automatic; otherwise lock playback to the best track of that height. */
private fun Player.selectVideoHeight(height: Int) {
    val builder = trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
    if (height > 0) {
        var bestGroup: Tracks.Group? = null
        var bestIndex = -1
        var bestBitrate = Int.MIN_VALUE
        for (g in currentTracks.groups) {
            if (g.type != C.TRACK_TYPE_VIDEO) continue
            for (i in 0 until g.length) {
                val f = g.getTrackFormat(i)
                if (g.isTrackSupported(i) && f.height == height && f.bitrate > bestBitrate) {
                    bestGroup = g
                    bestIndex = i
                    bestBitrate = f.bitrate
                }
            }
        }
        val chosen = bestGroup
        if (chosen != null) {
            builder.setOverrideForType(TrackSelectionOverride(chosen.mediaTrackGroup, bestIndex))
        }
    }
    trackSelectionParameters = builder.build()
}

private fun Player.selectAudio(option: AudioOption) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
        .build()
}

private fun Player.resetTrackChoices() {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        .build()
}

@Composable
private fun ControlIcon(icon: ImageVector, desc: String, tint: Color = Color.White, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun SeekButton(icon: ImageVector, seconds: String, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = Color.White, modifier = Modifier.size(48.dp))
        Text(seconds, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SkipButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = Color.White, modifier = Modifier.size(40.dp))
    }
}

@Composable
fun PlayerScreen(vm: MainViewModel, inPip: Boolean, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val activity = LocalContext.current as? Activity
    val view = LocalView.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val favs by vm.favorites.collectAsState()
    val ctrl = PlayerManager.player
    val c = PlayerManager.current
    val queueIndex = if (c != null) PlayerManager.queue.indexOfFirst { it.url == c.url } else -1
    val hasPrev = queueIndex > 0
    val hasNext = queueIndex >= 0 && queueIndex < PlayerManager.queue.size - 1

    var locked by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(true) }
    var tick by remember { mutableStateOf(0) }          // restarts the auto-hide timer
    var pos by remember { mutableStateOf(0L) }
    var dur by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var ccOn by remember { mutableStateOf(false) }
    var resize by remember { mutableStateOf(0) }
    var quality by remember { mutableStateOf(0) }
    var speed by remember { mutableStateOf(1f) }
    var showSettings by remember { mutableStateOf(false) }
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableStateOf(0f) }

    // Swipe up/down: left half = brightness, right half = volume
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volLevel by remember {
        mutableStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol)
    }
    var brightLevel by remember { mutableStateOf(currentBrightness(activity)) }
    var gesture by remember { mutableStateOf<String?>(null) }
    var gestureTick by remember { mutableStateOf(0) }
    LaunchedEffect(gestureTick) {
        if (gesture != null) {
            delay(900)
            gesture = null
        }
    }

    // Double-tap seek feedback: -1 = back, 1 = forward
    var seekFlash by remember { mutableStateOf(0) }
    var seekFlashTick by remember { mutableStateOf(0) }
    LaunchedEffect(seekFlashTick) {
        if (seekFlash != 0) {
            delay(600)
            seekFlash = 0
        }
    }

    // Immersive while watching; restore bars and orientation on exit
    DisposableEffect(Unit) {
        val insets = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            insets?.show(WindowInsetsCompat.Type.systemBars())
            activity?.window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = lp
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Each channel starts with captions off; undo our tweaks when leaving
    LaunchedEffect(ctrl) { ctrl?.setCaptions(false) }

    // Video qualities / audio tracks that the current channel really offers
    var tracks by remember { mutableStateOf(ctrl.currentTracks) }
    DisposableEffect(ctrl) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }
        }
        ctrl.addListener(listener)
        onDispose { ctrl.removeListener(listener) }
    }
    val heights = remember(tracks) { videoHeights(tracks) }
    val audioOpts = remember(tracks) { audioOptions(tracks) }

    // Every channel starts on automatic quality and its default audio
    LaunchedEffect(c?.url) {
        quality = 0
        ctrl.resetTrackChoices()
    }
    DisposableEffect(ctrl) {
        onDispose {
            ctrl?.run {
                volume = 1f
                setPlaybackSpeed(1f)
                resetTrackChoices()
                setCaptions(true)
            }
        }
    }

    // Poll the player for the seek bar / play state
    LaunchedEffect(ctrl) {
        while (true) {
            ctrl?.let {
                pos = it.currentPosition.coerceAtLeast(0L)
                dur = it.duration
                playing = it.playWhenReady
                buffering = it.playbackState == Player.STATE_BUFFERING
            }
            delay(500)
        }
    }

    // Auto-hide the controls while playing
    LaunchedEffect(visible, tick, playing, showSettings) {
        if (visible && playing && !showSettings) {
            delay(4000)
            visible = false
        }
    }

    val toggleOrientation: () -> Unit = {
        activity?.let { a ->
            val land = a.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            a.requestedOrientation =
                if (land) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(
            exo = ctrl,
            resizeMode = RESIZE_MODES[resize].first,
            modifier = Modifier.fillMaxSize()
        )

        PlayerManager.playerError?.let {
            Text(
                it,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }

        if (!inPip) {
            // Tap shows/hides the controls; swipe up/down changes brightness (left) or volume (right)
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                visible = !visible
                                tick++
                            },
                            onDoubleTap = { offset ->
                                if (!locked) {
                                    val back = offset.x < size.width / 2f
                                    val target = ctrl.currentPosition + if (back) -10_000L else 10_000L
                                    ctrl.seekTo(target.coerceAtLeast(0L))
                                    seekFlash = if (back) -1 else 1
                                    seekFlashTick++
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        var onLeft = false
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                onLeft = offset.x < size.width / 2f
                                volLevel = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
                            },
                            onVerticalDrag = { change, dy ->
                                if (!locked) {
                                    change.consume()
                                    val delta = -dy / (size.height * 0.6f)
                                    if (onLeft) {
                                        brightLevel = (brightLevel + delta).coerceIn(0.02f, 1f)
                                        activity?.window?.let { w ->
                                            val lp = w.attributes
                                            lp.screenBrightness = brightLevel
                                            w.attributes = lp
                                        }
                                        gesture = "brightness"
                                    } else {
                                        volLevel = (volLevel + delta).coerceIn(0f, 1f)
                                        audio.setStreamVolume(
                                            AudioManager.STREAM_MUSIC,
                                            (volLevel * maxVol).roundToInt(),
                                            0
                                        )
                                        gesture = "volume"
                                    }
                                    gestureTick++
                                }
                            }
                        )
                    }
            )

            gesture?.let { g ->
                val level = if (g == "volume") volLevel else brightLevel
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (g == "volume") {
                            if (level <= 0f) PlayerIcons.VolumeOff else PlayerIcons.VolumeUp
                        } else {
                            PlayerIcons.Brightness
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    LinearProgressIndicator(
                        progress = { level },
                        modifier = Modifier.width(110.dp),
                        color = PlayerAccent,
                        trackColor = Color(0x44FFFFFF)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${(level * 100).roundToInt()}%",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (seekFlash != 0) {
                Box(
                    Modifier
                        .align(if (seekFlash < 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 56.dp)
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(Color(0x66000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (seekFlash < 0) PlayerIcons.Replay else PlayerIcons.Forward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                    Text("10", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            val controlsShown = visible && !locked && c != null
            if (buffering && !controlsShown) {
                CircularProgressIndicator(
                    Modifier.align(Alignment.Center).size(48.dp),
                    color = PlayerAccent
                )
            }

            if (visible && c != null) {
                if (locked) {
                    IconButton(
                        onClick = {
                            locked = false
                            tick++
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    ) { Icon(Icons.Filled.Lock, contentDescription = "Unlock", tint = PlayerAccent) }
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0x55000000)))

                    // Top bar: close, title, favorite, lock
                    Row(
                        Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onClose() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(
                                c!!.name,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(3.dp))
                            LiveBadge()
                        }
                        val isFav = favs.any { it.url == c.url }
                        IconButton(onClick = { vm.toggleFavorite(c) }) {
                            Icon(
                                if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFav) HeartPink else Color.White
                            )
                        }
                        IconButton(onClick = {
                            locked = true
                            tick++
                        }) {
                            Icon(Icons.Filled.Lock, contentDescription = "Lock controls", tint = Color.White)
                        }
                    }

                    // Center: back 5s, play/pause, forward 15s
                    Row(
                        Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(40.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasPrev) {
                            SkipButton(PlayerIcons.SkipPrevious, "Previous channel") {
                                PlayerManager.playPrevious()
                                tick++
                            }
                        } else {
                            Spacer(Modifier.size(56.dp))
                        }
                        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                            if (buffering) {
                                CircularProgressIndicator(Modifier.size(48.dp), color = PlayerAccent)
                            } else {
                                IconButton(
                                    onClick = {
                                        ctrl?.let { if (it.playWhenReady) it.pause() else it.play() }
                                        tick++
                                    },
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Icon(
                                        if (playing) PlayerIcons.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (playing) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            }
                        }
                        if (hasNext) {
                            SkipButton(PlayerIcons.SkipNext, "Next channel") {
                                PlayerManager.playNext()
                                tick++
                            }
                        } else {
                            Spacer(Modifier.size(56.dp))
                        }
                    }

                    // Bottom: seek bar and tool row
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        val shown = if (seeking) seekValue else pos.toFloat()
                        if (dur > 0) {
                            Slider(
                                value = shown.coerceIn(0f, dur.toFloat()),
                                onValueChange = {
                                    seeking = true
                                    seekValue = it
                                    tick++
                                },
                                onValueChangeFinished = {
                                    ctrl?.seekTo(seekValue.toLong())
                                    seeking = false
                                    tick++
                                },
                                valueRange = 0f..dur.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color(0x66FFFFFF)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (dur > 0) {
                                Text(
                                    "-${formatTime(dur - shown.toLong())}  ·  ${formatTime(dur)}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                LiveBadge()
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                RESIZE_MODES[resize].second,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        resize = (resize + 1) % RESIZE_MODES.size
                                        tick++
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                            Box(
                                Modifier
                                    .padding(horizontal = 4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(
                                        1.5.dp,
                                        if (ccOn) PlayerAccent else Color.White,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        ccOn = !ccOn
                                        ctrl?.setCaptions(ccOn)
                                        tick++
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "CC",
                                    color = if (ccOn) PlayerAccent else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            ControlIcon(Icons.Filled.Settings, "Playback settings") {
                                showSettings = true
                            }
                            ControlIcon(
                                if (muted) PlayerIcons.VolumeOff else PlayerIcons.VolumeUp,
                                if (muted) "Unmute" else "Mute"
                            ) {
                                muted = !muted
                                ctrl?.volume = if (muted) 0f else 1f
                                tick++
                            }
                            ControlIcon(
                                if (landscape) PlayerIcons.FullscreenExit else PlayerIcons.Fullscreen,
                                if (landscape) "Exit fullscreen" else "Fullscreen"
                            ) {
                                toggleOrientation()
                                tick++
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = {
                showSettings = false
                tick++
            },
            containerColor = cs.surface,
            title = { Text("Playback settings") },
            text = {
                Column {
                    if (heights.size > 1) {
                        Text("Quality", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf(0) + heights) { h ->
                                Pill(if (h == 0) "Auto" else "${h}p", quality == h) {
                                    quality = h
                                    ctrl.selectVideoHeight(h)
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    if (audioOpts.size > 1) {
                        Text("Audio", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(audioOpts) { o ->
                                Pill(o.label, o.selected) { ctrl.selectAudio(o) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    Text("Speed", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(0.5f, 1f, 1.25f, 1.5f, 2f)) { s ->
                            val label = if (s % 1f == 0f) "${s.toInt()}x" else "${s}x"
                            Pill(label, speed == s) {
                                speed = s
                                ctrl?.setPlaybackSpeed(s)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSettings = false
                    tick++
                }) { Text("Done") }
            }
        )
    }
}

@Composable
fun VideoSurface(exo: Player?, resizeMode: Int, modifier: Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exo
                useController = false
                keepScreenOn = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            }
        },
        update = {
            it.player = exo
            it.resizeMode = resizeMode
        },
        onRelease = { it.player = null },
        modifier = modifier
    )
}

@Composable
fun LiveBadge() {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(TallyRed.copy(alpha = 0.25f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(TallyRed))
        Spacer(Modifier.width(5.dp))
        Text("LIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TallyRed)
    }
}
