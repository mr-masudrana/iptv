package com.iptv.player

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.iptv.player.data.Channel
import com.iptv.player.data.PlaylistEntity

@Composable
fun AppRoot(vm: MainViewModel, inPip: Boolean) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val current = vm.current

    if (current != null && (landscape || inPip)) {
        PlayerBox(vm.controller, Modifier.fillMaxSize())
    } else {
        Column(Modifier.fillMaxSize()) {
            if (current != null) {
                PlayerBox(vm.controller, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                Text(
                    current.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(12.dp)
                )
                vm.playerError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
            ChannelScreen(vm)
        }
    }
}

@Composable
fun PlayerBox(exo: Player?, modifier: Modifier) {
    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { player = exo } },
        update = { it.player = exo },
        onRelease = { it.player = null },
        modifier = modifier
    )
}

@Composable
fun ChannelScreen(vm: MainViewModel) {
    val favs by vm.favorites.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val favUrls = remember(favs) { favs.map { it.url }.toSet() }

    var showPanel by remember { mutableStateOf(false) }
    val panelOpen = showPanel || playlists?.isEmpty() == true

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.importFile(uri)
            showPanel = false
        }
    }

    val groups = remember(vm.channels) { vm.channels.map { it.group }.distinct().sorted() }
    val source = if (vm.showFavorites) favs else vm.channels
    val list = remember(source, vm.query, vm.selectedGroup, vm.showFavorites) {
        source.filter { c ->
            (vm.showFavorites || vm.selectedGroup == null || c.group == vm.selectedGroup) &&
                c.name.contains(vm.query, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        if (panelOpen) {
            PlaylistPanel(
                vm = vm,
                playlists = playlists.orEmpty(),
                onPickFile = { picker.launch(arrayOf("*/*")) },
                onDone = { showPanel = false }
            )
        }
        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            placeholder = { Text("Search channels") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = !vm.showFavorites,
                onClick = { vm.showFavorites = false },
                label = { Text("All") }
            )
            FilterChip(
                selected = vm.showFavorites,
                onClick = { vm.showFavorites = true },
                label = { Text("Favorites (${favs.size})") }
            )
            TextButton(onClick = { showPanel = !showPanel }) { Text("Playlists") }
        }

        if (!vm.showFavorites && groups.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = vm.selectedGroup == null,
                        onClick = { vm.selectedGroup = null },
                        label = { Text("All groups") }
                    )
                }
                items(groups) { g ->
                    FilterChip(
                        selected = vm.selectedGroup == g,
                        onClick = { vm.selectedGroup = g },
                        label = { Text(g) }
                    )
                }
            }
        }

        if (list.isEmpty() && !vm.loading) {
            Text("No channels", modifier = Modifier.padding(16.dp))
        }

        LazyColumn(Modifier.weight(1f)) {
            items(list) { c ->
                ChannelRow(
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

@Composable
fun PlaylistPanel(
    vm: MainViewModel,
    playlists: List<PlaylistEntity>,
    onPickFile: () -> Unit,
    onDone: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
            playlists.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            vm.selectPlaylist(p)
                            onDone()
                        }
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        p.name,
                        modifier = Modifier.weight(1f),
                        color = if (p.id == vm.activePlaylistId) MaterialTheme.colorScheme.primary
                        else Color.Unspecified,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { vm.deletePlaylist(p) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete playlist")
                    }
                }
            }
        }
        OutlinedTextField(
            value = vm.urlInput,
            onValueChange = { vm.urlInput = it },
            label = { Text("M3U playlist URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Button(onClick = {
                vm.addPlaylist(vm.urlInput, false, "")
                vm.urlInput = ""
                onDone()
            }) { Text("Add URL") }
            OutlinedButton(onClick = onPickFile) { Text("Import file") }
        }
    }
}

@Composable
fun ChannelRow(
    c: Channel,
    selected: Boolean,
    isFav: Boolean,
    onClick: () -> Unit,
    onFav: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = c.logo, contentDescription = null, modifier = Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                c.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (c.group.isNotBlank()) {
                Text(
                    c.group,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onFav) {
            Icon(
                if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Favorite"
            )
        }
    }
}
