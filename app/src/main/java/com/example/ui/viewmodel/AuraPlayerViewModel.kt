package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AuraApplication
import com.example.data.model.MediaItemEntity
import com.example.data.model.PlaylistEntity
import com.example.data.repository.MediaRepository
import com.example.player.AuraPlayerManager
import com.example.player.LyricLine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@kotlinx.coroutines.ExperimentalCoroutinesApi
class AuraPlayerViewModel(
    private val repository: MediaRepository,
    private val playerManager: AuraPlayerManager,
    private val appContext: Context
) : ViewModel() {

    val exoPlayer = playerManager.exoPlayer

    // --- Search & Search state ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // --- Media lists ---
    val allMedia: StateFlow<List<MediaItemEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) repository.allMedia else repository.searchMedia(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val audioMedia: StateFlow<List<MediaItemEntity>> = allMedia
        .map { list -> list.filter { it.type == "AUDIO" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videoMedia: StateFlow<List<MediaItemEntity>> = allMedia
        .map { list -> list.filter { it.type == "VIDEO" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<MediaItemEntity>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayed: StateFlow<List<MediaItemEntity>> = repository.recentlyPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayed: StateFlow<List<MediaItemEntity>> = repository.mostPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pinnedFolders: StateFlow<List<String>> = repository.pinnedFolders
        .map { list -> list.map { it.path } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Playback States (exposed directly from PlayerManager) ---
    val currentMediaItem = playerManager.currentMediaItem
    val playQueue = playerManager.playQueue
    val isPlaying = playerManager.isPlaying
    val currentPosition = playerManager.currentPosition
    val duration = playerManager.duration
    val shuffleModeEnabled = playerManager.shuffleModeEnabled
    val repeatMode = playerManager.repeatMode
    val playbackSpeed = playerManager.playbackSpeed
    val pitch = playerManager.pitch
    val sleepTimerRemaining = playerManager.sleepTimerRemaining

    // --- Audio Effects States ---
    val eqEnabled = playerManager.eqEnabled
    val eqBands = playerManager.eqBands
    val eq10Bands = playerManager.eq10Bands
    val bassBoostStrength = playerManager.bassBoostStrength
    val loudnessGain = playerManager.loudnessGain
    val activeLyricsLine = playerManager.activeLyricsLine
    val lyricsList = playerManager.lyricsList

    // --- Gapless Playback States ---
    val skipSilenceEnabled = playerManager.skipSilenceEnabled
    val prebufferStatus = playerManager.prebufferStatus
    val nextTrackTitle = playerManager.nextTrackTitle

    init {
        // Automatically scan device storage when ViewModel is initialized
        scanMedia()
    }

    fun scanMedia() {
        viewModelScope.launch {
            repository.scanDeviceStorage(appContext)
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    // --- Playback Commands ---
    fun playMediaItem(mediaItem: MediaItemEntity) {
        viewModelScope.launch {
            repository.updateRecentlyPlayed(mediaItem.id)
            playerManager.playMediaItem(mediaItem)
        }
    }

    fun playQueue(queue: List<MediaItemEntity>, startIndex: Int = 0) {
        if (queue.isEmpty()) return
        viewModelScope.launch {
            repository.updateRecentlyPlayed(queue[startIndex].id)
            playerManager.playQueue(queue, startIndex)
        }
    }

    fun play() = playerManager.play()
    fun pause() = playerManager.pause()
    fun seekTo(positionMs: Long) = playerManager.seekTo(positionMs)
    fun skipToNext() = playerManager.skipToNext()
    fun skipToPrevious() = playerManager.skipToPrevious()
    fun toggleShuffle() = playerManager.toggleShuffle()
    fun toggleRepeatMode() = playerManager.toggleRepeatMode()
    fun setPlaybackSpeed(speed: Float) = playerManager.setPlaybackSpeed(speed)
    fun setPitch(pitch: Float) = playerManager.setPitch(pitch)
    fun toggleSkipSilence() = playerManager.toggleSkipSilence()
    fun playTrackInQueue(index: Int) = playerManager.playTrackInQueue(index)
    fun removeFromQueue(index: Int) = playerManager.removeFromQueue(index)

    fun startSleepTimer(minutes: Int) = playerManager.startSleepTimer(minutes)
    fun cancelSleepTimer() = playerManager.cancelSleepTimer()

    // --- Audio Effects ---
    fun toggleEq(enabled: Boolean) = playerManager.toggleEqualizer(enabled)
    fun updateEqBand(bandIndex: Int, levelDb: Int) = playerManager.updateEqBand(bandIndex, levelDb)
    fun update10BandEq(bandIndex: Int, levelDb: Int) = playerManager.update10BandEq(bandIndex, levelDb)
    fun updateBass(strength: Int) = playerManager.updateBassBoost(strength)
    fun updateLoudness(gainMb: Int) = playerManager.updateLoudness(gainMb)

    // --- Database Operations ---
    fun toggleFavorite(mediaId: String) {
        viewModelScope.launch {
            val item = allMedia.value.find { it.id == mediaId }
            if (item != null) {
                repository.setFavorite(mediaId, !item.isFavorite)
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun generateAutomatedPlaylists(criteria: String, onFinished: () -> Unit) {
        viewModelScope.launch {
            val tracks = audioMedia.value
            if (tracks.isEmpty()) {
                onFinished()
                return@launch
            }

            when (criteria) {
                "GENRE" -> {
                    val grouped = tracks.groupBy { it.genre.ifBlank { "General" } }
                    grouped.forEach { (genre, genreTracks) ->
                        val playlistName = "$genre Vibes"
                        repository.createPlaylist(playlistName)
                        val existingPlaylists = repository.playlists.first()
                        val id = existingPlaylists.find { it.name.equals(playlistName, ignoreCase = true) }?.id
                        if (id != null && id > 0) {
                            genreTracks.forEach { track ->
                                repository.addMediaToPlaylist(id, track.id)
                            }
                        }
                    }
                }
                "YEAR" -> {
                    val calendar = java.util.Calendar.getInstance()
                    val grouped = tracks.groupBy { track ->
                        calendar.timeInMillis = track.dateAdded
                        calendar.get(java.util.Calendar.YEAR)
                    }
                    grouped.forEach { (year, yearTracks) ->
                        val playlistName = "Hits from $year"
                        repository.createPlaylist(playlistName)
                        val existingPlaylists = repository.playlists.first()
                        val id = existingPlaylists.find { it.name.equals(playlistName, ignoreCase = true) }?.id
                        if (id != null && id > 0) {
                            yearTracks.forEach { track ->
                                repository.addMediaToPlaylist(id, track.id)
                            }
                        }
                    }
                }
                "FREQUENCY" -> {
                    val mostPlayedTracks = tracks.filter { it.playCount > 0 }.sortedByDescending { it.playCount }.take(15)
                    if (mostPlayedTracks.isNotEmpty()) {
                        val playlistName = "Heavy Rotation"
                        repository.createPlaylist(playlistName)
                        val existingPlaylists = repository.playlists.first()
                        val id = existingPlaylists.find { it.name.equals(playlistName, ignoreCase = true) }?.id
                        if (id != null && id > 0) {
                            mostPlayedTracks.forEach { track ->
                                repository.addMediaToPlaylist(id, track.id)
                            }
                        }
                    }

                    val rareGemsTracks = tracks.filter { it.playCount == 0 }.shuffled().take(15)
                    if (rareGemsTracks.isNotEmpty()) {
                        val playlistName = "Rare Treasures & Discoveries"
                        repository.createPlaylist(playlistName)
                        val existingPlaylists = repository.playlists.first()
                        val id = existingPlaylists.find { it.name.equals(playlistName, ignoreCase = true) }?.id
                        if (id != null && id > 0) {
                            rareGemsTracks.forEach { track ->
                                repository.addMediaToPlaylist(id, track.id)
                            }
                        }
                    }
                }
            }
            onFinished()
        }
    }

    fun deletePlaylist(playlistId: Int) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun addMediaToPlaylist(playlistId: Int, mediaId: String) {
        viewModelScope.launch {
            repository.addMediaToPlaylist(playlistId, mediaId)
        }
    }

    fun removeMediaFromPlaylist(playlistId: Int, mediaId: String) {
        viewModelScope.launch {
            repository.removeMediaFromPlaylist(playlistId, mediaId)
        }
    }

    fun togglePinFolder(path: String) {
        viewModelScope.launch {
            if (pinnedFolders.value.contains(path)) {
                repository.unpinFolder(path)
            } else {
                repository.pinFolder(path)
            }
        }
    }

    fun saveLastPosition(mediaId: String, positionMs: Long) {
        viewModelScope.launch {
            repository.updateLastPosition(mediaId, positionMs)
        }
    }

    fun getMediaForPlaylist(playlistId: Int): Flow<List<MediaItemEntity>> {
        return repository.getMediaForPlaylist(playlistId)
    }

    // --- Custom Provider Factory (Manual DI) ---
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = context.applicationContext as AuraApplication
            @Suppress("UNCHECKED_CAST")
            return AuraPlayerViewModel(app.repository, app.playerManager, app) as T
        }
    }
}
