package com.example.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.*

/**
 * Utility object for determining required storage and media permissions
 * across different Android OS versions (Android 13+ Tiramisu vs Android 12 and lower).
 */
object StoragePermissionUtils {

    /**
     * Returns the appropriate storage/media permissions list based on Android API level.
     * Android 13+ (API 33+) uses granular READ_MEDIA_AUDIO & READ_MEDIA_VIDEO permissions.
     * Android 12 and below uses READ_EXTERNAL_STORAGE.
     */
    fun getRequiredStoragePermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * Returns permissions specific to audio playback.
     */
    fun getAudioStoragePermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * Returns permissions specific to video playback.
     */
    fun getVideoStoragePermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * Checks synchronously if storage access permissions are granted.
     */
    fun hasStoragePermission(context: Context): Boolean {
        val permissions = getRequiredStoragePermissions()
        return permissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks synchronously if audio read permission is granted.
     */
    fun hasAudioPermission(context: Context): Boolean {
        val permissions = getAudioStoragePermissions()
        return permissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks synchronously if video read permission is granted.
     */
    fun hasVideoPermission(context: Context): Boolean {
        val permissions = getVideoStoragePermissions()
        return permissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Launches the system App Settings screen for the application.
     * Used when the user has permanently denied permissions (Don't ask again).
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

/**
 * Remember and observe the state of required local media storage permissions using Accompanist.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberMediaStoragePermissionState(
    onPermissionsResult: ((Map<String, Boolean>) -> Unit)? = null
): MultiplePermissionsState {
    val permissions = remember { StoragePermissionUtils.getRequiredStoragePermissions() }
    return rememberMultiplePermissionsState(
        permissions = permissions,
        onPermissionsResult = { result ->
            onPermissionsResult?.invoke(result)
        }
    )
}

/**
 * Composable permission gate that checks storage permissions.
 * If granted: displays [content].
 * If denied or rationale needed: displays a Material 3 rationale interface.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MediaPermissionGate(
    permissionState: MultiplePermissionsState,
    onPermissionGranted: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(permissionState.allPermissionsGranted) {
        if (permissionState.allPermissionsGranted) {
            onPermissionGranted()
        }
    }

    if (permissionState.allPermissionsGranted) {
        content()
    } else {
        MediaPermissionRationaleView(
            permissionState = permissionState,
            onOpenSettings = { StoragePermissionUtils.openAppSettings(context) },
            modifier = modifier
        )
    }
}

/**
 * Beautiful Material 3 rationale view for media storage permissions.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MediaPermissionRationaleView(
    permissionState: MultiplePermissionsState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPermanentlyDenied = !permissionState.shouldShowRationale && !permissionState.allPermissionsGranted

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon Header
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPermanentlyDenied) Icons.Default.Lock else Icons.Default.FolderSpecial,
                        contentDescription = "Storage Access",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isPermanentlyDenied) "Storage Access Blocked" else "Access Your Local Media",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isPermanentlyDenied) {
                        "Permission to access device media was previously denied. Please enable Storage/Media permissions in Settings to play your local music and videos."
                    } else {
                        "Aura Player needs permission to read audio and video files stored on your device so you can listen to songs, watch videos, and manage playlists."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isPermanentlyDenied) {
                    Button(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open App Settings", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = { permissionState.launchMultiplePermissionRequest() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Grant Storage Access", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Compact Material 3 banner that can be displayed inside screens (such as Library)
 * when permissions are not yet granted.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun StoragePermissionBanner(
    permissionState: MultiplePermissionsState,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isPermanentlyDenied = !permissionState.shouldShowRationale && !permissionState.allPermissionsGranted

    if (!permissionState.allPermissionsGranted) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Device Storage Access",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Grant permission to scan and play all local tracks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (isPermanentlyDenied) {
                            StoragePermissionUtils.openAppSettings(context)
                        } else {
                            permissionState.launchMultiplePermissionRequest()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = if (isPermanentlyDenied) "Settings" else "Allow",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Material 3 Dialog for requesting permissions with custom rationale.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun StoragePermissionDialog(
    permissionState: MultiplePermissionsState,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val isPermanentlyDenied = !permissionState.shouldShowRationale && !permissionState.allPermissionsGranted

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = if (isPermanentlyDenied) Icons.Default.Lock else Icons.Default.FolderSpecial,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = if (isPermanentlyDenied) "Storage Access Needed" else "Permission Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = if (isPermanentlyDenied) {
                    "Aura Player needs storage permission to locate and play your local music and video files. Please enable it in Settings."
                } else {
                    "Allow Aura Player to access music and videos on your device to build your media library."
                },
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isPermanentlyDenied) {
                        StoragePermissionUtils.openAppSettings(context)
                    } else {
                        permissionState.launchMultiplePermissionRequest()
                    }
                    onDismissRequest()
                }
            ) {
                Text(if (isPermanentlyDenied) "Open Settings" else "Grant Permission")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}
