package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.MediaItemEntity
import com.example.data.model.PlaylistEntity
import com.example.ui.viewmodel.AuraPlayerViewModel
import androidx.activity.compose.BackHandler
import com.example.util.StoragePermissionBanner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class LibraryDetail {
    object None : LibraryDetail()
    data class Album(val name: String) : LibraryDetail()
    data class Artist(val name: String) : LibraryDetail()
    data class Folder(val path: String) : LibraryDetail()
    data class Playlist(val playlistId: Int, val playlistName: String) : LibraryDetail()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalPermissionsApi::class)
@Composable
fun LibraryScreen(
    viewModel: AuraPlayerViewModel,
    onPlayAudio: (MediaItemEntity, List<MediaItemEntity>) -> Unit,
    onPlayVideo: (MediaItemEntity) -> Unit,
    modifier: Modifier = Modifier,
    mediaPermissionState: MultiplePermissionsState? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mainTabSelected by remember { mutableStateOf(0) } // 0: Audio, 1: Video
    var subTabSelected by remember { mutableStateOf(0) } // 0: Songs, 1: Albums, 2: Artists, 3: Folders, 4: Playlists, 5: Favorites
    var activeDetail by remember { mutableStateOf<LibraryDetail>(LibraryDetail.None) }

    val allMediaList by viewModel.allMedia.collectAsState()
    val audioList by viewModel.audioMedia.collectAsState()
    val videoList by viewModel.videoMedia.collectAsState()
    val favoritesList by viewModel.favorites.collectAsState()
    val playlistsList by viewModel.playlists.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showPlaylistDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }
    var showAddToPlaylistSheet by remember { mutableStateOf<String?>(null) } // mediaId to add

    BackHandler(enabled = activeDetail != LibraryDetail.None) {
        activeDetail = LibraryDetail.None
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background, // Ensure solid black screen background
        topBar = {
            if (activeDetail == LibraryDetail.None) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background) // full bleed black background
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                // Search bar and scan action styled for Elegant Dark theme
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.search(it) },
                        placeholder = { Text("Instant search Aura...") },
                        leadingIcon = { Icon(Icons.Default.Search, "Search") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.search("") }) {
                                    Icon(Icons.Default.Close, "Clear search")
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface, // `#1C1B1F`
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface, // `#1C1B1F`
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp)),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = { viewModel.scanMedia() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface // `#1C1B1F` circular background
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync and Rescan",
                            tint = MaterialTheme.colorScheme.primary // lavender tint
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Main Tab Bar
                PrimaryTabRow(
                    selectedTabIndex = mainTabSelected,
                    containerColor = Color.Transparent,
                    indicator = { TabRowDefaults.PrimaryIndicator(modifier = Modifier.tabIndicatorOffset(mainTabSelected)) }
                ) {
                    Tab(
                        selected = mainTabSelected == 0,
                        onClick = { mainTabSelected = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MusicNote, "Audio")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Acoustic", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    Tab(
                        selected = mainTabSelected == 1,
                        onClick = { mainTabSelected = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Videocam, "Video")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cinematic", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
            }
        } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { activeDetail = LibraryDetail.None }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = when (val detail = activeDetail) {
                            is LibraryDetail.Album -> detail.name
                            is LibraryDetail.Artist -> detail.name
                            is LibraryDetail.Folder -> detail.path.split("/").lastOrNull() ?: "Folder"
                            is LibraryDetail.Playlist -> detail.playlistName
                            else -> "Details"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (mediaPermissionState != null && !mediaPermissionState.allPermissionsGranted) {
                StoragePermissionBanner(permissionState = mediaPermissionState)
            }

            if (activeDetail != LibraryDetail.None) {
                LibraryDetailScreen(
                    activeDetail = activeDetail,
                    audioList = audioList,
                    viewModel = viewModel,
                    onPlayAudio = onPlayAudio,
                    onAddPlaylist = { showAddToPlaylistSheet = it }
                )
            } else if (mainTabSelected == 0) {
                // Custom Elegant Dark horizontally-scrollable sub-category pills row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf("Tracks", "Albums", "Artists", "Folders", "Playlists", "Favorites")
                    tabs.forEachIndexed { index, title ->
                        val isSelected = subTabSelected == index
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) // `#381E72` or `#1C1B1F`
                                .clickable { subTabSelected = index }
                                .then(
                                    if (!isSelected) {
                                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)) // `#49454F` border outline
                                    } else Modifier
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant // `#D0BCFF` or `#CAC4D0`
                            )
                        }
                    }
                }

                // Sub-tab Contents
                Box(modifier = Modifier.fillMaxSize()) {
                    when (subTabSelected) {
                        0 -> MediaList(audioList, onPlay = { item -> onPlayAudio(item, audioList) }, onAddPlaylist = { showAddToPlaylistSheet = it }, onToggleFavorite = { viewModel.toggleFavorite(it) })
                        1 -> AlbumGrid(audioList, onPlayAlbum = { album ->
                            activeDetail = LibraryDetail.Album(album)
                        })
                        2 -> ArtistList(audioList, onPlayArtist = { artist ->
                            activeDetail = LibraryDetail.Artist(artist)
                        })
                        3 -> FolderList(audioList, onPlayFolder = { folder ->
                            activeDetail = LibraryDetail.Folder(folder)
                        })
                        4 -> PlaylistManagerView(
                            playlists = playlistsList,
                            onCreatePlaylist = { showPlaylistDialog = true },
                            onDeletePlaylist = { viewModel.deletePlaylist(it) },
                            onSelectPlaylist = { pl ->
                                activeDetail = LibraryDetail.Playlist(pl.id, pl.name)
                            },
                            onGenerateAutomated = { criteria, onFinished ->
                                viewModel.generateAutomatedPlaylists(criteria, onFinished)
                            }
                        )
                        5 -> MediaList(favoritesList, onPlay = { item -> onPlayAudio(item, favoritesList) }, onAddPlaylist = { showAddToPlaylistSheet = it }, onToggleFavorite = { viewModel.toggleFavorite(it) })
                    }
                }
            } else {
                // Cinematic Videos Screen
                VideoGrid(videoList, onPlayVideo = onPlayVideo)
            }
        }

        // --- Dialogs ---
        if (showPlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showPlaylistDialog = false },
                title = { Text("New Playlist") },
                text = {
                    TextField(
                        value = playlistNameInput,
                        onValueChange = { playlistNameInput = it },
                        placeholder = { Text("E.g., Chill Beats") }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (playlistNameInput.isNotBlank()) {
                                viewModel.createPlaylist(playlistNameInput.trim())
                                playlistNameInput = ""
                                showPlaylistDialog = false
                            }
                        }
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPlaylistDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showAddToPlaylistSheet != null) {
            ModalBottomSheet(onDismissRequest = { showAddToPlaylistSheet = null }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Add to Playlist",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (playlistsList.isEmpty()) {
                        Text(
                            text = "No playlists found. Create one first!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(playlistsList) { playlist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.addMediaToPlaylist(playlist.id, showAddToPlaylistSheet!!)
                                            showAddToPlaylistSheet = null
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PlaylistPlay, "Playlist")
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Audio List View ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaList(
    items: List<MediaItemEntity>,
    onPlay: (MediaItemEntity) -> Unit,
    onAddPlaylist: (String) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    if (items.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.MusicOff,
            title = "Acoustics stand silent",
            subtitle = "No songs found in this selection. Scan folders or seed demo tracks!"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
        ) {
            items(items, key = { it.id }) { track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onPlay(track) },
                            onLongClick = { onAddPlaylist(track.id) }
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Async Album cover art with fallback representation
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (track.coverUri != null) {
                            AsyncImage(
                                model = track.coverUri,
                                contentDescription = "Artwork",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "Song placeholder",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.artist + " • " + track.album,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row {
                        IconButton(onClick = { onToggleFavorite(track.id) }) {
                            Icon(
                                imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (track.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onAddPlaylist(track.id) }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More"
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Album Grid View ---
@Composable
fun AlbumGrid(items: List<MediaItemEntity>, onPlayAlbum: (String) -> Unit) {
    val albums = items.groupBy { it.album }
    if (albums.isEmpty()) {
        EmptyStateView(Icons.Default.Album, "No Albums found", "We found no indexed albums in storage.")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(albums.keys.toList()) { albumTitle ->
                val track = albums[albumTitle]?.firstOrNull()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayAlbum(albumTitle) }
                ) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (track?.coverUri != null) {
                            AsyncImage(
                                model = track.coverUri,
                                contentDescription = albumTitle,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Album, "Album Art", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = albumTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track?.artist ?: "Unknown Artist",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// --- Artist List View ---
@Composable
fun ArtistList(items: List<MediaItemEntity>, onPlayArtist: (String) -> Unit) {
    val artists = items.groupBy { it.artist }
    if (artists.isEmpty()) {
        EmptyStateView(Icons.Default.Person, "No Artists found", "We found no indexed artists in storage.")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(artists.keys.toList()) { artistName ->
                val size = artists[artistName]?.size ?: 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayArtist(artistName) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Person, "Artist", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(artistName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text("$size tracks in catalog", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// --- Folder List View ---
@Composable
fun FolderList(items: List<MediaItemEntity>, onPlayFolder: (String) -> Unit) {
    val folders = items.groupBy { it.folderPath }
    if (folders.isEmpty()) {
        EmptyStateView(Icons.Default.Folder, "No Folders found", "Scan media files to browse directory folders.")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(folders.keys.toList()) { path ->
                val count = folders[path]?.size ?: 0
                val displayName = path.split("/").lastOrNull() ?: "Storage"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayFolder(path) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FolderOpen, "Folder", tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text("$path • $count tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// --- Playlist Manager View ---
@Composable
fun PlaylistManagerView(
    playlists: List<PlaylistEntity>,
    onCreatePlaylist: () -> Unit,
    onDeletePlaylist: (Int) -> Unit,
    onSelectPlaylist: (PlaylistEntity) -> Unit,
    onGenerateAutomated: (String, () -> Unit) -> Unit
) {
    var showAutoGenerateDialog by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onCreatePlaylist,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Default.Add, "Add")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Manual")
            }

            Button(
                onClick = { showAutoGenerateDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.AutoAwesome, "Auto")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Smart Auto")
            }
        }

        if (playlists.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.PlaylistPlay,
                title = "No Playlists",
                subtitle = "Create manual playlists or use the Smart Auto button to intelligently group your library tracks!"
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(playlists) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPlaylist(playlist) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.QueueMusic, "Playlist", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(playlist.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { onDeletePlaylist(playlist.id) }) {
                            Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }

        if (showAutoGenerateDialog) {
            AlertDialog(
                onDismissRequest = { if (!isGenerating) showAutoGenerateDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, "Smart Auto", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Smart Playlist Generator")
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Aura's automated engine will scan your music metadata and intelligently group your tracks into smart decks.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isGenerating) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Analyzing track metadata & organizing...")
                            }
                        } else {
                            Button(
                                onClick = {
                                    isGenerating = true
                                    onGenerateAutomated("GENRE") {
                                        isGenerating = false
                                        showAutoGenerateDialog = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.Category, "Genre")
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
                                    Text("Group by Genre", fontWeight = FontWeight.Bold)
                                    Text("E.g., Synthwave Vibes, Lo-Fi Vibes", style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            Button(
                                onClick = {
                                    isGenerating = true
                                    onGenerateAutomated("YEAR") {
                                        isGenerating = false
                                        showAutoGenerateDialog = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.DateRange, "Year")
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
                                    Text("Group by Release / Added Year", fontWeight = FontWeight.Bold)
                                    Text("E.g., Hits from 2026, Hits from 2025", style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            Button(
                                onClick = {
                                    isGenerating = true
                                    onGenerateAutomated("FREQUENCY") {
                                        isGenerating = false
                                        showAutoGenerateDialog = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.TrendingUp, "Frequency")
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
                                    Text("Group by Play Frequency", fontWeight = FontWeight.Bold)
                                    Text("Creates Heavy Rotation & Rare Treasures", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (!isGenerating) {
                        TextButton(onClick = { showAutoGenerateDialog = false }) {
                            Text("Cancel")
                        }
                    }
                }
            )
        }
    }
}

// --- Cinematic Video Grid ---
@Composable
fun VideoGrid(items: List<MediaItemEntity>, onPlayVideo: (MediaItemEntity) -> Unit) {
    if (items.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.VideoLibrary,
            title = "Cinematic deck empty",
            subtitle = "No video files found. Scan devices or play demo cinematic sequences!"
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, top = 8.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items) { video ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayVideo(video) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (video.coverUri != null) {
                                AsyncImage(
                                    model = video.coverUri,
                                    contentDescription = "Video Thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayCircleOutline,
                                    contentDescription = "Video",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            // Duration Badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.7f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                val min = video.duration / 60000
                                val sec = (video.duration % 60000) / 1000
                                Text(
                                    text = String.format("%d:%02d", min, sec),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }

                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = video.genre,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Empty State Component ---
@Composable
fun EmptyStateView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun LibraryDetailScreen(
    activeDetail: LibraryDetail,
    audioList: List<MediaItemEntity>,
    viewModel: AuraPlayerViewModel,
    onPlayAudio: (MediaItemEntity, List<MediaItemEntity>) -> Unit,
    onAddPlaylist: (String) -> Unit
) {
    val filteredTracks = when (activeDetail) {
        is LibraryDetail.Album -> {
            remember(activeDetail.name, audioList) {
                audioList.filter { it.album == activeDetail.name }
            }
        }
        is LibraryDetail.Artist -> {
            remember(activeDetail.name, audioList) {
                audioList.filter { it.artist == activeDetail.name }
            }
        }
        is LibraryDetail.Folder -> {
            remember(activeDetail.path, audioList) {
                audioList.filter { it.folderPath == activeDetail.path }
            }
        }
        is LibraryDetail.Playlist -> {
            viewModel.getMediaForPlaylist(activeDetail.playlistId).collectAsState(initial = emptyList()).value
        }
        else -> emptyList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (filteredTracks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onPlayAudio(filteredTracks.first(), filteredTracks) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play All")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play All")
                }

                FilledTonalButton(
                    onClick = {
                        viewModel.toggleShuffle()
                        val shuffled = filteredTracks.shuffled()
                        if (shuffled.isNotEmpty()) {
                            onPlayAudio(shuffled.first(), shuffled)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle All")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Shuffle")
                }
            }
        }

        MediaList(
            items = filteredTracks,
            onPlay = { item -> onPlayAudio(item, filteredTracks) },
            onAddPlaylist = onAddPlaylist,
            onToggleFavorite = { viewModel.toggleFavorite(it) }
        )
    }
}
