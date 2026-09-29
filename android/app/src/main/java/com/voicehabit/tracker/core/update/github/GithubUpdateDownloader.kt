package com.voicehabit.tracker.core.update.github

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.voicehabit.tracker.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Фоновая загрузка APK с GitHub Releases и вызов системного установщика.
 *
 * - Стриминг чанками 8 КБ: APK на ~20 МБ не держится в памяти целиком.
 * - Прогресс отдаётся колбэком (байты/всего) — UI рисует прогресс-бар.
 * - Файл кладётся в `filesDir/updates` (не cache: система может вычистить
 *   кэш между загрузкой и установкой) и перезаписывается при повторе.
 * - Установка — только через системный инсталлер (`ACTION_VIEW` +
 *   `FileProvider`); тихую установку без ведома пользователя не делаем.
 */
class GithubUpdateDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .build()
) {
    companion object {
        const val APK_FILE_NAME = "voice-habit-tracker-update.apk"
        const val DIRECTORY = "updates"
        private const val CHUNK_SIZE = 8 * 1024
        // Защита от «бесконечного» редиректа/потока: APK больше 500 МБ не ждём.
        const val MAX_APK_BYTES = 500L * 1024 * 1024

        /**
         * Проверяет, устарел ли скачанный APK по сравнению с установленным приложением.
         * APK считается устаревшим, если его версия/код <= текущих или если данные APK повреждены.
         */
        fun isApkObsolete(
            currentVer: SemVer?,
            currentCode: Long?,
            apkVer: SemVer?,
            apkCode: Long?
        ): Boolean {
            if (apkVer == null && apkCode == null) return true
            val verObsolete = if (currentVer != null && apkVer != null) apkVer <= currentVer else false
            val codeObsolete = if (currentCode != null && apkCode != null) apkCode <= currentCode else false
            return verObsolete || codeObsolete
        }
    }

    sealed interface DownloadResult {
        data class Done(val file: File, val bytes: Long) : DownloadResult
        data class Failed(val reason: String) : DownloadResult
    }

    suspend fun download(
        context: Context,
        url: String,
        expectedSizeBytes: Long? = null,
        onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> }
    ): DownloadResult {
        val dir = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        return downloadToFile(url, File(dir, APK_FILE_NAME), expectedSizeBytes, onProgress)
    }

    /**
     * Ядро загрузки без зависимости от Android — тестируется на JVM через
     * обычный HTTP-сервер из JDK.
     */
    suspend fun downloadToFile(
        url: String,
        target: File,
        expectedSizeBytes: Long? = null,
        onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> }
    ): DownloadResult = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "voice-habit-tracker")
            .header("Accept", "application/vnd.android.package-archive, application/octet-stream")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext DownloadResult.Failed("Загрузка: HTTP ${response.code}")
                }
                val body = response.body
                    ?: return@withContext DownloadResult.Failed("Пустое тело ответа")
                val total = body.contentLength().takeIf { it > 0 } ?: expectedSizeBytes
                if (total != null && total > MAX_APK_BYTES) {
                    return@withContext DownloadResult.Failed("Файл подозрительно большой (${total / 1024 / 1024} МБ)")
                }
                var downloaded = 0L
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(CHUNK_SIZE)
                    while (true) {
                        val read = body.byteStream().read(buffer)
                        if (read == -1) break
                        downloaded += read
                        if (downloaded > MAX_APK_BYTES) {
                            output.close()
                            target.delete()
                            return@withContext DownloadResult.Failed("Файл превышает лимит 500 МБ")
                        }
                        output.write(buffer, 0, read)
                        onProgress(downloaded, total)
                    }
                    output.flush()
                }
                if (downloaded == 0L) {
                    target.delete()
                    return@withContext DownloadResult.Failed("Загружен пустой файл")
                }
                AppLogger.instance().i(
                    "update",
                    "APK загружен",
                    mapOf("bytes" to downloaded.toString(), "file" to target.name)
                )
                DownloadResult.Done(target, downloaded)
            }
        } catch (e: Exception) {
            target.delete()
            DownloadResult.Failed(e.message ?: "Ошибка сети при загрузке")
        }
    }

    /**
     * Возвращает Intent системного установщика. Бросает исключение только при
     * ошибке конфигурации FileProvider — вызывающий показывает Failed.
     */
    fun installIntent(context: Context, apk: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }

    fun downloadedApk(context: Context): File? {
        val file = File(File(context.filesDir, DIRECTORY), APK_FILE_NAME)
        return file.takeIf { it.exists() && it.length() > 0 }
    }

    fun deleteDownloadedApk(context: Context): Boolean {
        val file = File(File(context.filesDir, DIRECTORY), APK_FILE_NAME)
        return deleteApkFile(file)
    }

    fun deleteApkFile(file: File): Boolean {
        return runCatching {
            if (file.exists()) file.delete() else false
        }.getOrDefault(false)
    }

    /**
     * Извлекает информацию о версии и коде сборки из скачанного APK.
     * Возвращает null, если файл повреждён, не существует или не является валидным APK.
     */
    fun getApkInfo(context: Context, file: File): Pair<SemVer?, Long?>? = runCatching {
        if (!file.exists() || file.length() == 0L) return null
        val archiveInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
        val versionName = archiveInfo.versionName
        val semVer = SemVer.parse(versionName)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            archiveInfo.versionCode.toLong()
        }
        Pair(semVer, versionCode)
    }.getOrNull()
}
