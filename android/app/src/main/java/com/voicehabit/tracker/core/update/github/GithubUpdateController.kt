package com.voicehabit.tracker.core.update.github

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.voicehabit.tracker.core.logging.AppLogger
import com.voicehabit.tracker.core.notifications.Notify
import com.voicehabit.tracker.core.update.AppUpdateUiState
import com.voicehabit.tracker.core.update.UPDATE_GITHUB_OWNER
import com.voicehabit.tracker.core.update.UPDATE_GITHUB_REPO
import com.voicehabit.tracker.data.local.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Оркестрация обновлений через GitHub Releases (DmKOwO/voice-habit-tracker).
 * Работает полностью автоматически по воздуху (OTA):
 * 1. Проверяет наличие новых релизов при старте приложения и в фоне.
 * 2. Автоматически скачивает свежий APK без лишних действий со стороны пользователя.
 * 3. Готовое обновление устанавливается одним тапом через системный инсталлер.
 */
class GithubUpdateController(
    private val appContext: Context,
    private val settings: SettingsManager,
    private val checker: GithubReleaseChecker = GithubReleaseChecker(
        currentVersion = { currentSemVer(appContext) }
    ),
    private val downloader: GithubUpdateDownloader = GithubUpdateDownloader()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    val state: StateFlow<AppUpdateUiState> = mutableState.asStateFlow()

    private var pendingRelease: GithubRelease? = null
    val latestRelease: GithubRelease? get() = pendingRelease

    init {
        cleanObsoleteApk()
    }

    fun check(auto: Boolean = false) {
        if (auto && !shouldAutoCheck()) {
            return
        }
        if (mutableState.value is AppUpdateUiState.Checking) return

        mutableState.value = AppUpdateUiState.Checking
        AppLogger.instance().startOperation(OPERATION, "Проверка обновления (GitHub)")
        scope.launch(Dispatchers.IO) {
            val result = checker.check(UPDATE_GITHUB_OWNER, UPDATE_GITHUB_REPO)
            withContext(Dispatchers.Main.immediate) {
                when (result) {
                    is CheckResult.UpdateAvailable -> {
                        val release = result.release
                        pendingRelease = release
                        settings.lastGithubCheckMillis = System.currentTimeMillis()
                        AppLogger.instance().finishOperation(OPERATION, "найдено ${release.tagName}")

                        val downloadedApk = downloader.downloadedApk(appContext)
                        if (downloadedApk != null && settings.lastGithubAutoTag == release.tagName) {
                            mutableState.value = AppUpdateUiState.Downloaded
                        } else {
                            mutableState.value = AppUpdateUiState.Available(
                                version = release.version.toString(),
                                tag = release.tagName,
                                notes = release.notes,
                                sizeBytes = release.apkSizeBytes,
                                prerelease = release.prerelease
                            )
                            if (settings.autoUpdateCheck) {
                                startDownload()
                            }
                        }
                    }
                    CheckResult.UpToDate -> {
                        settings.lastGithubCheckMillis = System.currentTimeMillis()
                        AppLogger.instance().finishOperation(OPERATION, "уже последняя версия")
                        cleanObsoleteApk()
                        downloader.deleteDownloadedApk(appContext)
                        settings.lastGithubAutoTag = ""
                        Notify.cancel(appContext, Notify.ID_UPDATE)
                        mutableState.value = AppUpdateUiState.UpToDate
                    }
                    CheckResult.NoReleases -> {
                        AppLogger.instance().finishOperation(OPERATION, "релизов нет")
                        mutableState.value = AppUpdateUiState.Unavailable("В репозитории пока нет релизов")
                    }
                    CheckResult.Offline -> {
                        AppLogger.instance().failOperation(OPERATION, "нет сети")
                        mutableState.value = AppUpdateUiState.Unavailable(
                            "Нет соединения с интернетом для проверки обновлений."
                        )
                    }
                    is CheckResult.RateLimited -> {
                        AppLogger.instance().failOperation(OPERATION, "rate-limit GitHub API")
                        val hint = result.retryAfterMillis?.let { " Повторите через ${it / 1000} c." } ?: ""
                        mutableState.value = AppUpdateUiState.Unavailable(
                            "GitHub временно ограничил запросы (лимит 60/час без авторизации).$hint"
                        )
                    }
                    is CheckResult.Invalid -> {
                        AppLogger.instance().failOperation(OPERATION, result.reason)
                        mutableState.value = AppUpdateUiState.Failed(result.reason)
                    }
                }
            }
        }
    }

    fun startDownload() {
        val release = pendingRelease
        if (release == null || release.apkUrl.isBlank()) {
            mutableState.value = AppUpdateUiState.Failed("Нет ссылки на APK в релизе")
            return
        }
        mutableState.value = AppUpdateUiState.InProgress(0L, release.apkSizeBytes ?: 0L)
        scope.launch {
            when (val result = downloader.download(
                context = appContext,
                url = release.apkUrl,
                expectedSizeBytes = release.apkSizeBytes,
                onProgress = { downloaded, total ->
                    mutableState.value = AppUpdateUiState.InProgress(downloaded, total ?: 0L)
                }
            )) {
                is GithubUpdateDownloader.DownloadResult.Done -> {
                    settings.lastGithubAutoTag = release.tagName
                    mutableState.value = AppUpdateUiState.Downloaded
                }
                is GithubUpdateDownloader.DownloadResult.Failed ->
                    mutableState.value = AppUpdateUiState.Failed(result.reason)
            }
        }
    }

    /**
     * Удаляет скачанный APK, если он равен текущей версии или старше её (или если повреждён).
     * Отменяет уведомление 9201 и сбрасывает устаревший статус в UI.
     * Возвращает true, если устаревший APK был обнаружен и удалён.
     */
    fun cleanObsoleteApk(): Boolean {
        val apk = downloader.downloadedApk(appContext) ?: run {
            Notify.cancel(appContext, Notify.ID_UPDATE)
            return false
        }
        val curVer = currentSemVer(appContext)
        val curCode = currentVersionCode(appContext)
        val apkInfo = downloader.getApkInfo(appContext, apk)

        val isObsolete = if (apkInfo == null) {
            true
        } else {
            GithubUpdateDownloader.isApkObsolete(
                currentVer = curVer,
                currentCode = curCode,
                apkVer = apkInfo.first,
                apkCode = apkInfo.second
            )
        }

        if (isObsolete) {
            AppLogger.instance().i("update", "Удаление устаревшего APK обновления", mapOf("file" to apk.name))
            downloader.deleteDownloadedApk(appContext)
            settings.lastGithubAutoTag = ""
            Notify.cancel(appContext, Notify.ID_UPDATE)
            if (mutableState.value is AppUpdateUiState.Downloaded) {
                mutableState.value = AppUpdateUiState.UpToDate
            }
            return true
        }
        return false
    }

    fun installDownloaded(onError: (String) -> Unit = {}) {
        if (cleanObsoleteApk()) {
            AppLogger.instance().i("update", "Попытка установки отменена: APK уже установлен или устарел")
            onError("Установлена актуальная версия приложения")
            return
        }
        val apk = downloader.downloadedApk(appContext)
        if (apk == null) {
            mutableState.value = AppUpdateUiState.Failed("Загруженный файл не найден, скачиваю заново…")
            startDownload()
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!appContext.packageManager.canRequestPackageInstalls()) {
                    val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${appContext.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(manageIntent)
                    onError("Разрешите установку из этого источника в открывшихся настройках")
                    return
                }
            }
            appContext.startActivity(downloader.installIntent(appContext, apk))
        } catch (e: Exception) {
            val reason = e.message ?: "Не удалось открыть установщик"
            mutableState.value = AppUpdateUiState.Failed(reason)
            onError(reason)
        }
    }

    fun dismiss() {
        mutableState.value = AppUpdateUiState.Idle
    }

    private fun shouldAutoCheck(): Boolean {
        val last = settings.lastGithubCheckMillis
        return System.currentTimeMillis() - last > AUTO_CHECK_INTERVAL_MILLIS
    }

    fun destroy() = scope.cancel()

    companion object {
        const val OPERATION = "github_update"
        // 15 минут между автопроверками при открытии приложения
        const val AUTO_CHECK_INTERVAL_MILLIS = 15L * 60 * 1000

        fun currentSemVer(context: Context): SemVer? = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            SemVer.parse(info.versionName)
        }.getOrNull()

        fun currentVersionCode(context: Context): Long? = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        }.getOrNull()
    }
}

