package com.iptv.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.iptv.player.data.Channel
import com.iptv.player.data.PlaylistEntity

// ───────────────────────── Root: Playlist → Channels → Player ─────────────────────────

@Composable
fun AppRoot(vm: MainViewModel, inPip: Boolean) {
    BackHandler(enabled = vm.screen == 2) { vm.closePlayer() }
    BackHandler(enabled = vm.screen == 1) { vm.closeChannels() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (vm.screen) {
            2 -> PlayerScreen(vm, inPip)
            1 -> ChannelsScreen(vm)
            else -> PlaylistScreen(vm)
        }
    }
}

// ───────────────────────── Playlist page ─────────────────────────

@Composable
fun PlaylistScreen(vm: MainViewModel) {
    val playlists by vm.playlists.collectAsState()
    val list = playlists.orEmpty()
    var menu by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PlaylistEntity?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(uri)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Playlist",
                style = MaterialTheme.typography.headlineMedium,
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
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(list, key = { it.id }) { p ->
                    PlaylistCard(p, onClick = { vm.openPlaylist(p) }, onEdit = { editing = p })
                }
            }
        }
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

@Composable
fun PlaylistCard(p: PlaylistEntity, onClick: () -> Unit, onEdit: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
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
        // Top bar
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
                        "Channels",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${list.size} channels",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                IconButton(onClick = { vm.refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
                IconButton(onClick = { vm.showFavorites = !vm.showFavorites }) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Favorites",
                        tint = if (vm.showFavorites) cs.primary else cs.onSurfaceVariant
                    )
                }
                IconButton(onClick = { searching = true }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
            }
        }

        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = cs.primary)

        if (!vm.showFavorites && groups.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Pill("All", vm.selectedGroup == null) { vm.selectedGroup = null } }
                items(groups) { g -> Pill(g, vm.selectedGroup == g) { vm.selectedGroup = g } }
            }
        }

        when {
            vm.error != null && vm.channels.isEmpty() -> EmptyState(
                title = "Couldn't load playlist",
                body = vm.error.orEmpty()
            )
            vm.channels.isEmpty() && !vm.loading -> EmptyState(
                title = "No channels found",
                body = "Tap refresh to try again."
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
                        onClick = { vm.openChannel(c) },
                        onFav = { vm.toggleFavorite(c) }
                    )
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
            Text(
                c.name.trim().take(1).uppercase().ifEmpty { "?" },
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0A1020)
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
        Modifier.fillMaxSize().padding(32.dp),
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

@Composable
fun PlayerScreen(vm: MainViewModel, inPip: Boolean) {
    val cs = MaterialTheme.colorScheme
    val activity = LocalContext.current as? Activity
    val view = LocalView.current
    val favs by vm.favorites.collectAsState()
    val c = vm.current
    var locked by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    // Immersive while watching; restore bars and orientation on exit
    DisposableEffect(Unit) {
        val insets = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            insets?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(
            exo = vm.controller,
            locked = locked,
            inPip = inPip,
            onControls = { controlsVisible = it },
            onFullscreen = {
                activity?.let { a ->
                    val landscape =
                        a.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    a.requestedOrientation =
                        if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        vm.playerError?.let {
            Text(
                it,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }

        if (!inPip && c != null) {
            if (locked) {
                IconButton(
                    onClick = { locked = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) { Icon(Icons.Filled.Lock, contentDescription = "Unlock", tint = cs.primary) }
            } else if (controlsVisible) {
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { vm.closePlayer() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(
                            c.name,
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
                    IconButton(onClick = { locked = true }) {
                        Icon(Icons.Filled.Lock, contentDescription = "Lock controls", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun VideoSurface(
    exo: Player?,
    locked: Boolean,
    inPip: Boolean,
    onControls: (Boolean) -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exo
                keepScreenOn = true
                controllerShowTimeoutMs = 3500
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { v -> onControls(v == View.VISIBLE) }
                )
                setFullscreenButtonClickListener { _ -> onFullscreen() }
            }
        },
        update = {
            it.player = exo
            it.useController = !locked && !inPip
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
