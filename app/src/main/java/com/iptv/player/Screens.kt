package com.iptv.player

import android.content.res.Configuration
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.iptv.player.data.Channel
import com.iptv.player.data.PlaylistEntity

// ───────────────────────── Root ─────────────────────────

@Composable
fun AppRoot(vm: MainViewModel, inPip: Boolean) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val live = vm.screen == 1

    // Back from the live page returns to the playlist page
    BackHandler(enabled = live) { vm.closeLive() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            live && vm.current != null && (landscape || inPip) ->
                PlayerBox(vm.controller, Modifier.fillMaxSize().background(Color.Black))
            live -> LiveScreen(vm)
            else -> PlaylistScreen(vm)
        }
    }
}

@Composable
fun PlayerBox(exo: Player?, modifier: Modifier) {
    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { player = exo; keepScreenOn = true } },
        update = { it.player = exo },
        onRelease = { it.player = null },
        modifier = modifier
    )
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

// ───────────────────────── Live page ─────────────────────────

@Composable
fun LiveScreen(vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    val favs by vm.favorites.collectAsState()
    val favUrls = remember(favs) { favs.map { it.url }.toSet() }
    val groups = remember(vm.channels) { vm.channels.map { it.group }.distinct().sorted() }
    val source = if (vm.showFavorites) favs else vm.channels
    val list = remember(source, vm.query, vm.selectedGroup, vm.showFavorites) {
        source.filter { c ->
            (vm.showFavorites || vm.selectedGroup == null || c.group == vm.selectedGroup) &&
                c.name.contains(vm.query, ignoreCase = true)
        }
    }
    val current = vm.current

    Column(Modifier.fillMaxSize()) {
        // Player
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            PlayerBox(vm.controller, Modifier.fillMaxSize())
            if (current == null) {
                Text(
                    "চ্যানেল বেছে নিন",
                    color = Color(0x88FFFFFF),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Info strip: channel count, or the playing channel
        Row(
            Modifier
                .fillMaxWidth()
                .background(cs.surface)
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (current == null) {
                Text(
                    "${vm.channels.size} চ্যানেল",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Column(Modifier.weight(1f)) {
                    Text(
                        current.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LiveBadge()
                        if (current.group.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                current.group,
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    vm.playerError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = cs.error)
                    }
                }
                val isFav = current.url in favUrls
                IconButton(onClick = { vm.toggleFavorite(current) }) {
                    Icon(
                        if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFav) HeartPink else cs.onSurfaceVariant
                    )
                }
            }
        }

        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = cs.primary)

        // Search + refresh
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                placeholder = { Text("চ্যানেল খুঁজুন...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { vm.refresh() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = cs.primary)
            }
        }

        // Category chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Pill("All", !vm.showFavorites && vm.selectedGroup == null) {
                    vm.showFavorites = false
                    vm.selectedGroup = null
                }
            }
            item {
                Pill("পছন্দের (${favs.size})", vm.showFavorites) {
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

        when {
            vm.error != null && vm.channels.isEmpty() -> EmptyState(
                title = "প্লেলিস্ট লোড হয়নি",
                body = vm.error.orEmpty()
            )
            vm.channels.isEmpty() && !vm.loading -> EmptyState(
                title = "কোনো চ্যানেল পাওয়া যায়নি",
                body = "রিফ্রেশ করে আবার চেষ্টা করুন।"
            )
            vm.showFavorites && favs.isEmpty() -> EmptyState(
                title = "কোনো পছন্দের চ্যানেল নেই",
                body = "চ্যানেলের হার্ট আইকনে চাপ দিন।"
            )
            list.isEmpty() && !vm.loading -> EmptyState(
                title = "কিছু পাওয়া যায়নি",
                body = "অন্য নাম বা গ্রুপ দিয়ে চেষ্টা করুন।"
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(list) { c ->
                    ChannelCard(
                        c = c,
                        selected = current?.url == c.url,
                        isFav = c.url in favUrls,
                        onClick = { vm.play(c) },
                        onFav = { vm.toggleFavorite(c) }
                    )
                }
            }
        }
    }
}

@Composable
fun LiveBadge() {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(TallyRed.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(TallyRed))
        Spacer(Modifier.width(5.dp))
        Text("LIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TallyRed)
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

@Composable
fun ChannelCard(
    c: Channel,
    selected: Boolean,
    isFav: Boolean,
    onClick: () -> Unit,
    onFav: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .clip(shape)
            .background(cs.surfaceVariant)
            .border(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.outline, shape)
            .clickable(onClick = onClick)
    ) {
        Box {
            ChannelLogo(c, Modifier.fillMaxWidth().aspectRatio(1.3f).background(LogoTile))
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .clickable(onClick = onFav),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFav) HeartPink else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            c.name,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) cs.primary else cs.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)
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
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (!c.logo.isNullOrBlank()) {
            AsyncImage(
                model = c.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onState = { loaded = it is AsyncImagePainter.State.Success },
                modifier = Modifier.fillMaxSize().padding(14.dp)
            )
        }
    }
}
