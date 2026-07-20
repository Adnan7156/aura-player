package com.example.player

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.MediaItemEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import kotlin.math.absoluteValue

@OptIn(UnstableApi::class)
class AuraPlayerManager(private val context: Context) {

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .setLoadControl(
            androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15000, // minBufferMs
                    50000, // maxBufferMs
                    1000,  // bufferForPlaybackMs
                    1500   // bufferForPlaybackAfterRebufferMs
                )
                .setBackBuffer(10000, true) // Retain 10 seconds of back buffer
                .build()
        )
        .build()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Equalizer, BassBoost, LoudnessEnhancer
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    // Player State Flows
    private val _currentMediaItem = MutableStateFlow<MediaItemEntity?>(null)
    val currentMediaItem: StateFlow<MediaItemEntity?> = _currentMediaItem.asStateFlow()

    private val _playQueue = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    val playQueue: StateFlow<List<MediaItemEntity>> = _playQueue.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    // Speed and Pitch
    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _pitch = MutableStateFlow(1.0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    // Sleep Timer
    private val _sleepTimerRemaining = MutableStateFlow(0L) // in milliseconds
    val sleepTimerRemaining: StateFlow<Long> = _sleepTimerRemaining.asStateFlow()
    private var sleepTimerJob: Job? = null

    // Audio Effects States
    private val _eqEnabled = MutableStateFlow(false)
    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()

    private val _eqBands = MutableStateFlow<List<Pair<Int, Int>>>(emptyList()) // List of <Frequency, Level_in_dB_scaled>
    val eqBands: StateFlow<List<Pair<Int, Int>>> = _eqBands.asStateFlow()

    private val _eq10Bands = MutableStateFlow<List<Pair<Int, Int>>>(
        listOf(
            31 to 0,
            62 to 0,
            125 to 0,
            250 to 0,
            500 to 0,
            1000 to 0,
            2000 to 0,
            4000 to 0,
            8000 to 0,
            16000 to 0
        )
    )
    val eq10Bands: StateFlow<List<Pair<Int, Int>>> = _eq10Bands.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(0) // 0 to 1000
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    private val _loudnessGain = MutableStateFlow(0) // 0 to 2000 mB
    val loudnessGain: StateFlow<Int> = _loudnessGain.asStateFlow()

    // Active Parsed Lyrics
    private val _activeLyricsLine = MutableStateFlow<String>("")
    val activeLyricsLine: StateFlow<String> = _activeLyricsLine.asStateFlow()
    private var lyricsTimeline = TreeMap<Long, String>()

    private val _lyricsList = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyricsList: StateFlow<List<LyricLine>> = _lyricsList.asStateFlow()

    // Gapless Playback Engine States
    private val _skipSilenceEnabled = MutableStateFlow(false)
    val skipSilenceEnabled: StateFlow<Boolean> = _skipSilenceEnabled.asStateFlow()

    private val _prebufferStatus = MutableStateFlow("Idle")
    val prebufferStatus: StateFlow<String> = _prebufferStatus.asStateFlow()

    private val _nextTrackTitle = MutableStateFlow<String?>(null)
    val nextTrackTitle: StateFlow<String?> = _nextTrackTitle.asStateFlow()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val activeId = mediaItem?.mediaId
                val found = _playQueue.value.find { it.id == activeId }
                if (found != null) {
                    _currentMediaItem.value = found
                    loadLyricsForMediaItem(found)
                }
                _duration.value = exoPlayer.duration.coerceAtLeast(0)
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _duration.value = exoPlayer.duration.coerceAtLeast(0)
                if (playbackState == Player.STATE_READY) {
                    initAudioEffects()
                }
            }
        })

        // Periodically update position, duration, check active lyrics & gapless engine pre-buffer status
        scope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    val pos = exoPlayer.currentPosition
                    _currentPosition.value = pos
                    updateActiveLyric(pos)
                }
                updateGaplessStatus()
                delay(250)
            }
        }
    }

    private fun initAudioEffects() {
        val audioSessionId = exoPlayer.audioSessionId
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return

        try {
            if (equalizer == null || equalizer?.id != audioSessionId) {
                equalizer = Equalizer(0, audioSessionId).apply {
                    enabled = _eqEnabled.value
                    // Read available bands
                    val numBands = numberOfBands
                    val bands = mutableListOf<Pair<Int, Int>>()
                    for (i in 0 until numBands) {
                        val freq = getCenterFreq(i.toShort()) / 1000 // Convert mHz to Hz
                        val level = getBandLevel(i.toShort()) / 100 // Convert mB to dB
                        bands.add(freq to level)
                    }
                    _eqBands.value = bands
                    apply10BandEqToNative(this)
                }
            }

            if (bassBoost == null) {
                bassBoost = BassBoost(0, audioSessionId).apply {
                    enabled = _eqEnabled.value
                    setStrength(_bassBoostStrength.value.toShort())
                }
            }

            if (loudnessEnhancer == null) {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                    enabled = _eqEnabled.value
                    setTargetGain(_loudnessGain.value)
                }
            }
        } catch (e: Exception) {
            Log.e("AudioEffects", "Failed to initialize audio effects: ${e.message}")
        }
    }

    fun apply10BandEqToNative(eq: Equalizer? = equalizer) {
        val activeEq = eq ?: return
        try {
            val numNativeBands = activeEq.numberOfBands
            for (nativeIdx in 0 until numNativeBands) {
                val nativeFreqHz = activeEq.getCenterFreq(nativeIdx.toShort()) / 1000
                // Find closest band in 10-band configuration
                val closest10BandIdx = _eq10Bands.value.minByOrNull { (uiFreq, _) ->
                    (uiFreq - nativeFreqHz).absoluteValue
                }?.let { closestPair ->
                    _eq10Bands.value.indexOfFirst { it.first == closestPair.first }
                } ?: 0

                val levelDbToApply = _eq10Bands.value[closest10BandIdx].second
                val levelMb = (levelDbToApply * 100).coerceIn(-1500, 1500).toShort()
                activeEq.setBandLevel(nativeIdx.toShort(), levelMb)
            }
        } catch (e: Exception) {
            Log.e("AudioEffects", "Failed applying 10-band EQ to native: ${e.message}")
        }
    }

    fun toggleEqualizer(enabled: Boolean) {
        _eqEnabled.value = enabled
        equalizer?.enabled = enabled
        bassBoost?.enabled = enabled
        loudnessEnhancer?.enabled = enabled
        if (enabled) {
            apply10BandEqToNative()
        }
    }

    fun update10BandEq(bandIndex: Int, levelDb: Int) {
        val current = _eq10Bands.value.toMutableList()
        if (bandIndex in current.indices) {
            current[bandIndex] = current[bandIndex].first to levelDb
            _eq10Bands.value = current
            apply10BandEqToNative()
        }
    }

    fun updateEqBand(bandIndex: Int, levelDb: Int) {
        val levelMb = (levelDb * 100).coerceIn(-1500, 1500).toShort()
        try {
            equalizer?.setBandLevel(bandIndex.toShort(), levelMb)
            val current = _eqBands.value.toMutableList()
            if (bandIndex in current.indices) {
                current[bandIndex] = current[bandIndex].first to levelDb
                _eqBands.value = current
            }
        } catch (e: Exception) {
            Log.e("AudioEffects", "Failed setting EQ band $bandIndex: ${e.message}")
        }
    }

    fun updateBassBoost(strength: Int) {
        _bassBoostStrength.value = strength
        try {
            bassBoost?.setStrength(strength.coerceIn(0, 1000).toShort())
        } catch (e: Exception) {
            Log.e("AudioEffects", "Failed setting Bass Boost: ${e.message}")
        }
    }

    fun updateLoudness(gainMb: Int) {
        _loudnessGain.value = gainMb
        try {
            loudnessEnhancer?.setTargetGain(gainMb.coerceIn(0, 2000))
        } catch (e: Exception) {
            Log.e("AudioEffects", "Failed setting Loudness Enhancer: ${e.message}")
        }
    }

    // --- Lyrics Support (LRC) ---
    private fun parseLyrics(lrcContent: String?) {
        lyricsTimeline.clear()
        _activeLyricsLine.value = ""
        if (lrcContent == null) {
            _lyricsList.value = emptyList()
            return
        }

        val lines = lrcContent.split("\n")
        val pattern = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2})](.*)")
        val tempLines = mutableListOf<LyricLine>()
        for (line in lines) {
            val match = pattern.matchEntire(line.trim())
            if (match != null) {
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toLong()
                val msDigit = match.groupValues[3].toLong()
                val text = match.groupValues[4].trim()

                val totalMs = (min * 60 * 1000) + (sec * 1000) + (msDigit * 10)
                lyricsTimeline[totalMs] = text
                tempLines.add(LyricLine(totalMs, text))
            } else if (line.isNotEmpty() && !line.startsWith("[")) {
                // Support plain text line fallback, seed arbitrarily at start
                val totalMs = lyricsTimeline.size * 5000L
                lyricsTimeline[totalMs] = line.trim()
                tempLines.add(LyricLine(totalMs, line.trim()))
            }
        }
        _lyricsList.value = tempLines.sortedBy { it.timeMs }
    }

    private fun loadLyricsForMediaItem(mediaItem: MediaItemEntity) {
        scope.launch(Dispatchers.IO) {
            val lyricsToParse = mediaItem.lyrics ?: extractEmbeddedLyrics(context, mediaItem.uri) ?: extractEmbeddedLyrics(context, mediaItem.path)
            withContext(Dispatchers.Main) {
                parseLyrics(lyricsToParse)
            }
        }
    }

    private fun extractEmbeddedLyrics(context: Context, uriString: String?): String? {
        if (uriString == null) return null
        val retriever = android.media.MediaMetadataRetriever()
        try {
            if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
                retriever.setDataSource(context, Uri.parse(uriString))
            } else {
                retriever.setDataSource(uriString)
            }
            return retriever.extractMetadata(31) // 31 is MediaMetadataRetriever.METADATA_KEY_LYRICS
        } catch (e: Exception) {
            Log.e("AuraPlayerManager", "Failed to extract embedded lyrics: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }
        return null
    }

    private fun updateActiveLyric(positionMs: Long) {
        if (lyricsTimeline.isEmpty()) return
        val entry = lyricsTimeline.floorEntry(positionMs)
        val lyricText = entry?.value ?: ""
        if (_activeLyricsLine.value != lyricText) {
            _activeLyricsLine.value = lyricText
        }
    }

    // --- Playback Controls ---
    fun playMediaItem(mediaItem: MediaItemEntity) {
        _currentMediaItem.value = mediaItem
        _playQueue.value = listOf(mediaItem)
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaItem(MediaItem.Builder()
            .setMediaId(mediaItem.id)
            .setUri(Uri.parse(mediaItem.uri))
            .build()
        )
        exoPlayer.prepare()
        exoPlayer.seekTo(mediaItem.lastPosition)
        exoPlayer.play()
    }

    fun playQueue(queue: List<MediaItemEntity>, startIndex: Int = 0) {
        if (queue.isEmpty()) return
        _playQueue.value = queue
        _currentMediaItem.value = queue[startIndex]

        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        queue.forEach { item ->
            exoPlayer.addMediaItem(MediaItem.Builder()
                .setMediaId(item.id)
                .setUri(Uri.parse(item.uri))
                .build()
            )
        }

        exoPlayer.prepare()
        exoPlayer.seekTo(startIndex, queue[startIndex].lastPosition)
        exoPlayer.play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun play() {
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun stop() {
        exoPlayer.stop()
    }

    fun skipToNext() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
        } else if (_repeatMode.value == Player.REPEAT_MODE_ALL && _playQueue.value.isNotEmpty()) {
            exoPlayer.seekTo(0, 0L)
        }
    }

    fun skipToPrevious() {
        if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem()
        }
    }

    fun toggleShuffle() {
        val enabled = !_shuffleModeEnabled.value
        _shuffleModeEnabled.value = enabled
        exoPlayer.shuffleModeEnabled = enabled
    }

    fun toggleRepeatMode() {
        val nextMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = nextMode
        exoPlayer.repeatMode = nextMode
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        val params = PlaybackParameters(speed, _pitch.value)
        exoPlayer.playbackParameters = params
    }

    fun setPitch(pitch: Float) {
        _pitch.value = pitch
        val params = PlaybackParameters(_playbackSpeed.value, pitch)
        exoPlayer.playbackParameters = params
    }

    // --- Dynamic Queue Controls ---
    fun playTrackInQueue(index: Int) {
        if (index in _playQueue.value.indices) {
            _currentMediaItem.value = _playQueue.value[index]
            exoPlayer.seekTo(index, 0L)
            exoPlayer.play()
        }
    }

    fun removeFromQueue(index: Int) {
        val currentList = _playQueue.value.toMutableList()
        if (index in currentList.indices) {
            val removedItem = currentList.removeAt(index)
            _playQueue.value = currentList
            exoPlayer.removeMediaItem(index)
            
            // If we removed the currently playing item, and the queue is not empty, play another track
            if (_currentMediaItem.value?.id == removedItem.id) {
                if (currentList.isNotEmpty()) {
                    val newIndex = index.coerceAtMost(currentList.size - 1)
                    _currentMediaItem.value = currentList[newIndex]
                    exoPlayer.seekTo(newIndex, 0L)
                    exoPlayer.play()
                } else {
                    _currentMediaItem.value = null
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                }
            }
        }
    }

    // --- Sleep Timer ---
    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerRemaining.value = 0L
            return
        }

        val durationMs = minutes * 60 * 1000L
        _sleepTimerRemaining.value = durationMs

        sleepTimerJob = scope.launch {
            var remaining = durationMs
            while (remaining > 0) {
                delay(1000)
                remaining -= 1000
                _sleepTimerRemaining.value = remaining
            }
            pause() // Pause playback!
            _sleepTimerRemaining.value = 0L
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerRemaining.value = 0L
    }

    fun release() {
        scope.cancel()
        exoPlayer.release()
        equalizer?.release()
        bassBoost?.release()
        loudnessEnhancer?.release()
    }

    // --- Gapless Engine & Silence Skipping Support ---
    fun toggleSkipSilence() {
        val enabled = !_skipSilenceEnabled.value
        _skipSilenceEnabled.value = enabled
        exoPlayer.skipSilenceEnabled = enabled
    }

    private fun updateGaplessStatus() {
        val duration = exoPlayer.duration
        val buffered = exoPlayer.bufferedPosition
        val hasNext = exoPlayer.hasNextMediaItem()
        
        if (hasNext) {
            val nextIndex = exoPlayer.nextMediaItemIndex
            val nextItem = _playQueue.value.getOrNull(nextIndex)
            _nextTrackTitle.value = nextItem?.title
            
            if (duration > 0) {
                val currentBufferedPercent = (buffered * 100) / duration
                if (currentBufferedPercent >= 98L) {
                    _prebufferStatus.value = "Ready (Zero-latency)"
                } else if (currentBufferedPercent >= 80L || exoPlayer.isLoading) {
                    _prebufferStatus.value = "Pre-buffering ($currentBufferedPercent%)"
                } else {
                    _prebufferStatus.value = "Standby (Buffered: $currentBufferedPercent%)"
                }
            } else {
                _prebufferStatus.value = "Preparing..."
            }
        } else {
            _nextTrackTitle.value = null
            _prebufferStatus.value = "Queue End (No next track)"
        }
    }
}

data class LyricLine(val timeMs: Long, val text: String)
