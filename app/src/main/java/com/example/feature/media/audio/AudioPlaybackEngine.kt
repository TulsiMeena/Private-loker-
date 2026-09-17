package com.example.feature.media.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import com.example.core.database.VaultItemEntity
import com.example.core.database.VaultRepository
import com.example.core.security.LockState
import com.example.core.security.SessionSecurityManager
import com.example.core.storage.FileShredder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * High-security Audio Playback & Queue Engine.
 *
 * Security Requirements:
 * - Decrypts media only to private ephemeral transient files with tracked lifecycles.
 * - Automatically shuts down, releases decoders, and multi-pass shreds working files upon vault lock.
 * - Manages playlist queue, speed adjustment (0.5x - 2.0x), and position seeking.
 */
class AudioPlaybackEngine(
    private val context: Context,
    private val repository: VaultRepository,
    private val sessionManager: SessionSecurityManager,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private var mediaPlayer: MediaPlayer? = null
    private var activeTransientFile: File? = null
    private var progressPollingJob: Job? = null

    private val _currentTrack = MutableStateFlow<VaultItemEntity?>(null)
    val currentTrack: StateFlow<VaultItemEntity?> = _currentTrack.asStateFlow()

    private val _queue = MutableStateFlow<List<VaultItemEntity>>(emptyList())
    val queue: StateFlow<List<VaultItemEntity>> = _queue.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isPrepared = MutableStateFlow(false)
    val isPrepared: StateFlow<Boolean> = _isPrepared.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isMiniPlayerVisible = MutableStateFlow(false)
    val isMiniPlayerVisible: StateFlow<Boolean> = _isMiniPlayerVisible.asStateFlow()

    init {
        // Vault Lock Interruption Observer
        coroutineScope.launch {
            sessionManager.lockState.collect { lockState ->
                if (lockState !is LockState.Unlocked) {
                    stopAndPurge()
                }
            }
        }
    }

    fun playTrack(item: VaultItemEntity, playlist: List<VaultItemEntity> = listOf(item)) {
        coroutineScope.launch {
            // Update queue ensuring current item is at start or current position
            val updatedQueue = if (playlist.any { it.id == item.id }) {
                playlist
            } else {
                listOf(item) + playlist
            }
            _queue.value = updatedQueue
            loadAndStartTrack(item)
        }
    }

    private suspend fun loadAndStartTrack(item: VaultItemEntity) {
        // Stop and shred previous active file
        cleanupCurrentPlayer()

        _currentTrack.value = item
        _isPrepared.value = false
        _currentPositionMs.value = 0
        _durationMs.value = 0
        _isMiniPlayerVisible.value = true

        val previewResult = withContext(Dispatchers.IO) {
            repository.createTransientPreview(item)
        }

        val transientFile = previewResult.getOrNull()
        if (transientFile == null) {
            _currentTrack.value = null
            _isMiniPlayerVisible.value = false
            return
        }

        activeTransientFile = transientFile

        try {
            val player = MediaPlayer()
            player.setDataSource(transientFile.absolutePath)
            player.setOnPreparedListener { mp ->
                _durationMs.value = mp.duration
                _isPrepared.value = true
                applySpeedToPlayer(player, _playbackSpeed.value)
                player.start()
                _isPlaying.value = true
                startProgressPolling()
            }

            player.setOnCompletionListener {
                _isPlaying.value = false
                _currentPositionMs.value = _durationMs.value
                skipToNext()
            }

            player.setOnErrorListener { _, _, _ ->
                stopAndPurge()
                true
            }

            player.prepareAsync()
            mediaPlayer = player

            repository.recordFileAccess(item)
            repository.logSecurityEvent(
                action = "MEDIA_PLAYBACK_START",
                details = "Started audio playback for #${item.id} (${item.title})",
                isSuccess = true
            )
        } catch (_: Exception) {
            cleanupCurrentPlayer()
            _currentTrack.value = null
            _isMiniPlayerVisible.value = false
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (!_isPrepared.value) return

        if (player.isPlaying) {
            player.pause()
            _isPlaying.value = false
        } else {
            player.start()
            _isPlaying.value = true
            startProgressPolling()
        }
    }

    fun seekTo(positionMs: Int) {
        val player = mediaPlayer ?: return
        if (!_isPrepared.value) return
        val clamped = positionMs.coerceIn(0, _durationMs.value)
        player.seekTo(clamped)
        _currentPositionMs.value = clamped
    }

    fun seekRelative(offsetMs: Int) {
        val player = mediaPlayer ?: return
        if (!_isPrepared.value) return
        val newPos = (_currentPositionMs.value + offsetMs).coerceIn(0, _durationMs.value)
        player.seekTo(newPos)
        _currentPositionMs.value = newPos
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        mediaPlayer?.let { applySpeedToPlayer(it, speed) }
    }

    private fun applySpeedToPlayer(player: MediaPlayer, speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val params = player.playbackParams ?: PlaybackParams()
                params.speed = speed
                player.playbackParams = params
            } catch (_: Exception) {}
        }
    }

    fun skipToNext() {
        val current = _currentTrack.value ?: return
        val q = _queue.value
        val currentIndex = q.indexOfFirst { it.id == current.id }
        if (currentIndex != -1 && currentIndex + 1 < q.size) {
            coroutineScope.launch {
                loadAndStartTrack(q[currentIndex + 1])
            }
        } else {
            // End of queue
            _isPlaying.value = false
        }
    }

    fun skipToPrevious() {
        val current = _currentTrack.value ?: return
        val q = _queue.value
        val currentIndex = q.indexOfFirst { it.id == current.id }

        if (_currentPositionMs.value > 3000) {
            // Seek to start
            seekTo(0)
        } else if (currentIndex > 0) {
            coroutineScope.launch {
                loadAndStartTrack(q[currentIndex - 1])
            }
        } else {
            seekTo(0)
        }
    }

    fun addToQueue(item: VaultItemEntity) {
        if (_queue.value.none { it.id == item.id }) {
            _queue.value = _queue.value + item
        }
    }

    fun playNext(item: VaultItemEntity) {
        val current = _currentTrack.value
        val q = _queue.value.toMutableList()
        q.removeAll { it.id == item.id }
        val insertIndex = if (current != null) {
            (q.indexOfFirst { it.id == current.id } + 1).coerceAtLeast(0)
        } else 0
        q.add(insertIndex, item)
        _queue.value = q
    }

    fun removeFromQueue(itemId: Long) {
        _queue.value = _queue.value.filter { it.id != itemId }
        if (_currentTrack.value?.id == itemId) {
            skipToNext()
        }
    }

    fun clearQueue() {
        val current = _currentTrack.value
        _queue.value = if (current != null) listOf(current) else emptyList()
    }

    fun dismissMiniPlayer() {
        _isMiniPlayerVisible.value = false
        stopAndPurge()
    }

    /**
     * Complete teardown and file shredding.
     */
    fun stopAndPurge() {
        cleanupCurrentPlayer()
        _currentTrack.value = null
        _queue.value = emptyList()
        _isPlaying.value = false
        _isPrepared.value = false
        _currentPositionMs.value = 0
        _durationMs.value = 0
        _isMiniPlayerVisible.value = false
    }

    private fun cleanupCurrentPlayer() {
        progressPollingJob?.cancel()
        progressPollingJob = null

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        activeTransientFile?.let { file ->
            FileShredder.shredAndPurge(file)
            activeTransientFile = null
        }
    }

    private fun startProgressPolling() {
        progressPollingJob?.cancel()
        progressPollingJob = coroutineScope.launch {
            while (isActive && _isPlaying.value) {
                mediaPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            _currentPositionMs.value = player.currentPosition
                        }
                    } catch (_: Exception) {}
                }
                delay(250)
            }
        }
    }
}
