package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.data.database.MediaDao
import com.example.data.model.MediaItemEntity
import com.example.data.model.PinnedFolderEntity
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class MediaRepository(private val mediaDao: MediaDao) {

    val allMedia: Flow<List<MediaItemEntity>> = mediaDao.getAllMedia()
    val favorites: Flow<List<MediaItemEntity>> = mediaDao.getFavorites()
    val recentlyPlayed: Flow<List<MediaItemEntity>> = mediaDao.getRecentlyPlayed()
    val mostPlayed: Flow<List<MediaItemEntity>> = mediaDao.getMostPlayed()
    val pinnedFolders: Flow<List<PinnedFolderEntity>> = mediaDao.getPinnedFolders()

    fun getMediaByType(type: String): Flow<List<MediaItemEntity>> = mediaDao.getMediaByType(type)
    fun searchMedia(query: String): Flow<List<MediaItemEntity>> = mediaDao.searchMedia(query)
    fun getMediaForPlaylist(playlistId: Int): Flow<List<MediaItemEntity>> = mediaDao.getMediaForPlaylist(playlistId)
    val playlists: Flow<List<PlaylistEntity>> = mediaDao.getAllPlaylists()

    suspend fun getMediaById(id: String): MediaItemEntity? = mediaDao.getMediaById(id)
    suspend fun getMediaByIds(ids: List<String>): List<MediaItemEntity> = mediaDao.getMediaByIds(ids)

    suspend fun setFavorite(id: String, isFavorite: Boolean) = mediaDao.setFavorite(id, isFavorite)
    suspend fun updateRecentlyPlayed(id: String) {
        mediaDao.updateRecentlyPlayed(id, System.currentTimeMillis())
    }
    suspend fun updateLastPosition(id: String, position: Long) {
        mediaDao.updateLastPosition(id, position)
    }

    // --- Playlist Management ---
    suspend fun createPlaylist(name: String): Long {
        return mediaDao.insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun deletePlaylist(playlistId: Int) = mediaDao.deletePlaylist(playlistId)

    suspend fun addMediaToPlaylist(playlistId: Int, mediaId: String) {
        val items = mediaDao.getPlaylistItems(playlistId)
        if (items.any { it.mediaId == mediaId }) return
        val order = if (items.isEmpty()) 0 else items.maxOf { it.displayOrder } + 1
        mediaDao.insertPlaylistItem(
            PlaylistItemEntity(
                playlistId = playlistId,
                mediaId = mediaId,
                displayOrder = order
            )
        )
    }

    suspend fun removeMediaFromPlaylist(playlistId: Int, mediaId: String) {
        mediaDao.deletePlaylistItem(playlistId, mediaId)
    }

    // --- Pinned Folders ---
    suspend fun pinFolder(path: String) {
        mediaDao.insertPinnedFolder(PinnedFolderEntity(path = path))
    }

    suspend fun unpinFolder(path: String) {
        mediaDao.deletePinnedFolder(path)
    }

    // --- Core Scanner with Smart Seed Fallback ---
    suspend fun scanDeviceStorage(context: Context) = withContext(Dispatchers.IO) {
        Log.d("MediaScanner", "Starting device media storage scan...")
        val scannedItems = mutableListOf<MediaItemEntity>()

        // 1. Scan Audio
        val audioProjection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val audioCursor: Cursor? = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            audioProjection,
            null,
            null,
            null
        )

        audioCursor?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Unknown Track"
                val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                val album = cursor.getString(albumCol) ?: "Unknown Album"
                val duration = cursor.getLong(durationCol)
                val path = cursor.getString(dataCol) ?: ""
                val date = cursor.getLong(dateCol) * 1000 // Convert to ms
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()

                val file = File(path)
                val folderPath = file.parent ?: "Internal Storage"
                val folderName = file.parentFile?.name ?: "Music"

                // Extract artwork URI
                val artworkUri = Uri.parse("content://media/external/audio/media/$id/albumart").toString()

                scannedItems.add(
                    MediaItemEntity(
                        id = "audio_$id",
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        uri = contentUri,
                        path = path,
                        type = "AUDIO",
                        coverUri = artworkUri,
                        genre = "General",
                        dateAdded = date,
                        folderPath = folderPath
                    )
                )
            }
        }

        // 2. Scan Video
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_ADDED
        )

        val videoCursor: Cursor? = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection,
            null,
            null,
            null
        )

        videoCursor?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Unknown Video"
                val duration = cursor.getLong(durationCol)
                val path = cursor.getString(dataCol) ?: ""
                val date = cursor.getLong(dateCol) * 1000
                val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id).toString()

                val file = File(path)
                val folderPath = file.parent ?: "Internal Storage"

                scannedItems.add(
                    MediaItemEntity(
                        id = "video_$id",
                        title = title,
                        artist = "Video Library",
                        album = "Camera",
                        duration = duration,
                        uri = contentUri,
                        path = path,
                        type = "VIDEO",
                        coverUri = null,
                        genre = "Video",
                        dateAdded = date,
                        folderPath = folderPath
                    )
                )
            }
        }

        if (scannedItems.isNotEmpty()) {
            mediaDao.insertMedia(scannedItems)
            Log.d("MediaScanner", "Successfully scanned and indexed ${scannedItems.size} local media items.")
        } else {
            // Smart Fallback Seed: If the MediaStore database is completely empty (like in standard Android emulators),
            // we automatically seed beautiful, high-quality public domain demo files so the app works out of the box!
            val existingInDb = mediaDao.getAllMedia().first()
            if (existingInDb.isEmpty()) {
                Log.d("MediaScanner", "MediaStore is empty (standard Emulator behaviour). Seeding high-quality sample files.")
                seedDemoFiles()
            }
        }
    }

    private suspend fun seedDemoFiles() {
        val demoItems = listOf(
            // Audio Items
            MediaItemEntity(
                id = "demo_audio_1",
                title = "Cosmic Resonance",
                artist = "Aura Lab",
                album = "Future Horizons",
                duration = 312000, // 5:12
                uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                path = "/storage/emulated/0/Music/Aura Lab/Cosmic Resonance.mp3",
                type = "AUDIO",
                coverUri = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=500&q=80",
                genre = "Synthwave",
                dateAdded = System.currentTimeMillis() - 1000000,
                folderPath = "/storage/emulated/0/Music/Aura Lab",
                lyrics = "[00:10.00]Welcome to Cosmic Resonance\n[00:15.00]Experience the ultimate rhythm\n[00:22.00]Let the bass waves wash over you\n[00:30.00]Breathe in, feel the neon horizon\n[00:45.00]Synthesizing frequency structures...\n[01:00.00]Pulse in harmony with the cosmos"
            ),
            MediaItemEntity(
                id = "demo_audio_2",
                title = "Midnight Breeze",
                artist = "Lofi Dreamer",
                album = "Chill Study Beats",
                duration = 423000, // 7:03
                uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                path = "/storage/emulated/0/Music/Lofi Dreamer/Midnight Breeze.mp3",
                type = "AUDIO",
                coverUri = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=500&q=80",
                genre = "Lo-Fi Hip Hop",
                dateAdded = System.currentTimeMillis() - 2000000,
                folderPath = "/storage/emulated/0/Music/Lofi Dreamer",
                lyrics = "[00:05.00]Relax and let your mind drift...\n[00:25.00]Midnight breeze through open windows\n[00:45.00]Unwinding under soft starlight\n[01:10.00]Flowing smoothly, beats guiding your study"
            ),
            MediaItemEntity(
                id = "demo_audio_3",
                title = "Vapor Trail",
                artist = "Retro Glide",
                album = "Neon Sunset",
                duration = 302000, // 5:02
                uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                path = "/storage/emulated/0/Music/Retro Glide/Vapor Trail.mp3",
                type = "AUDIO",
                coverUri = "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?w=500&q=80",
                genre = "Chillwave",
                dateAdded = System.currentTimeMillis() - 3000000,
                folderPath = "/storage/emulated/0/Music/Retro Glide",
                lyrics = "[00:12.00]Cruising down the empty highway\n[00:28.00]Catching vapor trails in the dusk\n[00:48.00]Retro vibe, infinite state of flow"
            ),
            MediaItemEntity(
                id = "demo_audio_4",
                title = "Mountain Whispers",
                artist = "Forest Wand",
                album = "Acoustic Journeys",
                duration = 302000, // 5:02
                uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
                path = "/storage/emulated/0/Music/Forest Wand/Mountain Whispers.mp3",
                type = "AUDIO",
                coverUri = "https://images.unsplash.com/photo-1448375240586-882707db888b?w=500&q=80",
                genre = "Acoustic Folk",
                dateAdded = System.currentTimeMillis() - 4000000,
                folderPath = "/storage/emulated/0/Music/Forest Wand",
                lyrics = "[00:08.00]Wandering through the whispering pines\n[00:20.00]Where the mountain wind sings of yesterday\n[00:38.00]Folk acoustic, returning home to nature"
            ),
            MediaItemEntity(
                id = "demo_audio_5",
                title = "Nebula Echoes",
                artist = "Stella Drift",
                album = "Deep Space",
                duration = 354000, // 5:54
                uri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
                path = "/storage/emulated/0/Music/Stella Drift/Nebula Echoes.mp3",
                type = "AUDIO",
                coverUri = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=500&q=80",
                genre = "Space Ambient",
                dateAdded = System.currentTimeMillis() - 5000000,
                folderPath = "/storage/emulated/0/Music/Stella Drift"
            ),

            // Video Items
            MediaItemEntity(
                id = "demo_video_1",
                title = "Big Buck Bunny",
                artist = "Blender Animation",
                album = "Open Media Project",
                duration = 596000, // 9:56
                uri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                path = "/storage/emulated/0/Video/Animation/Big Buck Bunny.mp4",
                type = "VIDEO",
                coverUri = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500&q=80",
                genre = "Animation",
                dateAdded = System.currentTimeMillis() - 6000000,
                folderPath = "/storage/emulated/0/Video/Animation"
            ),
            MediaItemEntity(
                id = "demo_video_2",
                title = "Sintel Cinematic",
                artist = "Durian Open Source",
                album = "Open Media Project",
                duration = 888000, // 14:48
                uri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                path = "/storage/emulated/0/Video/Animation/Sintel.mp4",
                type = "VIDEO",
                coverUri = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500&q=80",
                genre = "Animation",
                dateAdded = System.currentTimeMillis() - 7000000,
                folderPath = "/storage/emulated/0/Video/Animation"
            ),
            MediaItemEntity(
                id = "demo_video_3",
                title = "Tears of Steel Sci-Fi",
                artist = "Blender VFX",
                album = "Open Media Project",
                duration = 734000, // 12:14
                uri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                path = "/storage/emulated/0/Video/VFX/Tears of Steel.mp4",
                type = "VIDEO",
                coverUri = "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=500&q=80",
                genre = "Sci-Fi",
                dateAdded = System.currentTimeMillis() - 8000000,
                folderPath = "/storage/emulated/0/Video/VFX"
            ),
            MediaItemEntity(
                id = "demo_video_4",
                title = "For Bigger Escapes",
                artist = "Google Samples",
                album = "Chromecast Demo",
                duration = 15000, // 0:15
                uri = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                path = "/storage/emulated/0/Video/Promo/For Bigger Escapes.mp4",
                type = "VIDEO",
                coverUri = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=500&q=80",
                genre = "Scenic",
                dateAdded = System.currentTimeMillis() - 9000000,
                folderPath = "/storage/emulated/0/Video/Promo"
            )
        )
        mediaDao.insertMedia(demoItems)
    }
}
