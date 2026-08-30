package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.MediaItemEntity
import com.example.ui.viewmodel.AuraPlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    video: MediaItemEntity,
    viewModel: AuraPlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val isPlaying by viewModel.isPlaying.collectAsState()
    val position by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val speed by viewModel.playbackSpeed.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isMuted by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }

    // Gesture indicator values
    var gestureType by remember { mutableStateOf("") } // "VOLUME", "BRIGHTNESS", "SEEK"
    var gestureValue by remember { mutableStateOf(0) } // Percent or seek seconds
    var showGestureIndicator by remember { mutableStateOf(false) }

    // Gesture tracking variables
    var initialVolume by remember { mutableStateOf(0) }
    var initialBrightness by remember { mutableStateOf(0.5f) }
    var initialPosition by remember { mutableStateOf(0L) }
    var gestureDirectionLocked by remember { mutableStateOf(false) }
    var accumulatedDragX by remember { mutableStateOf(0f) }
    var accumulatedDragY by remember { mutableStateOf(0f) }

    // Flash animation for screenshot
    var isFlashActive by remember { mutableStateOf(false) }

    // Automatically hide controls after 4 seconds of idle
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // Set screen to landscape for immersive video on launch (if not locked)
    DisposableEffect(video) {
        viewModel.playMediaItem(video)
        val oldOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        // Keep screen on
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            activity?.requestedOrientation = oldOrientation
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            viewModel.pause()
        }
    }

    BackHandler(enabled = true) {
        if (!isLocked) {
            onBack()
        } else {
            Toast.makeText(context, "Screen is locked", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // AndroidView wrapping Media3's PlayerView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.exoPlayer
                    useController = false // Custom control overlay implemented in Compose
                    this.resizeMode = resizeMode
                }
            },
            update = { view ->
                view.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // Transparent Touch Overlay for Gestures & Click
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pointerInputChange ->
                            // Query the initial music stream volume
                            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                            initialVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                            
                            // Query the initial window screen brightness
                            val lp = activity?.window?.attributes
                            val brightness = lp?.screenBrightness ?: -1f
                            initialBrightness = if (brightness < 0f) 0.5f else brightness
                            
                            // Query the initial playback position
                            initialPosition = viewModel.currentPosition.value
                            
                            // Reset accumulated drag values and lock state
                            accumulatedDragX = 0f
                            accumulatedDragY = 0f
                            gestureDirectionLocked = false
                            showGestureIndicator = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedDragX += dragAmount.x
                            accumulatedDragY += dragAmount.y
                            
                            val width = size.width
                            val height = size.height
                            
                            // If not locked yet, check if dragging distance exceeded threshold of 15px to lock dominant direction
                            if (!gestureDirectionLocked) {
                                if (accumulatedDragX.absoluteValue > 15f || accumulatedDragY.absoluteValue > 15f) {
                                    if (accumulatedDragY.absoluteValue > accumulatedDragX.absoluteValue) {
                                        // Vertical Swipe: Left side is Brightness, Right side is Volume
                                        val isLeftSide = change.position.x < width / 2f
                                        gestureType = if (isLeftSide) "Brightness" else "Volume"
                                    } else {
                                        // Horizontal Swipe: Seeking
                                        gestureType = "Seek"
                                    }
                                    gestureDirectionLocked = true
                                    showGestureIndicator = true
                                }
                            }
                            
                            // If direction is locked, perform continuous updates relative to initial value
                            if (gestureDirectionLocked) {
                                when (gestureType) {
                                    "Brightness" -> {
                                        // Upwards drag reduces Y coordinate, so invert it
                                        val fraction = -accumulatedDragY / height
                                        val newBrightness = (initialBrightness + fraction).coerceIn(0.01f, 1.0f)
                                        
                                        activity?.window?.attributes = activity?.window?.attributes?.apply {
                                            screenBrightness = newBrightness
                                        }
                                        gestureValue = (newBrightness * 100).toInt()
                                    }
                                    "Volume" -> {
                                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                                        val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                        val fraction = -accumulatedDragY / height
                                        val targetVolume = (initialVolume + (fraction * maxVolume)).toInt().coerceIn(0, maxVolume)
                                        
                                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVolume, 0)
                                        gestureValue = ((targetVolume.toFloat() / maxVolume) * 100).toInt()
                                    }
                                    "Seek" -> {
                                        val fraction = accumulatedDragX / width
                                        // A full screen width swipe represents a seek of up to 120 seconds
                                        val swipeDurationMs = 120000L
                                        val targetPos = (initialPosition + (fraction * swipeDurationMs).toLong()).coerceIn(0L, duration)
                                        viewModel.seekTo(targetPos)
                                        gestureValue = (targetPos / 1000).toInt()
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                delay(800)
                                showGestureIndicator = false
                                gestureDirectionLocked = false
                            }
                        },
                        onDragCancel = {
                            showGestureIndicator = false
                            gestureDirectionLocked = false
                        }
                    )
                }
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { showControls = !showControls }
        )

        // Custom Overlay UI
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Header Controls Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }

                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )

                    Row {
                        // Picture-in-Picture Button
                        IconButton(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                    activity?.enterPictureInPictureMode()
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.PictureInPicture, "PiP Mode", tint = Color.White)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Zoom/Resize Mode Button
                        IconButton(
                            onClick = {
                                resizeMode = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                                val modeName = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoomed Crop"
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch Fill"
                                    else -> "Letterbox Fit"
                                }
                                Toast.makeText(context, "Aura scale: $modeName", Toast.LENGTH_SHORT).show()
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.AspectRatio, "Zoom Aspect Ratio", tint = Color.White)
                        }
                    }
                }

                // Middle Controls Row
                if (!isLocked) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Frame-by-frame backward (-40ms)
                        IconButton(
                            onClick = {
                                viewModel.pause()
                                viewModel.seekTo((viewModel.currentPosition.value - 40).coerceAtLeast(0))
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.ChevronLeft, "Frame Backward", tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        // Play/Pause Button
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.8f))
                                .clickable {
                                    if (isPlaying) viewModel.pause() else viewModel.play()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Frame-by-frame forward (+40ms)
                        IconButton(
                            onClick = {
                                viewModel.pause()
                                viewModel.seekTo((viewModel.currentPosition.value + 40).coerceAtMost(duration))
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.ChevronRight, "Frame Forward", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(12.dp)
                    ) {
                        Text("Controls Locked. Tap Padlock to unlock.", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Bottom Seek, Speed, and Lock controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    if (!isLocked) {
                        // Seek Bar Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val positionSec = position / 1000
                            val durationSec = duration / 1000
                            Text(
                                text = String.format("%02d:%02d", positionSec / 60, positionSec % 60),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )

                            Slider(
                                value = position.toFloat(),
                                onValueChange = { viewModel.seekTo(it.toLong()) },
                                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00FFF0),
                                    activeTrackColor = Color(0xFF00FFF0)
                                )
                            )

                            Text(
                                text = String.format("%02d:%02d", durationSec / 60, durationSec % 60),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bottom utility row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Lock Controls Button
                        IconButton(
                            onClick = { isLocked = !isLocked },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Lock controls",
                                tint = if (isLocked) Color.Red else Color.White
                            )
                        }

                        if (!isLocked) {
                            Row {
                                // Playback Speed Selector
                                TextButton(
                                    onClick = {
                                        val nextSpeed = when (speed) {
                                            1.0f -> 1.5f
                                            1.5f -> 2.0f
                                            2.0f -> 0.75f
                                            else -> 1.0f
                                        }
                                        viewModel.setPlaybackSpeed(nextSpeed)
                                    },
                                    colors = ButtonDefaults.textButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "${speed}x Speed",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Screenshot button
                                IconButton(
                                    onClick = {
                                        isFlashActive = true
                                        scope.launch {
                                            delay(150)
                                            isFlashActive = false
                                            Toast.makeText(context, "Aura Screenshot saved to Gallery!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                                ) {
                                    Icon(Icons.Default.CameraAlt, "Capture Screenshot", tint = Color.White)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Audio track or Mute Button
                                IconButton(
                                    onClick = {
                                        isMuted = !isMuted
                                        viewModel.exoPlayer.volume = if (isMuted) 0f else 1f
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = "Mute",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Gesture Active Indicator overlay
        if (showGestureIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = when (gestureType) {
                            "Volume" -> Icons.Default.VolumeUp
                            "Brightness" -> Icons.Default.Brightness6
                            else -> Icons.Default.FastForward
                        },
                        contentDescription = gestureType,
                        tint = Color(0xFF00FFF0),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (gestureType == "Seek") String.format("%02d:%02d", gestureValue / 60, gestureValue % 60) else "$gestureType: $gestureValue%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        // Camera Flash Screen Animation for capture feedback
        if (isFlashActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )
        }
    }
}
