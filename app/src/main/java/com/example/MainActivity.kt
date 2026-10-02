package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.MediaItemEntity
import com.example.ui.components.FloatingMiniPlayer
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingAudioScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AuraPlayerViewModel
import com.example.util.rememberMediaStoragePermissionState
import com.google.accompanist.permissions.ExperimentalPermissionsApi

sealed class Screen {
    object Library : Screen()
    data class VideoPlayer(val video: MediaItemEntity) : Screen()
}

@OptIn(ExperimentalPermissionsApi::class)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val viewModel: AuraPlayerViewModel = viewModel(
                    factory = AuraPlayerViewModel.Factory(context)
                )

                // Accompanist Permissions for local media storage access
                val mediaPermissionState = rememberMediaStoragePermissionState { permissionsResult ->
                    val isAnyGranted = permissionsResult.values.any { it }
                    if (isAnyGranted) {
                        Toast.makeText(context, "Storage permissions granted!", Toast.LENGTH_SHORT).show()
                        viewModel.scanMedia()
                    }
                }

                // Request permissions smoothly on launch if not granted
                LaunchedEffect(Unit) {
                    if (!mediaPermissionState.allPermissionsGranted) {
                        mediaPermissionState.launchMultiplePermissionRequest()
                    }
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
                                    mediaPermissionState = mediaPermissionState,
                                    onPlayAudio = { track, list ->
                                        viewModel.playQueue(list, list.indexOf(track).coerceAtLeast(0))
                                        showAudioPlayerSheet = true
                                    },
                                    onPlayVideo = { video ->
                                        currentScreen = Screen.VideoPlayer(video)
                                    },
                                    modifier = Modifier.padding(bottom = if (currentTrack != null && !showAudioPlayerSheet) 88.dp else 0.dp)
                                )
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

                        // Floating Mini-Player Component
                        // Floats above content when user navigates away from the main playback screen
                        AnimatedVisibility(
                            visible = currentTrack != null && !showAudioPlayerSheet && currentScreen is Screen.Library,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy)
                            ) + fadeIn(),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(220)
                            ) + fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 12.dp)
                        ) {
                            FloatingMiniPlayer(
                                viewModel = viewModel,
                                onExpand = { showAudioPlayerSheet = true },
                                onDismiss = { viewModel.pause() }
                            )
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
}
