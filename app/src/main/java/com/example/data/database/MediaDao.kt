package com.example.data.database

import androidx.room.*
import com.example.data.model.MediaItemEntity
import com.example.data.model.PinnedFolderEntity
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    // --- Media Items ---
    @Query("SELECT * FROM media_items ORDER BY title ASC")
    fun getAllMedia(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE type = :type ORDER BY title ASC")
    fun getMediaByType(type: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getMediaById(id: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE id IN (:ids)")
    suspend fun getMediaByIds(ids: List<String>): List<MediaItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(items: List<MediaItemEntity>)

    @Update
    suspend fun updateMedia(item: MediaItemEntity)

    @Query("UPDATE media_items SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE media_items SET recentlyPlayed = :timestamp, playCount = playCount + 1 WHERE id = :id")
    suspend fun updateRecentlyPlayed(id: String, timestamp: Long)

    @Query("UPDATE media_items SET lastPosition = :position WHERE id = :id")
    suspend fun updateLastPosition(id: String, position: Long)

    @Query("SELECT * FROM media_items WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavorites(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE recentlyPlayed > 0 ORDER BY recentlyPlayed DESC LIMIT 100")
    fun getRecentlyPlayed(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE playCount > 0 ORDER BY playCount DESC LIMIT 100")
    fun getMostPlayed(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchMedia(query: String): Flow<List<MediaItemEntity>>

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteMedia(id: String)

    // --- Playlists ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Int)

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Int): PlaylistEntity?

    // --- Playlist Items ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun deletePlaylistItem(playlistId: Int, mediaId: String)

    @Query("""
        SELECT m.* FROM media_items m
        INNER JOIN playlist_items p ON m.id = p.mediaId
        WHERE p.playlistId = :playlistId
        ORDER BY p.displayOrder ASC
    """)
    fun getMediaForPlaylist(playlistId: Int): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY displayOrder ASC")
    suspend fun getPlaylistItems(playlistId: Int): List<PlaylistItemEntity>

    // --- Folder Pinning ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPinnedFolder(folder: PinnedFolderEntity)

    @Query("DELETE FROM pinned_folders WHERE path = :path")
    suspend fun deletePinnedFolder(path: String)

    @Query("SELECT * FROM pinned_folders ORDER BY pinnedAt DESC")
    fun getPinnedFolders(): Flow<List<PinnedFolderEntity>>
}
