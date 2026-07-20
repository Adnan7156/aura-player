package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["type"]),
        Index(value = ["isFavorite"]),
        Index(value = ["recentlyPlayed"]),
        Index(value = ["folderPath"])
    ]
)
data class MediaItemEntity(
    @PrimaryKey val id: String, // String representation of media ID or path
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: String,
    val path: String,
    val type: String, // "AUDIO" or "VIDEO"
    val coverUri: String?,
    val genre: String,
    val dateAdded: Long,
    val isFavorite: Boolean = false,
    val recentlyPlayed: Long = 0, // Last played timestamp
    val playCount: Int = 0,
    val lastPosition: Long = 0, // Resume seek position
    val folderPath: String,
    val lyrics: String? = null,
    val playSpeed: Float = 1.0f,
    val pitch: Float = 1.0f
)
