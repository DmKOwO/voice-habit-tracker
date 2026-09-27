package com.voicehabit.tracker.core.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AudioRecorderManager(
    private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var pollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordDurationSeconds = MutableStateFlow(0)
    val recordDurationSeconds: StateFlow<Int> = _recordDurationSeconds.asStateFlow()

    private val _amplitudeNormalized = MutableStateFlow(0f)
    val amplitudeNormalized: StateFlow<Float> = _amplitudeNormalized.asStateFlow()

    fun startRecording(outputFile: File) {
        if (_isRecording.value) return

        currentOutputFile = outputFile

        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            mediaRecorder.apply {
                // VOICE_RECOGNITION включает встроенный в Android аппаратный шумодав
                setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000) // 16 кГц достаточно для речи
                setAudioEncodingBitRate(32000) // 32 кбит/с = ~1.2 МБ за 5 минут!
                setAudioChannels(1) // Моно
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            _isRecording.value = true
            _recordDurationSeconds.value = 0

            startAmplitudeAndTimerPolling()
        } catch (e: Exception) {
            e.printStackTrace()
            releaseRecorder()
        }
    }

    fun stopRecording(): File? {
        if (!_isRecording.value) return null

        stopPolling()
        try {
            recorder?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            releaseRecorder()
        }

        _isRecording.value = false
        val file = currentOutputFile
        currentOutputFile = null
        return if (file != null && file.exists() && file.length() > 0) file else null
    }

    fun cancelRecording() {
        stopRecording()
        currentOutputFile?.delete()
        currentOutputFile = null
        _recordDurationSeconds.value = 0
        _amplitudeNormalized.value = 0f
    }

    private fun startAmplitudeAndTimerPolling() {
        pollingJob = scope.launch {
            var counter = 0
            while (isActive && _isRecording.value) {
                delay(50) // каждые 50 мс для плавной анимации волны
                counter++
                if (counter % 20 == 0) {
                    _recordDurationSeconds.value += 1
                }

                try {
                    val maxAmp = recorder?.maxAmplitude ?: 0
                    // maxAmplitude возвращает 0..32767
                    val norm = (maxAmp / 32767f).coerceIn(0f, 1f)
                    _amplitudeNormalized.value = norm
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun releaseRecorder() {
        try {
            recorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        recorder = null
    }
}
