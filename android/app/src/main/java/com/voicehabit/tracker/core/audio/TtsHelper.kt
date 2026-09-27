package com.voicehabit.tracker.core.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/** G1: озвучка результата разбора и сводки дня. Тихий отказ, если движка нет. */
class TtsHelper(context: Context) {

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var tts: TextToSpeech? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val ru = tts?.setLanguage(Locale("ru"))
                    if (ru == TextToSpeech.LANG_MISSING_DATA || ru == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.language = Locale.US
                    }
                    _ready.value = true
                }
            }
        } catch (e: Exception) {
            _ready.value = false
        }
    }

    fun speak(text: String) {
        val engine = tts ?: return
        if (!_ready.value) return
        try {
            engine.speak(text.take(500), TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        } catch (e: Exception) {
            // TTS не должен ронять flow разбора.
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
        }
    }

    fun shutdown() {
        try {
            tts?.shutdown()
        } catch (e: Exception) {
        }
        tts = null
    }
}
