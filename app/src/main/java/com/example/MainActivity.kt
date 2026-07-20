package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.model.MediaItemEntity
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingAudioScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AuraPlayerViewModel

sealed class Screen {
    object Library : Screen()
    data class VideoPlayer(val video: MediaItemEntity) : Screen()
}

class MainActivity : ComponentActivity() {

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.READ_MEDIA_AUDIO] == true ||
                permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        if (audioGranted) {
            Toast.makeText(this, "Aura: Storage permissions granted!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme(darkTheme = true) { // Force beautiful Premium Dark theme
                val context = LocalContext.current
                val viewModel: AuraPlayerViewModel = viewModel(
                    factory = AuraPlayerViewModel.Factory(context)
                )

                // Request Permissions on Launch
                LaunchedEffect(Unit) {
                    checkAndRequestPermissions()
                }

                var currentScreen by remember { mutableStateOf<Screen>(Screen.Library) }
                var showAudioPlayerSheet by remember { mutableStateOf(false) }

                val currentTrack by viewModel.currentMediaItem.collectAsState()
                val isPlaying by viewModel.isPlaying.collectAsState()
                val position by viewModel.currentPosition.collectAsState()
                val duration by viewModel.duration.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background // Solid black for elegant dark
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Screen Navigation Switch
                        when (val screen = currentScreen) {
                            is Screen.Library -> {
                                LibraryScreen(
                                    viewModel = viewModel,
                                    onPlayAudio = { track, list ->
                                        viewModel.playQueue(list, list.indexOf(track).coerceAtLeast(0))
                                        showAudioPlayerSheet = true
                                    },
                                    onPlayVideo = { video ->
                                        currentScreen = Screen.VideoPlayer(video)
                                    },
                                    modifier = Modifier.padding(bottom = if (currentTrack != null) 72.dp else 0.dp)
                                )

                                // Anchored Mini Player styled with Elegant Dark guidelines
                                if (currentTrack != null) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(horizontal = 12.dp)
                                            .padding(bottom = 24.dp) // Lift slightly from screen bottom edge
                                            .fillMaxWidth()
                                            .height(64.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surface) // `#1C1B1F`
                                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(16.dp)) // subtle border
                                            .clickable { showAudioPlayerSheet = true }
                                    ) {
                                        // Miniature Top Seek Line
                                        val progress = if (duration > 0) position.toFloat() / duration else 0f
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(progress)
                                                .height(2.dp)
                                                .background(
                                                    Brush.horizontalGradient(
                                                        colors = listOf(
                                                            MaterialTheme.colorScheme.primary, // `#D0BCFF`
                                                            MaterialTheme.colorScheme.primaryContainer // `#381E72`
                                                        )
                                                    )
                                                )
                                                .align(Alignment.TopStart)
                                        )

                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Miniature Art
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (currentTrack?.coverUri != null) {
                                                    AsyncImage(
                                                        model = currentTrack?.coverUri,
                                                        contentDescription = "Mini Art",
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = currentTrack?.title ?: "",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = currentTrack?.artist ?: "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            // Play/Pause Action with primary lavender tint
                                            IconButton(
                                                onClick = { if (isPlaying) viewModel.pause() else viewModel.play() }
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = "Play",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            // Skip Next
                                            IconButton(onClick = { viewModel.skipToNext() }) {
                                                Icon(
                                                    imageVector = Icons.Default.SkipNext,
                                                    contentDescription = "Skip",
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            is Screen.VideoPlayer -> {
                                VideoPlayerScreen(
                                    video = screen.video,
                                    viewModel = viewModel,
                                    onBack = {
                                        currentScreen = Screen.Library
                                    }
                                )
                            }
                        }

                        // Sliding Now Playing Overlay Sheet
                        AnimatedVisibility(
                            visible = showAudioPlayerSheet,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            NowPlayingAudioScreen(
                                viewModel = viewModel,
                                onCollapse = { showAudioPlayerSheet = false }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
