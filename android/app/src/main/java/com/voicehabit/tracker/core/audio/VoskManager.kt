package com.voicehabit.tracker.core.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * F1: офлайн-распознавание через Vosk (модель small-ru, ~40 МБ).
 *
 * Модель качается один раз по требованию в filesDir и распаковывается там же.
 * Транскрибируется записанный WAV (16 кГц моно — формат нашего движка),
 * поэтому микрофон в реальном времени не нужен: цепочка остаётся
 * «запись → файл → текст», только без сети.
 */
class VoskManager(private val appContext: Context) {

    enum class Status { NOT_DOWNLOADED, DOWNLOADING, READY, ERROR }

    private val _status = MutableStateFlow(Status.NOT_DOWNLOADED)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private var lastError: String? = null
    fun lastError(): String? = lastError

    init {
        // Модель могла быть скачана в прошлой сессии: статус должен отражать
        // реальность сразу, а не только после нового скачивания.
        if (modelDir().exists()) {
            _status.value = Status.READY
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun modelDir(): File = File(appContext.filesDir, "vosk-model-small-ru")

    fun isReady(): Boolean = _status.value == Status.READY || modelDir().exists()

    /** Скачивание + распаковка модели. Долгий вызов — только с IO-диспетчера. */
    suspend fun downloadModel(): Boolean = withContext(Dispatchers.IO) {
        if (isReady()) {
            _status.value = Status.READY
            return@withContext true
        }
        _status.value = Status.DOWNLOADING
        _downloadProgress.value = 0f
        try {
            val zipFile = File(appContext.filesDir, "vosk-model-small-ru.zip")
            val request = Request.Builder().url(MODEL_URL).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                val body = response.body ?: throw IllegalStateException("Пустое тело")
                val total = body.contentLength().takeIf { it > 0 }
                var done = 0L
                FileOutputStream(zipFile).use { out ->
                    val buf = ByteArray(32 * 1024)
                    body.byteStream().use { input ->
                        while (true) {
                            val read = input.read(buf)
                            if (read == -1) break
                            out.write(buf, 0, read)
                            done += read
                            if (total != null) _downloadProgress.value = done.toFloat() / total
                        }
                    }
                }
            }
            unzip(zipFile, appContext.filesDir)
            zipFile.delete()
            _downloadProgress.value = 1f
            _status.value = if (modelDir().exists()) Status.READY else Status.ERROR
            if (_status.value != Status.READY) lastError = "Модель не распаковалась"
            _status.value == Status.READY
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            _status.value = Status.ERROR
            false
        }
    }

    /** Транскрибация WAV 16 кГц моно. Возвращает текст или null. */
    suspend fun transcribe(wavFile: File): String? = withContext(Dispatchers.Default) {
        if (!isReady()) return@withContext null
        try {
            val model = Model(modelDir().absolutePath)
            try {
                val recognizer = Recognizer(model, 16000f)
                try {
                    val bytes = wavFile.readBytes()
                    // Пропускаем WAV-заголовок 44 байта, дальше PCM16.
                    var offset = if (bytes.size > 44) 44 else 0
                    val chunk = ByteArray(4096)
                    while (offset < bytes.size) {
                        val len = minOf(chunk.size, bytes.size - offset)
                        System.arraycopy(bytes, offset, chunk, 0, len)
                        recognizer.acceptWaveForm(chunk, len)
                        offset += len
                    }
                    val json = recognizer.finalResult
                    // {"text": "..."}
                    Regex("\"text\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)
                        ?.takeIf { it.isNotBlank() }
                } finally {
                    recognizer.close()
                }
            } finally {
                model.close()
            }
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            null
        }
    }

    private fun unzip(zipFile: File, targetDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val out = File(targetDir, entry.name)
                if (!out.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    throw IllegalStateException("Zip Slip: ${entry.name}")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        // Архив содержит папку vosk-model-small-ru-0.22 — переименовываем.
        val unpacked = targetDir.listFiles()?.firstOrNull { it.isDirectory && it.name.startsWith("vosk-model-small-ru") }
        if (unpacked != null && !modelDir().exists()) {
            unpacked.renameTo(modelDir())
        }
    }

    companion object {
        const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip"
    }
}
