package com.iptv.player

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    val current = vm.current
    val favs by vm.favorites.collectAsState()
    val playlists by vm.playlists.collectAsState()

    if (current != null && (landscape || inPip)) {
        PlayerBox(vm.controller, Modifier.fillMaxSize().background(Color.Black))
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BottomBar(vm, favs.size) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (current != null) {
                NowPlaying(vm, current, isFav = favs.any { it.url == current.url })
            }
            when (vm.tab) {
                0 -> ChannelsTab(vm, favoritesOnly = false, favs = favs)
                1 -> ChannelsTab(vm, favoritesOnly = true, favs = favs)
                else -> PlaylistsTab(vm, playlists.orEmpty())
            }
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

@Composable
fun BottomBar(vm: MainViewModel, favCount: Int) {
    val cs = MaterialTheme.colorScheme
    NavigationBar(containerColor = cs.surface) {
        val items = listOf(
            Triple("Live", Icons.Filled.PlayArrow, 0),
            Triple("Favorites", Icons.Filled.Favorite, 1),
            Triple("Playlists", Icons.AutoMirrored.Filled.List, 2)
        )
        items.forEach { (label, icon, index) ->
            NavigationBarItem(
                selected = vm.tab == index,
                onClick = { vm.tab = index },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = cs.onPrimary,
                    selectedTextColor = cs.primary,
                    indicatorColor = cs.primary,
                    unselectedIconColor = cs.onSurfaceVariant,
                    unselectedTextColor = cs.onSurfaceVariant
                )
            )
        }
    }
}

// ───────────────────────── Now playing ─────────────────────────

@Composable
fun NowPlaying(vm: MainViewModel, c: Channel, isFav: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column {
        PlayerBox(
            vm.controller,
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .background(cs.surface)
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    c.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveBadge()
                    if (c.group.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            c.group,
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
            IconButton(onClick = { vm.toggleFavorite(c) }) {
                Icon(
                    if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFav) HeartPink else cs.onSurfaceVariant
                )
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

// ───────────────────────── Live / Favorites ─────────────────────────

@Composable
fun ChannelsTab(vm: MainViewModel, favoritesOnly: Boolean, favs: List<Channel>) {
    val cs = MaterialTheme.colorScheme
    var searching by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }

    val favUrls = remember(favs) { favs.map { it.url }.toSet() }
    val groups = remember(vm.channels) { vm.channels.map { it.group }.distinct().sorted() }
    val source = if (favoritesOnly) favs else vm.channels
    val list = remember(source, vm.query, vm.selectedGroup, favoritesOnly) {
        source.filter { c ->
            (favoritesOnly || vm.selectedGroup == null || c.group == vm.selectedGroup) &&
                c.name.contains(vm.query, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (favoritesOnly) "Favorites" else "Live TV",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${list.size} channels",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
            IconButton(onClick = {
                searching = !searching
                if (!searching) vm.query = ""
            }) {
                Icon(
                    if (searching) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = "Search"
                )
            }
        }

        if (searching) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                placeholder = { Text("Search channels") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focus)
            )
        }

        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = cs.primary)
        vm.error?.let {
            Text(
                it,
                color = cs.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (!favoritesOnly && groups.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Pill("All", vm.selectedGroup == null) { vm.selectedGroup = null } }
                items(groups) { g -> Pill(g, vm.selectedGroup == g) { vm.selectedGroup = g } }
            }
        }

        when {
            !favoritesOnly && vm.channels.isEmpty() && !vm.loading -> EmptyState(
                title = "No channels yet",
                body = "Add an M3U playlist to start watching.",
                action = "Add playlist",
                onAction = { vm.tab = 2 }
            )
            favoritesOnly && favs.isEmpty() -> EmptyState(
                title = "No favorites yet",
                body = "Tap the heart on any channel to keep it here.",
                action = null,
                onAction = {}
            )
            list.isEmpty() -> EmptyState("No matches", "Try a different name or group.", null) {}
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
                        selected = vm.current?.url == c.url,
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
fun EmptyState(title: String, body: String, action: String?, onAction: () -> Unit) {
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
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction, shape = RoundedCornerShape(14.dp)) { Text(action) }
        }
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

// ───────────────────────── Playlists ─────────────────────────

@Composable
fun PlaylistsTab(vm: MainViewModel, playlists: List<PlaylistEntity>) {
    val cs = MaterialTheme.colorScheme
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Playlists",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        if (vm.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = cs.primary)
            Spacer(Modifier.height(8.dp))
        }
        vm.error?.let {
            Text(it, color = cs.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }

        if (playlists.isEmpty()) {
            Text(
                "You haven't added a playlist yet.",
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        playlists.forEach { p ->
            PlaylistCard(
                p = p,
                active = p.id == vm.activePlaylistId,
                onClick = { vm.selectPlaylist(p) },
                onDelete = { vm.deletePlaylist(p) }
            )
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Add a playlist",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = vm.urlInput,
            onValueChange = { vm.urlInput = it },
            label = { Text("M3U link") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    vm.addPlaylist(vm.urlInput, false, "")
                    vm.urlInput = ""
                },
                shape = RoundedCornerShape(14.dp)
            ) { Text("Add link") }
            OutlinedButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                shape = RoundedCornerShape(14.dp)
            ) { Text("Choose file") }
        }
    }
}

@Composable
fun PlaylistCard(p: PlaylistEntity, active: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surfaceVariant)
            .border(if (active) 2.dp else 1.dp, if (active) cs.primary else cs.outline, shape)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(LogoTile),
            contentAlignment = Alignment.Center
        ) {
            Text(
                p.name.trim().take(1).uppercase().ifEmpty { "?" },
                fontWeight = FontWeight.Bold,
                color = cs.primary
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                p.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (p.isFile) "File on this phone" else "Online link",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
        if (active) {
            Text(
                "Playing from",
                style = MaterialTheme.typography.labelSmall,
                color = cs.primary
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete playlist", tint = cs.onSurfaceVariant)
        }
    }
}
