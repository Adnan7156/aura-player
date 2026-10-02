package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.MediaItemEntity
import com.example.ui.viewmodel.AuraPlayerViewModel
import kotlin.math.roundToInt

/**
 * Stateful Floating Mini-Player connected to AuraPlayerViewModel.
 */
@Composable
fun FloatingMiniPlayer(
    viewModel: AuraPlayerViewModel,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    val currentTrack by viewModel.currentMediaItem.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val position by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val currentVolume by viewModel.currentVolume.collectAsState()
    val maxVolume = viewModel.maxVolume

    if (currentTrack != null) {
        FloatingMiniPlayer(
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            position = position,
            duration = duration,
            onPlayPause = {
                if (isPlaying) viewModel.pause() else viewModel.play()
            },
            onSkipNext = { viewModel.skipToNext() },
            onSkipPrevious = { viewModel.skipToPrevious() },
            onSeekTo = { viewModel.seekTo(it) },
            onExpand = onExpand,
            onDismiss = onDismiss ?: { viewModel.pause() },
            currentVolume = currentVolume,
            maxVolume = maxVolume,
            onVolumeStepUp = { viewModel.increaseVolume() },
            onVolumeStepDown = { viewModel.decreaseVolume() },
            modifier = modifier
        )
    }
}

/**
 * Highly polished, floating mini-player component with glassmorphic dark styling,
 * interactive progress scrubber, playback controls, and volume adjustments.
 */
@Composable
fun FloatingMiniPlayer(
    currentTrack: MediaItemEntity?,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    currentVolume: Int = 0,
    maxVolume: Int = 15,
    onVolumeStepUp: (() -> Unit)? = null,
    onVolumeStepDown: (() -> Unit)? = null
) {
    if (currentTrack == null) return

    // Disc rotation animation when active
    val infiniteTransition = rememberInfiniteTransition(label = "mini_disc_spin")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mini_rotation"
    )
    val discRotation = if (isPlaying) rotationAngle else 0f

    // Horizontal swipe gesture detection for quick track skipping
    var dragOffsetX by remember { mutableFloatStateOf(0f) }

    // Quick volume overlay state
    var showQuickVolume by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                ambientColor = Color.Black
            )
            .offset { IntOffset(dragOffsetX.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // Dampen horizontal drag
                        dragOffsetX = (dragOffsetX + dragAmount.x * 0.6f).coerceIn(-180f, 180f)
                    },
                    onDragEnd = {
                        if (dragOffsetX > 90f) {
                            onSkipPrevious()
                        } else if (dragOffsetX < -90f) {
                            onSkipNext()
                        }
                        dragOffsetX = 0f
                    },
                    onDragCancel = {
                        dragOffsetX = 0f
                    }
                )
            }
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onExpand)
            .testTag("floating_mini_player"),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF18171F), // Dark sleek surface
        tonalElevation = 6.dp,
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )
            )
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Interactive Scrubber Top Progress Line
            val progressFraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.1f))
                    .pointerInput(duration) {
                        detectTapGestures { offset ->
                            if (duration > 0) {
                                val tapFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                onSeekTo((tapFraction * duration).toLong())
                            }
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                )
            }

            // Quick Volume Pop-down Strip (When toggled)
            AnimatedVisibility(visible = showQuickVolume) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                            contentDescription = "Volume",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Volume: $currentVolume / $maxVolume",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onVolumeStepDown != null) {
                            IconButton(
                                onClick = onVolumeStepDown,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Volume Down",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (onVolumeStepUp != null) {
                            IconButton(
                                onClick = onVolumeStepUp,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Volume Up",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { showQuickVolume = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Volume Bar",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Main Mini-Player Content Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art / Spinning Disc
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .rotate(discRotation),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentTrack.coverUri != null) {
                        AsyncImage(
                            model = currentTrack.coverUri,
                            contentDescription = "Cover thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.img_aura_logo_1790965105681),
                            contentDescription = "Aura Player Logo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Track Title and Artist & Time Info
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                ) {
                    Text(
                        text = currentTrack.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = currentTrack.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // Duration timer pill
                        val curSec = position / 1000
                        val durSec = duration / 1000
                        Text(
                            text = String.format("%d:%02d / %d:%02d", curSec / 60, curSec % 60, durSec / 60, durSec % 60),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Audio Controls Cluster
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // Quick Volume Toggle Button
                    IconButton(
                        onClick = { showQuickVolume = !showQuickVolume },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Quick Volume",
                            tint = if (showQuickVolume) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Skip Previous Track Button
                    IconButton(
                        onClick = onSkipPrevious,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous Track",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Central Play / Pause Button with primary glowing container
                    FilledIconButton(
                        onClick = onPlayPause,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .size(42.dp)
                            .padding(2.dp)
                            .testTag("floating_play_pause")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Skip Next Track Button
                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Track",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Dismiss / Close mini-player button
                    if (onDismiss != null) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss Mini Player",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
