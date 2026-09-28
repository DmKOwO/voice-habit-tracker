package com.voicehabit.tracker.core.audio

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class AudioPlaybackState(
    val isPlaying: Boolean = false,
    val currentAudioPath: String? = null,
    val currentPositionMs: Int = 0,
    val totalDurationMs: Int = 0
)

class AudioPlayerManager {

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _playbackState = MutableStateFlow(AudioPlaybackState())
    val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

    fun play(filePath: String) {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            return
        }

        if (_playbackState.value.currentAudioPath == filePath && mediaPlayer != null) {
            mediaPlayer?.let { player ->
                if (!player.isPlaying) {
                    player.start()
                    _playbackState.update { it.copy(isPlaying = true) }
                    startProgressTracker()
                }
            }
            return
        }

        stop()

        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp ->
                    mp.start()
                    _playbackState.update {
                        AudioPlaybackState(
                            isPlaying = true,
                            currentAudioPath = filePath,
                            currentPositionMs = 0,
                            totalDurationMs = mp.duration
                        )
                    }
                    startProgressTracker()
                }
                setOnCompletionListener {
                    stop()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            stop()
        }
    }

    fun pause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _playbackState.update { it.copy(isPlaying = false) }
            }
        }
        progressJob?.cancel()
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.let { player ->
            val target = positionMs.coerceIn(0, player.duration)
            player.seekTo(target)
            _playbackState.update { it.copy(currentPositionMs = target) }
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        _playbackState.update {
            AudioPlaybackState(
                isPlaying = false,
                currentAudioPath = null,
                currentPositionMs = 0,
                totalDurationMs = 0
            )
        }
    }

    fun toggle(filePath: String) {
        if (_playbackState.value.currentAudioPath == filePath && _playbackState.value.isPlaying) {
            pause()
        } else {
            play(filePath)
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && mediaPlayer?.isPlaying == true) {
                val current = mediaPlayer?.currentPosition ?: 0
                val total = mediaPlayer?.duration ?: 0
                _playbackState.update {
                    it.copy(
                        currentPositionMs = current,
                        totalDurationMs = total
                    )
                }
                delay(100)
            }
        }
    }
}
